"""Coordinator state-machine tests; fakes only, no provider calls, no git/gh mutations."""
import json
from datetime import datetime, timezone
from pathlib import Path
import sys
import tempfile
import types
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import pipeline_coordinator as pc  # noqa: E402

CONFIG = yaml.safe_load((ROOT / 'defaults/agents.yaml').read_text())
SHA = 'a' * 40
ROLES = ('implementer', 'reviewer', 'merge-judge')


class FakeBackend(pc.Backend):
    def __init__(self, wt):
        self.wt, self.n, self.calls, self.status, self.owner = Path(wt), 0, [], {}, {}

    def _launch(self, kind, ticket_id, **extra):
        self.n += 1
        dispatch = f'dsp-{self.n}'
        self.status[dispatch], self.owner[dispatch] = 'running', ticket_id
        self.calls.append(dict(kind=kind, ticket=ticket_id, dispatch=dispatch, **extra))
        return {'dispatchId': dispatch, 'taskId': f'task-{ticket_id}'}

    def start_implementer(self, ticket, text, plan):
        (self.wt / ticket['id']).mkdir(exist_ok=True)
        return self._launch('start_implementer', ticket['id'], plan=plan, text=text)

    def continue_implementer(self, ticket_id, prompt, plan, worktree, previous_dispatch=None):
        return self._launch('continue_implementer', ticket_id, prompt=prompt, plan=plan, worktree=worktree)

    def start_reviewer(self, ticket_id, prompt, role_plan, worktree):
        return self._launch('start_reviewer', ticket_id, prompt=prompt, plan=role_plan)

    def start_judge(self, ticket_id, prompt, role_plan, worktree):
        return self._launch('start_judge', ticket_id, prompt=prompt, plan=role_plan)

    def poll(self, dispatch):
        ticket = self.owner[dispatch]
        return {'state': self.status[dispatch], 'taskId': f'task-{ticket}', 'worktree': str(self.wt / ticket)}

    def of(self, kind, ticket=None):
        return [c for c in self.calls if c['kind'] == kind and ticket in (None, c['ticket'])]


class FakeForge(pc.Forge):
    def __init__(self):
        self.pr = {'number': 7, 'head_sha': SHA, 'draft': False}
        self.ci, self.merges, self.readied = 'success', [], []

    def mark_ready(self, number):
        self.readied.append(number)
        self.pr = dict(self.pr, draft=False)

    def find_pr(self, branch):
        return self.pr

    def ci_state(self, number, sha):
        return self.ci

    def merge(self, number, sha):
        self.merges.append((number, sha))


def fake_git(worktree, *args, dirty=''):
    if args[0] == 'diff':
        return 'DIFFTEXT'
    if args[0] == 'status':
        return dirty
    return 'feat/x' if '--abbrev-ref' in args else SHA


class CoordinatorCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        base = Path(self.tmp.name)
        self.root, self.wt, self.shared = base / 'repo', base / 'wt', base / 'shared'
        (self.root / '.pipeline/tickets').mkdir(parents=True)
        (self.root / '.pipeline/project.yaml').write_text(
            'version: 1\nbase_ref: origin/main\nmerge_method: merge\ngate: [[echo, test]]\n')
        (self.root / '.pipeline/prompts/roles').mkdir(parents=True)
        self.wt.mkdir()
        for role in ROLES:
            (self.root / f'.pipeline/prompts/roles/{role}.md').write_text(f'# {role}')
        self.backend, self.forge = FakeBackend(self.wt), FakeForge()
        self.gate_calls, self.gate_results, self.approved, self.dirty = [], [], True, ''
        self.c = self.make()

    def make(self):
        return pc.Coordinator(self.root, self.backend, self.forge, CONFIG, shared=self.shared,
                              gate=self.fake_gate, git=self.fake_git, approval=self.fake_approval)

    def fake_git(self, worktree, *args):
        return fake_git(worktree, *args, dirty=self.dirty)

    def fake_approval(self, root):
        if not self.approved:
            raise ValueError('agents.yaml changed since human approval')

    def fake_gate(self, worktree):
        self.gate_calls.append(worktree)
        return self.gate_results.pop(0) if self.gate_results else (0, 'gate ok')

    def ticket(self, ident, tier='medium', depends=(), touches=None, status='ready'):
        body = {'id': ident, 'title': ident, 'tier': tier, 'effort_override': None,
                'depends_on': list(depends), 'requirements': ['REQ-1'], 'acceptance_tests': ['AT-1'],
                'touches': touches or [f'mod/{ident}.kt'], 'consumes': [], 'produces': ['GET /x'],
                'status': status}
        text = f'---\n{yaml.safe_dump(body)}---\n\n## 목표\n{ident}\n'
        (self.root / f'.pipeline/tickets/{ident}.md').write_text(text)

    def rec(self, ident):
        return self.c.state['tickets'][ident]

    def finish(self, ident, state='succeeded'):
        self.backend.status[self.rec(ident)['dispatch_id']] = state

    def review(self, ident, decision='approve', **override):
        rec = self.rec(ident)
        findings = [] if decision == 'approve' else [
            {'id': 'F1', 'severity': 'blocking', 'file': 'a.kt', 'evidence': 'race'}]
        data = {'schema': pc.SCHEMA_REVIEW, 'ticket': ident, 'dispatch_id': rec['dispatch_id'],
                'round': rec['review_round'] + 1, 'decision': decision, 'findings': findings, 'head_sha': SHA}
        data.update(override)
        Path(rec['report']).write_text(json.dumps(data))
        self.finish(ident)

    def judge(self, ident, decision='merge', **override):
        rec = self.rec(ident)
        data = {'schema': pc.SCHEMA_JUDGE, 'ticket': ident, 'dispatch_id': rec['dispatch_id'],
                'decision': decision, 'requirements_met': ['REQ-1'], 'rationale': 'ok', 'head_sha': SHA}
        if decision == 'merge_with_followup':
            self.ticket('T-900', status='pending')
            data['followup_ticket'] = 'T-900'
        data.update(override)
        Path(rec['report']).write_text(json.dumps(data))
        self.finish(ident)

    def to_review(self, ident):
        self.c.tick()
        self.finish(ident)
        self.c.tick()
        self.assertEqual(self.rec(ident)['state'], 'reviewing')

    def blocking_round(self, ident):
        self.review(ident, 'request_changes')
        self.c.tick()
        self.finish(ident)
        self.c.tick()


class SchedulingTest(CoordinatorCase):
    def test_ready_dag_waits_for_merged_dependencies(self):
        self.ticket('T-1', 'low')
        self.ticket('T-2', 'low', depends=['T-1'])
        self.ticket('T-3', 'low', status='pending')
        self.c.tick()
        self.assertEqual(set(self.c.state['tickets']), {'T-1'})
        self.to_review('T-1')
        self.review('T-1')
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'merging')
        self.assertNotIn('T-2', self.c.state['tickets'])
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'merged')
        self.assertEqual(self.rec('T-2')['state'], 'implementing')
        self.assertNotIn('T-3', self.c.state['tickets'])

    def test_at_most_two_implementers_and_slot_reuse(self):
        for ident in ('T-1', 'T-2', 'T-3'):
            self.ticket(ident)
        self.c.tick()
        self.assertEqual(len(self.backend.of('start_implementer')), 2)
        self.assertNotIn('T-3', self.c.state['tickets'])
        self.finish('T-1')
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'reviewing')
        self.assertEqual(self.rec('T-3')['state'], 'implementing')
        self.assertLessEqual(len(self.c.records('implementing')), 2)

    def test_overlapping_touches_never_run_in_parallel(self):
        self.ticket('T-1', touches=['mod/a'])
        self.ticket('T-2', touches=['mod/a/B.kt'])
        self.c.tick()
        self.assertEqual(set(self.c.state['tickets']), {'T-1'})

    def test_unapproved_agents_config_blocks_all_dispatch(self):
        self.ticket('T-1')
        self.approved = False
        report = self.c.tick()
        self.assertFalse(report['config_approved'])
        self.assertEqual(self.backend.calls, [])

    def test_invalid_ticket_is_reported_and_never_dispatched(self):
        (self.root / '.pipeline/tickets/T-9.md').write_text('no frontmatter')
        report = self.c.tick()
        self.assertEqual(report['invalid_tickets'][0]['file'], 'T-9.md')
        self.assertEqual(self.backend.calls, [])

    def test_models_are_balanced_across_candidates(self):
        self.ticket('T-1')
        self.ticket('T-2')
        self.c.tick()
        models = {self.rec(i)['plan']['implementer']['model'] for i in ('T-1', 'T-2')}
        self.assertEqual(models, {'gpt-6.1-terra', 'claude-sonnet-5-5'})

    def test_records_task_and_dispatch_ids(self):
        self.ticket('T-1')
        self.c.tick()
        self.finish('T-1')
        self.c.tick()
        rec = self.rec('T-1')
        self.assertEqual(rec['task_id'], 'task-T-1')
        self.assertEqual([d['role'] for d in rec['dispatches']], ['implementer', 'reviewer'])


class GateTest(CoordinatorCase):
    def test_gate_runs_in_worker_worktree_not_coordinator_checkout(self):
        self.ticket('T-1')
        self.to_review('T-1')
        self.assertEqual(self.gate_calls, [str(self.wt / 'T-1')])
        self.assertNotEqual(Path(self.gate_calls[0]).resolve(), self.root.resolve())

    def test_gate_failure_feeds_log_to_original_implementer(self):
        self.ticket('T-1')
        self.gate_results = [(1, 'FAIL LOG xyz')]
        self.c.tick()
        original = self.rec('T-1')['plan']['implementer']['model']
        self.finish('T-1')
        self.c.tick()
        again = self.backend.of('continue_implementer', 'T-1')[0]
        self.assertIn('FAIL LOG xyz', again['prompt'])
        self.assertEqual(again['worktree'], str(self.wt / 'T-1'))
        self.assertEqual(again['plan']['implementer']['model'], original)
        self.assertEqual((self.rec('T-1')['failures'], self.rec('T-1')['state']), (1, 'implementing'))
        self.assertEqual(self.backend.of('start_reviewer'), [])

    def test_uncommitted_changes_block_before_gate(self):
        self.ticket('T-1')
        self.c.tick()
        self.dirty = '?? NewFile.kt'
        self.finish('T-1')
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertIn('uncommitted', self.rec('T-1')['reason'])
        self.assertEqual(self.gate_calls, [])

    def test_unknown_worktree_refuses_to_gate(self):
        self.ticket('T-1')
        self.c.tick()
        self.backend.poll = lambda d: {'state': 'succeeded', 'taskId': 't', 'worktree': None}
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertEqual(self.gate_calls, [])

    def test_worktree_equal_to_coordinator_root_is_refused(self):
        self.ticket('T-1')
        self.c.tick()
        self.backend.poll = lambda d: {'state': 'succeeded', 'taskId': 't', 'worktree': str(self.root)}
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertEqual(self.gate_calls, [])

    def test_worker_failure_reassigns_instead_of_gating(self):
        self.ticket('T-1')
        self.c.tick()
        self.finish('T-1', 'failed')
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'implementing')
        self.assertEqual(self.gate_calls, [])

    def test_high_tier_escalates_after_failure_budget(self):
        self.ticket('T-1', 'high')
        self.gate_results = [(1, 'boom')] * 6
        self.c.tick()
        for _ in range(6):
            self.finish('T-1')
            self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'escalated')
        self.assertEqual(self.rec('T-1')['target'], 'main_designer')


class ReviewTest(CoordinatorCase):
    def test_reviewer_is_opposite_vendor_with_report_path_and_diff(self):
        for ident, tier in (('T-1', 'low'), ('T-2', 'medium'), ('T-3', 'high')):
            self.ticket(ident, tier)
        self.c.tick()
        for ident in ('T-1', 'T-2'):
            self.finish(ident)
        self.c.tick()
        self.finish('T-3')
        self.c.tick()
        for ident in ('T-1', 'T-2', 'T-3'):
            plan, review = self.rec(ident)['plan'], self.backend.of('start_reviewer', ident)[0]
            self.assertNotEqual(plan['implementer']['vendor'], pc.vendor_of(review['plan']['model']))
            self.assertIn(self.rec(ident)['report'], review['prompt'])
            self.assertIn('DIFFTEXT', review['prompt'])
        self.assertEqual(self.backend.of('start_reviewer', 'T-3')[0]['plan']['model'], 'gpt-6.1-sol')

    def test_missing_review_report_fails_closed(self):
        self.ticket('T-1', 'low')
        self.to_review('T-1')
        self.finish('T-1')
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertIn('report missing', self.rec('T-1')['reason'])

    def test_contradictory_or_foreign_reports_fail_closed(self):
        cases = [{'findings': [{'id': 'F', 'severity': 'blocking', 'file': 'f', 'evidence': 'e'}]},
                 {'dispatch_id': 'someone-else'}, {'round': 2}, {'schema': 'review/v0'}]
        for override in cases:
            with self.subTest(override=list(override)):
                self.setUp()
                self.ticket('T-1', 'low')
                self.to_review('T-1')
                self.review('T-1', 'approve', **override)
                self.c.tick()
                self.assertEqual(self.rec('T-1')['state'], 'blocked')
                self.assertEqual(self.forge.merges, [])

    def test_blocking_review_loops_back_to_same_worktree_and_raises_effort(self):
        self.ticket('T-1')
        self.to_review('T-1')
        self.blocking_round('T-1')
        self.assertEqual(self.rec('T-1')['state'], 'reviewing')
        self.assertEqual(self.rec('T-1')['review_round'], 1)
        self.blocking_round('T-1')
        fixes = self.backend.of('continue_implementer', 'T-1')
        self.assertEqual([f['plan']['implementer']['effort'] for f in fixes], ['medium', 'high'])
        self.assertIn('race', fixes[0]['prompt'])
        self.assertEqual({f['worktree'] for f in fixes}, {str(self.wt / 'T-1')})

    def test_three_blocking_rounds_hand_medium_ticket_to_judge(self):
        self.ticket('T-1')
        self.to_review('T-1')
        self.blocking_round('T-1')
        self.blocking_round('T-1')
        self.review('T-1', 'request_changes')
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'judging')
        judge = self.backend.of('start_judge', 'T-1')[0]
        self.assertEqual((judge['plan']['model'], judge['plan']['effort']), ('gpt-6.1-sol', 'medium'))
        self.assertIn('Only merge_with_followup or reject', judge['prompt'])

    def test_exhausted_review_rejects_plain_merge_decision(self):
        self.exhaust_medium()
        self.judge('T-1', 'merge')
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertEqual(self.forge.merges, [])

    def test_exhausted_review_merge_with_followup_reaches_merged(self):
        self.exhaust_medium()
        self.judge('T-1', 'merge_with_followup')
        self.c.tick()
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'merged')
        self.assertEqual(self.forge.merges, [(7, SHA)])
        self.assertEqual(self.rec('T-1')['evidence']['judge']['followup_ticket'], 'T-900')

    def test_judge_reject_stops_for_spec_revision(self):
        self.exhaust_medium()
        self.judge('T-1', 'reject')
        self.c.tick()
        self.assertEqual((self.rec('T-1')['state'], self.rec('T-1')['target']),
                         ('rejected', 'spec_or_ticket_revision'))
        self.assertEqual(self.forge.merges, [])

    def exhaust_medium(self):
        self.ticket('T-1')
        self.to_review('T-1')
        self.blocking_round('T-1')
        self.blocking_round('T-1')
        self.review('T-1', 'request_changes')
        self.c.tick()


class JudgmentTest(CoordinatorCase):
    def test_low_tier_skips_judgment_and_merges_after_ci(self):
        self.ticket('T-1', 'low')
        self.to_review('T-1')
        self.review('T-1')
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'merging')
        self.assertEqual(self.backend.of('start_judge'), [])
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'merged')
        self.assertTrue(self.c.tick()['idle'])

    def test_medium_and_high_require_judge_with_assigned_model(self):
        self.ticket('T-1', 'high')
        self.to_review('T-1')
        self.review('T-1')
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'judging')
        judge = self.backend.of('start_judge', 'T-1')[0]
        self.assertEqual((judge['plan']['model'], judge['plan']['vendor']), ('claude-opus-5-5', 'claude'))
        self.assertIn('REQ-1', judge['prompt'])
        self.assertIn('GET /x', judge['prompt'])

    def test_judge_must_cover_ticket_requirements(self):
        self.ticket('T-1')
        self.to_review('T-1')
        self.review('T-1')
        self.c.tick()
        self.judge('T-1', 'merge', requirements_met=[])
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')

    def test_low_tier_three_blocking_rounds_promote_to_medium(self):
        self.ticket('T-1', 'low')
        self.to_review('T-1')
        self.blocking_round('T-1')
        self.blocking_round('T-1')
        self.review('T-1', 'request_changes')
        self.c.tick()
        rec = self.rec('T-1')
        self.assertEqual(rec['plan']['tier'], 'medium')
        self.assertEqual(rec['promotions'][0]['from'], 'low')
        self.assertEqual(rec['review_round'], 0)
        self.assertEqual(self.backend.of('start_judge'), [])
        self.assertIn(rec['plan']['implementer']['model'], ('gpt-6.1-terra', 'claude-sonnet-5-5'))


class MergeTest(CoordinatorCase):
    def at_merging(self):
        self.ticket('T-1', 'low')
        self.to_review('T-1')
        self.review('T-1')
        self.c.tick()

    def test_ci_pending_waits_without_merging(self):
        self.at_merging()
        self.forge.ci = 'pending'
        self.c.tick()
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'merging')
        self.assertEqual(self.forge.merges, [])

    def test_draft_pr_is_marked_ready_after_judgment_then_merged_next_tick_on_ci(self):
        self.at_merging()
        self.forge.pr = dict(self.forge.pr, draft=True)
        self.c.tick()
        self.assertEqual((self.forge.readied, self.forge.merges, self.rec('T-1')['state']), ([7], [], 'merging'))
        self.c.tick()
        self.assertEqual((self.rec('T-1')['state'], self.forge.merges), ('merged', [(7, SHA)]))

    def test_draft_pr_is_not_readied_without_judgment_evidence(self):
        self.ticket('T-1')
        self.to_review('T-1')
        self.review('T-1')
        self.c.tick()
        self.judge('T-1')
        self.c.tick()
        self.forge.pr = dict(self.forge.pr, draft=True)
        self.corrupt(lambda rec: rec['evidence'].pop('judge'))
        self.c.tick()
        self.assertEqual((self.rec('T-1')['state'], self.forge.readied), ('blocked', []))

    def test_missing_pr_blocks_instead_of_waiting_or_creating(self):
        self.at_merging()
        self.forge.pr = None
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertIn('draft PR', self.rec('T-1')['reason'])
        self.assertEqual(self.forge.merges, [])

    def test_unverified_mark_ready_blocks_with_clear_reason(self):
        self.at_merging()
        self.forge.pr = dict(self.forge.pr, draft=True)
        self.forge.mark_ready = lambda n: pc.Forge.mark_ready(self.forge, n)
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertIn('mark_ready', self.rec('T-1')['reason'])

    def test_pending_marker_is_cleared_after_successful_external_call(self):
        self.at_merging()
        self.forge.pr = dict(self.forge.pr, draft=True)
        self.c.tick()
        self.assertIsNone(json.loads((self.shared / pc.STATE_FILE).read_text())['tickets']['T-1']['pending_call'])

    def test_ci_checks_missing_wait_a_bounded_grace_then_block(self):
        self.at_merging()
        self.forge.ci = 'missing'
        for _ in range(pc.CI_MISSING_GRACE_TICKS):
            self.c.tick()
            self.assertEqual(self.rec('T-1')['state'], 'merging')
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertEqual(self.forge.merges, [])

    def test_non_success_ci_blocks(self):
        for state in ('failure', 'stale', None):
            with self.subTest(ci=state):
                self.setUp()
                self.at_merging()
                self.forge.ci = state
                self.c.tick()
                self.assertEqual(self.rec('T-1')['state'], 'blocked')
                self.assertEqual(self.forge.merges, [])

    def test_pr_head_must_match_gated_sha(self):
        self.at_merging()
        self.forge.pr = {'number': 7, 'head_sha': 'b' * 40}
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertEqual(self.forge.merges, [])

    def test_missing_evidence_blocks_merge(self):
        for removed in ('gate', 'review'):
            with self.subTest(removed=removed):
                self.setUp()
                self.at_merging()
                self.corrupt(lambda rec: rec['evidence'].pop(removed))
                self.c.tick()
                self.assertEqual(self.rec('T-1')['state'], 'blocked')
                self.assertEqual(self.forge.merges, [])

    def test_missing_judge_evidence_blocks_medium_merge(self):
        self.ticket('T-1')
        self.to_review('T-1')
        self.review('T-1')
        self.c.tick()
        self.judge('T-1')
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'merging')
        self.corrupt(lambda rec: rec['evidence'].pop('judge'))
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertIn('judge', self.rec('T-1')['reason'])
        self.assertEqual(self.forge.merges, [])

    def corrupt(self, mutate):
        path = self.shared / pc.STATE_FILE
        state = json.loads(path.read_text())
        mutate(state['tickets']['T-1'])
        path.write_text(json.dumps(state))


class RobustnessTest(CoordinatorCase):
    def test_state_survives_restart(self):
        self.ticket('T-1', 'low')
        self.c.tick()
        self.c = self.make()
        self.assertEqual(self.rec('T-1')['state'], 'implementing')
        self.finish('T-1')
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'reviewing')
        self.assertEqual(self.backend.n, 2)

    def test_unfinished_external_call_is_blocked_not_replayed(self):
        self.ticket('T-1', 'low')
        self.to_review('T-1')
        self.corrupt_pending()
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertIn('ambiguous', self.rec('T-1')['reason'])
        self.assertEqual(len(self.backend.of('start_reviewer')), 1)

    def corrupt_pending(self):
        path = self.shared / pc.STATE_FILE
        state = json.loads(path.read_text())
        state['tickets']['T-1']['pending_call'] = 'start_reviewer'
        path.write_text(json.dumps(state))

    def test_illegal_transition_is_rejected(self):
        self.ticket('T-1')
        self.c.tick()
        rec = self.rec('T-1')
        with self.assertRaises(ValueError):
            self.c.move(rec, 'merged', 'skip everything')
        self.assertEqual(rec['state'], 'implementing')

    def test_terminal_states_have_no_exits(self):
        for state in pc.TERMINAL:
            self.assertEqual(pc.TRANSITIONS[state], set())

    def test_unverified_adapter_blocks_existing_ticket(self):  # base Backend refuses by default
        class Partial(FakeBackend):
            start_reviewer = pc.Backend.start_reviewer
        self.backend = Partial(self.wt)
        self.c = self.make()
        self.ticket('T-1', 'low')
        self.c.tick()
        self.finish('T-1')
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertIn('AdapterUnverified', self.rec('T-1')['reason'])

    def test_unverified_start_leaves_no_record_and_is_reported(self):
        self.c = pc.Coordinator(self.root, pc.Backend(), self.forge, CONFIG, shared=self.shared,
                                gate=self.fake_gate, git=self.fake_git, approval=self.fake_approval)
        self.ticket('T-1')
        report = self.c.tick()
        self.assertEqual(self.c.state['tickets'], {})
        self.assertEqual(report['refused'][0]['ticket'], 'T-1')

    def test_deploy_surface_does_not_exist(self):
        public = {n for n in dir(pc.Forge) if not n.startswith('_')}
        self.assertEqual(public, {'find_pr', 'mark_ready', 'ci_state', 'merge'})
        self.assertFalse([n for n in dir(pc) if 'deploy' in n.lower()])


class FakeOrca:
    def __init__(self, snapshot=None):
        self.snapshot, self.calls = snapshot, []

    def call(self, command, *arguments):
        self.calls.append((command, arguments))
        return self.snapshot if command == 'worker-show' else {'dispatchId': 'new-d', 'taskId': 'new-t'}


class AdapterTest(unittest.TestCase):
    def snapshot(self, state, verdict=None, dispatch='d1', task='tk'):
        return {'worker': {'state': state, 'worktreeId': 'repo-uuid::/abs/wt'},
                'dispatch': {'dispatchId': dispatch, 'taskId': task,
                             'dispatchedAt': datetime.now(timezone.utc).isoformat(), 'lastHeartbeatAt': None},
                'projection': {'liveness': {'verdict': verdict}}}

    def poll(self, snapshot):
        orca = FakeOrca(snapshot)
        return pc.OrcaBackend(ROOT, 'run', 'repo', 2, orca=orca).poll('d1'), orca

    def test_orca_poll_maps_state_and_splits_worktree_id_once(self):
        result, orca = self.poll(self.snapshot('succeeded'))
        self.assertEqual((result['state'], result['worktree'], result['taskId']), ('succeeded', '/abs/wt', 'tk'))
        self.assertEqual(orca.calls[-1], ('worker-release', ('--dispatch', 'd1')))
        self.assertEqual(self.poll(self.snapshot('failed', 'exited'))[0]['state'], 'failed')
        self.assertEqual(self.poll(self.snapshot('failed', 'alive'))[0]['state'], 'failed')
        running, orca = self.poll(self.snapshot('running'))
        self.assertEqual(running['state'], 'running')
        self.assertFalse([c for c in orca.calls if c[0] == 'worker-release'])

    def test_worktree_path_keeps_colons_after_first_separator(self):
        self.assertEqual(pc.worktree_path({'worktreeId': 'u::/a::b/c'}), '/a::b/c')
        self.assertIsNone(pc.worktree_path({'worktreeId': 'no-separator'}))
        self.assertIsNone(pc.worktree_path({}))

    def test_release_requires_proven_dispatch_and_task_identity(self):
        for snapshot in (self.snapshot('succeeded', dispatch='other'), self.snapshot('succeeded', task=None)):
            orca = FakeOrca(snapshot)
            with self.assertRaises(RuntimeError):
                pc.OrcaBackend(ROOT, 'run', 'repo', 2, orca=orca).poll('d1')
            self.assertFalse([c for c in orca.calls if c[0] == 'worker-release'])

    def test_release_happens_once_per_dispatch(self):
        orca = FakeOrca(self.snapshot('succeeded'))
        backend = pc.OrcaBackend(ROOT, 'run', 'repo', 2, orca=orca)
        backend.poll('d1')
        backend.poll('d1')
        self.assertEqual(len([c for c in orca.calls if c[0] == 'worker-release']), 1)

    def test_orca_start_uses_original_worktree_path_and_assigned_model(self):
        orca = FakeOrca()
        backend = pc.OrcaBackend(ROOT, 'run-1', 'repo', 2, orca=orca, ownership=lambda *args: args[-1](), intent_check=lambda root: None)
        role = {'vendor': 'openai', 'model': 'gpt-6.1-sol', 'effort': 'high'}
        result = backend.start_reviewer('T-1', 'PROMPT', role, '/abs/wt')
        command, argv = orca.calls[0]
        self.assertEqual(command, 'worker-start')
        pairs = dict(zip(argv[::2], argv[1::2]))
        self.assertEqual((pairs['--spec'], pairs['--worktree'], pairs['--agent']), ('PROMPT', 'path:/abs/wt', 'codex'))
        self.assertEqual((pairs['--model'], pairs['--effort'], pairs['--run']), ('gpt-6.1-sol', 'high', 'run-1'))
        self.assertEqual(result, {'dispatchId': 'new-d', 'taskId': 'new-t'})
        plan = {'implementer': {'vendor': 'claude', 'model': 'claude-sonnet-5-5', 'effort': 'medium'}}
        backend.continue_implementer('T-1', 'FIX', plan, '/abs/wt')
        self.assertIn('claude', orca.calls[1][1])

    def forge(self, outputs):
        calls = []

        def runner(argv, **kwargs):
            calls.append(argv)
            return types.SimpleNamespace(returncode=0, stdout=outputs.pop(0), stderr='')
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        (root / '.pipeline').mkdir()
        (root / '.pipeline/project.yaml').write_text(
            'version: 1\nbase_ref: origin/trunk\nmerge_method: squash\ngate: [[echo, test]]\n')
        return pc.GhForge(root, runner, intent_check=lambda root: None), calls

    def test_gh_ci_state_requires_every_check_successful_on_same_head(self):
        def state(checks, head=SHA):
            forge, _ = self.forge([json.dumps({'headRefOid': head, 'statusCheckRollup': checks})])
            return forge.ci_state(7, SHA)
        ok = {'status': 'COMPLETED', 'conclusion': 'SUCCESS'}
        self.assertEqual(state([ok, ok]), 'success')
        self.assertEqual(state([ok, {'status': 'IN_PROGRESS'}]), 'pending')
        self.assertEqual(state([ok, {'status': 'COMPLETED', 'conclusion': 'FAILURE'}]), 'failure')
        self.assertEqual(state([ok, {'status': 'COMPLETED', 'conclusion': 'SKIPPED'}]), 'failure')
        self.assertEqual(state([]), 'missing')
        self.assertEqual(state([ok], head='b' * 40), 'stale')

    def test_gh_merge_pins_head_and_never_uses_auto_or_branch_delete(self):
        forge, calls = self.forge([''])
        forge.merge(7, SHA)
        argv = calls[0]
        self.assertIn('--match-head-commit', argv)
        self.assertEqual(argv[argv.index('--match-head-commit') + 1], SHA)
        self.assertFalse({'--auto', '--delete-branch', '--admin'} & set(argv))

    def test_gh_find_pr_reports_draft_and_mark_ready_uses_gh_pr_ready(self):
        forge, calls = self.forge([json.dumps([{'number': 5, 'headRefOid': SHA, 'isDraft': True}]), ''])
        self.assertEqual(forge.find_pr('feat/x'), {'number': 5, 'head_sha': SHA, 'draft': True})
        forge.mark_ready(5)
        self.assertEqual(calls[1], ['gh', 'pr', 'ready', '5'])

    def test_gh_find_pr_rejects_ambiguity(self):
        forge, _ = self.forge([json.dumps([{'number': 1, 'headRefOid': SHA}, {'number': 2, 'headRefOid': SHA}])])
        with self.assertRaises(RuntimeError):
            forge.find_pr('feat/x')
        forge, _ = self.forge(['[]'])
        self.assertIsNone(forge.find_pr('feat/x'))


if __name__ == '__main__':
    unittest.main()
