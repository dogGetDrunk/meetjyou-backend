"""Deterministic ticket coordinator with durable lifecycle and commit evidence.

See .pipeline/coordinator.md for operation and integration boundaries.
"""
import argparse
from datetime import datetime, timezone
import json
import re
import subprocess
import sys
import time
from pathlib import Path

import yaml

from pipeline_dispatch import load_ticket, resolve
from pipeline_execution import approved_config, execute, overlaps, state_dir, write_json, continue_owned, approved_intent
from pipeline_runtime import Orca, dispatch_lock

from pipeline_config import load_policy, project_root, project_settings, role_text
from pipeline_gate import run_project_gate
STATE_FILE = 'coordinator.json'
GATE_TIMEOUT_S = 3600
ORCA_START_TIMEOUT_MS = '30000'
CI_MISSING_GRACE_TICKS = 3
FEEDBACK_LIMIT = 20000
SHA_PATTERN = re.compile(r'[0-9a-f]{40}')
TICKET_ID = re.compile(r'T-[A-Za-z0-9_-]+')
SCHEMA_REVIEW = 'review/v1'
SCHEMA_JUDGE = 'judge/v1'
TRANSITIONS = {
    'queued': {'implementing', 'blocked', 'escalated'},
    'implementing': {'gating', 'needs_fix', 'blocked', 'escalated'},
    'gating': {'reviewing', 'needs_fix', 'blocked', 'escalated'},
    'needs_fix': {'implementing', 'blocked', 'escalated'},
    'reviewing': {'judging', 'merging', 'needs_fix', 'blocked', 'escalated'},
    'judging': {'merging', 'rejected', 'blocked'},
    'merging': {'merged', 'blocked'},
    'merged': set(), 'blocked': set(), 'escalated': set(), 'rejected': set(),
}
TERMINAL = ('merged', 'blocked', 'escalated', 'rejected')
HOLDING = ('queued', 'implementing', 'gating', 'needs_fix', 'reviewing', 'judging', 'merging')
FAIL_CLOSED = (RuntimeError, ValueError, KeyError, OSError, subprocess.SubprocessError)


class AdapterUnverified(RuntimeError):
    """Raised by adapters whose live CLI schema is not verified."""


class ReportError(ValueError):
    """Missing, malformed or inconsistent machine report/evidence."""


class Backend:
    """Agent-dispatch port. Every method fails closed until a verified implementation exists."""

    def _refuse(self, name):
        raise AdapterUnverified(f'{name}: live Orca schema not verified; refusing')

    def start_implementer(self, ticket, text, plan):
        self._refuse('start_implementer')

    def continue_implementer(self, ticket_id, prompt, plan, worktree, previous_dispatch=None):
        self._refuse('continue_implementer')

    def start_reviewer(self, ticket_id, prompt, role_plan, worktree):
        self._refuse('start_reviewer')

    def start_judge(self, ticket_id, prompt, role_plan, worktree):
        self._refuse('start_judge')

    def poll(self, dispatch_id):
        self._refuse('poll')


class OrcaBackend(Backend):
    """Concrete Orca adapter over live-verified worker-start/worker-show/worker-release.

    Continuation, review and judge workers are FRESH same-model sessions started in the original
    worktree (path:ABS) with the full feedback in --spec; the original process is not reused.
    """

    def __init__(self, root, run, repo, limit, orca=None, ownership=continue_owned, intent_check=approved_intent):
        self.root, self.run, self.repo, self.limit = Path(root), run, repo, limit
        self.orca = orca or Orca(root)
        self.released = set()
        self.ownership = ownership
        self.intent_check = intent_check

    def start_implementer(self, ticket, text, plan):
        receipt = execute(self.root, ticket, text, plan, self.run, self.repo, self.limit)
        return {'dispatchId': receipt.get('dispatchId'), 'taskId': receipt.get('taskId')}

    def continue_implementer(self, ticket_id, prompt, plan, worktree, previous_dispatch=None):
        launch = lambda: self._continue_launch(previous_dispatch, prompt, plan, worktree)
        return self.ownership(self.root, ticket_id, previous_dispatch, plan, launch)

    def _continue_launch(self, previous, prompt, plan, worktree):
        if previous:
            snapshot = self.orca.call('worker-show', '--dispatch', previous)
            if snapshot['worker']['state'] in ('failed', 'stopped'):
                return self.orca.retry(previous, plan)
        return self._start_in(prompt, plan['implementer'], worktree)

    def start_reviewer(self, ticket_id, prompt, role_plan, worktree):
        return self._start_in(prompt, role_plan, worktree)

    def start_judge(self, ticket_id, prompt, role_plan, worktree):
        return self._start_in(prompt, role_plan, worktree)

    def _start_in(self, spec, worker, worktree):
        self.intent_check(self.root)
        agent = 'claude' if worker['vendor'] == 'claude' else 'codex'
        receipt = self.orca.call('worker-start', '--spec', spec, '--worktree', f'path:{worktree}',
                                 '--agent', agent, '--model', worker['model'],
                                 '--effort', worker['effort'], '--run', self.run,
                                 '--timeout-ms', ORCA_START_TIMEOUT_MS)
        return {'dispatchId': receipt.get('dispatchId'), 'taskId': receipt.get('taskId')}

    def poll(self, dispatch_id):
        snapshot = self.orca.call('worker-show', '--dispatch', dispatch_id)
        worker = snapshot['worker']
        verdict = (snapshot.get('projection', {}).get('liveness') or {}).get('verdict')
        state = worker['state']
        if state in ('succeeded', 'failed', 'stopped'):
            self._release_settled(dispatch_id, snapshot)
            result = state
        else:
            result = self.deadline_state(dispatch_id)
        task = (snapshot.get('dispatch') or {}).get('taskId')
        return {'state': result, 'taskId': task, 'worktree': worktree_path(worker)}

    def deadline_state(self, dispatch_id):
        from pipeline_watch import inspect, TASK_TIMEOUT_SECONDS, HEARTBEAT_GRACE_SECONDS
        snapshot, action = inspect(self.orca, dispatch_id, TASK_TIMEOUT_SECONDS, HEARTBEAT_GRACE_SECONDS)
        if action != 'inspect_then_fence_then_retry':
            return 'running'
        self.orca.call('worker-stop', '--dispatch', dispatch_id)
        stopped = self.orca.call('worker-show', '--dispatch', dispatch_id)
        self._release_settled(dispatch_id, stopped)
        return 'failed'

    def _release_settled(self, dispatch_id, snapshot):
        """Release the settled worker terminal only when task and dispatch identity both match."""
        context = snapshot.get('dispatch') or {}
        reported = context.get('dispatchId') or context.get('id')
        if reported != dispatch_id or not context.get('taskId'):
            raise RuntimeError(f'Refusing worker-release: dispatch/task identity unproven for {dispatch_id}')
        if dispatch_id not in self.released:
            self.orca.call('worker-release', '--dispatch', dispatch_id)
            self.released.add(dispatch_id)


def worktree_path(worker):
    """worktreeId is '<repoUUID>::<absolute path>' (live-verified); anything else is unknown."""
    identity = worker.get('worktreeId') or ''
    return identity.split('::', 1)[1] if '::' in identity else None


class Forge:
    """PR port: find, CI state, merge. Deliberately has no deploy surface."""

    def find_pr(self, branch):
        raise AdapterUnverified('find_pr: not implemented')

    def mark_ready(self, number):
        """Take the implementer's draft PR out of draft after judgment approves."""
        raise AdapterUnverified('mark_ready: not implemented')

    def ci_state(self, number, sha):
        raise AdapterUnverified('ci_state: not implemented')

    def merge(self, number, sha):
        raise AdapterUnverified('merge: not implemented')


class GhForge(Forge):
    """gh CLI forge. pr view/list fields live-verified; unknown shapes mean not-success."""

    def __init__(self, root, runner=subprocess.run, intent_check=approved_intent):
        self.root, self.runner = Path(root), runner
        self.intent_check = intent_check

    def _gh(self, *args):
        process = self.runner(['gh', *args], cwd=self.root, capture_output=True, text=True, timeout=60)
        if process.returncode:
            raise RuntimeError(f'gh {args[0]} {args[1]} failed: {process.stderr.strip()}')
        return process.stdout

    def find_pr(self, branch):
        items = json.loads(self._gh('pr', 'list', '--head', branch, '--state', 'open',
                                    '--json', 'number,headRefOid,isDraft'))
        if len(items) > 1:
            raise RuntimeError(f'Ambiguous PRs for branch {branch}')
        if not items:
            return None
        return {'number': items[0]['number'], 'head_sha': items[0]['headRefOid'],
                'draft': bool(items[0].get('isDraft'))}

    def mark_ready(self, number):
        self.intent_check(self.root)
        self._gh('pr', 'ready', str(number))

    def ci_state(self, number, sha):
        data = json.loads(self._gh('pr', 'view', str(number), '--json', 'headRefOid,statusCheckRollup'))
        if data.get('headRefOid') != sha:
            return 'stale'
        checks = data.get('statusCheckRollup') or []
        if not checks:
            return 'missing'
        verdicts = {check_verdict(check) for check in checks}
        return 'failure' if 'failure' in verdicts else 'pending' if 'pending' in verdicts else 'success'

    def merge(self, number, sha):
        self.intent_check(self.root)
        self._gh('pr', 'merge', str(number), '--' + project_settings(self.root)['merge_method'], '--match-head-commit', sha)


def check_verdict(check):
    if check.get('status') and check['status'] != 'COMPLETED':
        return 'pending'
    outcome = check.get('conclusion') or check.get('state') or ''
    if outcome == 'SUCCESS':
        return 'success'
    return 'pending' if outcome in ('', 'PENDING', 'EXPECTED') else 'failure'


def run_gate(root, worktree):
    return run_project_gate(root, worktree, GATE_TIMEOUT_S)


def git_read(worktree, *args):
    """Read-only git query inside a worktree."""
    process = subprocess.run(['git', '-C', str(worktree), *args], capture_output=True,
                             text=True, check=True, timeout=120)
    return process.stdout.strip()


def vendor_of(model):
    return 'claude' if model.startswith('claude') else 'openai'


def load_report(path, schema, expect):
    if not path or not Path(path).is_file():
        raise ReportError(f'report missing: {path}')
    try:
        data = json.loads(Path(path).read_text())
    except json.JSONDecodeError as error:
        raise ReportError(f'report is not valid JSON: {path}') from error
    if not isinstance(data, dict) or data.get('schema') != schema:
        raise ReportError(f'report schema mismatch (want {schema})')
    for key, value in expect.items():
        if data.get(key) != value:
            raise ReportError(f'report field {key} mismatch: {data.get(key)!r} != {value!r}')
    return data


def parse_review(path, expect):
    data = load_report(path, SCHEMA_REVIEW, expect)
    findings = data.get('findings')
    if data.get('decision') not in ('approve', 'request_changes') or not isinstance(findings, list):
        raise ReportError('review decision/findings invalid')
    for finding in findings:
        valid = (isinstance(finding, dict) and finding.get('severity') in ('blocking', 'nit')
                 and all(isinstance(finding.get(k), str) and finding[k] for k in ('id', 'file', 'evidence')))
        if not valid:
            raise ReportError('review finding must carry id/severity/file/evidence')
    blocking = [f for f in findings if f['severity'] == 'blocking']
    if (data['decision'] == 'approve') == bool(blocking):
        raise ReportError('review decision contradicts blocking findings')
    return data


def parse_judge(path, expect, requirements):
    data = load_report(path, SCHEMA_JUDGE, expect)
    decision = data.get('decision')
    if decision not in ('merge', 'merge_with_followup', 'reject'):
        raise ReportError('judge decision invalid')
    if not isinstance(data.get('rationale'), str) or not data['rationale']:
        raise ReportError('judge rationale missing')
    if decision == 'reject':
        return data
    if not set(requirements) <= set(data.get('requirements_met') or []):
        raise ReportError('judge requirements_met does not cover the ticket requirements')
    follow = data.get('followup_ticket')
    if (decision == 'merge_with_followup') != bool(follow) or (follow and not TICKET_ID.fullmatch(str(follow))):
        raise ReportError('followup_ticket must be present exactly for merge_with_followup')
    return data


class Coordinator:
    def __init__(self, root, backend, forge, config, shared=None, gate=None, git=git_read,
                 approval=approved_config):
        self.root = Path(root)
        self.backend, self.forge, self.config = backend, forge, config
        self.shared = Path(shared) if shared else state_dir(root)
        self.gate = gate or (lambda worktree: run_gate(self.root, worktree))
        self.git, self.approval = git, approval
        self.state = self.load_state()
        self.events = []
        self.refused = []

    # ---- persistence -------------------------------------------------
    def load_state(self):
        path = self.shared / STATE_FILE
        return json.loads(path.read_text()) if path.exists() else {'version': 1, 'seq': 0, 'tickets': {}}

    def save(self):
        self.shared.mkdir(parents=True, exist_ok=True)
        write_json(self.shared / STATE_FILE, self.state)

    def records(self, *states):
        return [r for _, r in sorted(self.state['tickets'].items()) if r['state'] in states]

    def move(self, rec, target, reason):
        if target not in TRANSITIONS[rec['state']]:
            raise ValueError(f"illegal transition {rec['state']} -> {target} for {rec['id']}")
        self.state['seq'] += 1
        rec['history'].append({'seq': self.state['seq'], 'from': rec['state'], 'to': target, 'reason': reason})
        rec['state'] = target
        rec['pending_call'] = None
        self.events.append({'ticket': rec['id'], 'to': target, 'reason': reason})
        self.save()
        self.sync_ticket(rec)

    def sync_ticket(self, rec):
        statuses = {'implementing': 'in_progress', 'gating': 'in_progress', 'needs_fix': 'in_progress',
                    'reviewing': 'review', 'judging': 'review', 'merging': 'review', 'merged': 'merged',
                    'blocked': 'blocked', 'rejected': 'blocked', 'escalated': 'blocked'}
        path = self.root / f".pipeline/tickets/{rec['id']}.md"
        if rec['state'] not in statuses or not path.exists():
            return
        text = path.read_text()
        parts = text.split('\n---', 1)
        if len(parts) != 2:
            return
        header = re.sub(r'^status:.*$', f"status: {statuses[rec['state']]}", parts[0], flags=re.MULTILINE)
        path.write_text(f'{header}\n---{parts[1]}')

    def append_audit(self, name, rec, detail):
        folder = self.root / '.pipeline/logs'
        folder.mkdir(parents=True, exist_ok=True)
        with (folder / f'{name}.md').open('a') as output:
            stamp = datetime.now(timezone.utc).isoformat()
            output.write(f"| {stamp} | {rec['id']} | {detail} | {rec['task_id']} | {rec['dispatch_id']} |\n")

    def block(self, rec, reason):
        if rec['state'] not in TERMINAL:
            rec['reason'] = reason
            self.move(rec, 'blocked', reason)

    def guarded(self, action, rec, *args):
        try:
            action(rec, *args)
        except AdapterUnverified as error:
            self.block(rec, f'AdapterUnverified: {error}')
        except FAIL_CLOSED as error:
            self.block(rec, f'{type(error).__name__}: {error}')

    def call(self, rec, name, *args):
        return self.call_on(rec, self.backend, name, *args)

    def call_on(self, rec, owner, name, *args):
        """Persist intent before an external call; an unfinished marker means the outcome is unknown."""
        rec['pending_call'] = name
        self.save()
        try:
            result = getattr(owner, name)(*args)
        except AdapterUnverified:
            rec['pending_call'] = None
            raise
        # Receipt and state must be persisted together by the caller's next move().
        return result

    def recover(self):
        for rec in self.state['tickets'].values():
            self.sync_ticket(rec)
        for rec in self.records('merged'):
            self.release_claim(rec)
        for rec in self.records(*HOLDING):
            if rec['state'] == 'gating':
                self.block(rec, 'interrupted gate: evidence unknown; inspection required')
            elif rec.get('pending_call'):
                self.block(rec, f"ambiguous {rec['pending_call']}: inspect Orca before any manual retry")

    # ---- tick --------------------------------------------------------
    def tick(self):
        with dispatch_lock(self.shared / 'coordinator'):
            self.state, self.events, self.refused = self.load_state(), [], []
            self.recover()
            tickets, invalid = self.load_tickets()
            approved = self.config_approved()
            if not approved:
                return {'events': self.events, 'refused': [], 'invalid_tickets': invalid,
                        'config_approved': False, 'idle': not bool(self.records(*HOLDING))}
            for rec in self.records('implementing', 'reviewing', 'judging', 'merging'):
                self.guarded(self.advance, rec, tickets)
            if approved:
                self.fill_slots(tickets)
            live = bool(self.records(*HOLDING))
            return {'events': self.events, 'refused': self.refused, 'invalid_tickets': invalid,
                    'config_approved': approved, 'idle': not live}

    def config_approved(self):
        try:
            self.approval(self.root)
            return True
        except (ValueError, KeyError, OSError):
            return False

    def load_tickets(self):
        tickets, invalid = {}, []
        for path in sorted((self.root / '.pipeline/tickets').glob('T-*.md')):
            try:
                ticket = load_ticket(path)
                tickets[ticket['id']] = {'ticket': ticket, 'text': path.read_text()}
            except (ValueError, KeyError, OSError, yaml.YAMLError) as error:
                invalid.append({'file': path.name, 'error': str(error)})
        return tickets, invalid

    def advance(self, rec, tickets):
        handler = {'implementing': self.on_implementing, 'reviewing': self.on_review,
                   'judging': self.on_judge, 'merging': self.on_merge}[rec['state']]
        handler(rec, tickets[rec['id']] if rec['id'] in tickets else self.missing_ticket(rec))

    def missing_ticket(self, rec):
        raise ValueError(f"ticket file vanished for {rec['id']}")

    # ---- scheduling --------------------------------------------------
    def merged_ids(self, tickets):
        merged = {i for i, r in self.state['tickets'].items() if r['state'] == 'merged'}
        merged |= {i for i, t in tickets.items() if t['ticket']['status'] == 'merged'}
        return merged

    def is_eligible(self, entry, merged):
        ticket = entry['ticket']
        if ticket['id'] in self.state['tickets'] or ticket['status'] != 'ready':
            return False
        if not set(ticket['depends_on']) <= merged:
            return False
        return not any(overlaps(ticket['touches'], r['touches']) for r in self.records(*HOLDING))

    def free_slots(self):
        return self.config['limits']['concurrent_implementers'] - len(self.records('implementing'))

    def fill_slots(self, tickets):
        for rec in self.records('needs_fix'):
            if self.free_slots() > 0 and rec['id'] in tickets:
                self.guarded(self.start_implementing, rec, tickets[rec['id']])
        merged = self.merged_ids(tickets)
        for identifier in sorted(tickets):
            if self.free_slots() <= 0:
                return
            if self.is_eligible(tickets[identifier], merged):
                self.dispatch_new(tickets[identifier])

    def dispatch_new(self, entry):
        ticket = entry['ticket']
        rec = {'id': ticket['id'], 'state': 'queued', 'base_tier': ticket['tier'],
               'effort_override': ticket.get('effort_override'), 'touches': list(ticket['touches']),
               'requirements': list(ticket['requirements']), 'produces': list(ticket['produces']),
               'failures': 0, 'rounds_total': 0, 'review_round': 0, 'model': None, 'plan': None,
               'worktree': None, 'dispatch_id': None, 'task_id': None, 'dispatches': [],
               'promotions': [], 'evidence': {}, 'feedback': None, 'exhausted': False,
               'pending_call': None, 'history': []}
        self.state['tickets'][rec['id']] = rec
        try:
            self.start_implementing(rec, entry)
        except AdapterUnverified as error:
            del self.state['tickets'][rec['id']]
            self.save()
            self.refused.append({'ticket': rec['id'], 'reason': str(error)})
        except FAIL_CLOSED as error:
            self.block(rec, f'{type(error).__name__}: {error}')

    def balanced_model(self, tier):
        used = [r['model'] for r in self.state['tickets'].values()]
        options = [c['model'] for c in self.config['implement'][tier]]
        return min(options, key=lambda m: (used.count(m), options.index(m)))

    def plan_for(self, rec):
        if rec['model'] is None:
            rec['model'] = self.balanced_model(rec['base_tier'])
        ticket = {'tier': rec['base_tier'], 'effort_override': rec['effort_override']}
        return resolve(self.config, ticket, rec['model'], None, rec['failures'], rec['rounds_total'])

    def role_text(self, role):
        return role_text(self.root, role)

    def start_implementing(self, rec, entry):
        plan = self.plan_for(rec)
        if plan['action'] == 'escalate':
            rec['target'] = plan['target']
            return self.move(rec, 'escalated', 'implementer failures exhausted')
        if rec['worktree'] is None:
            result = self.call(rec, 'start_implementer', entry['ticket'], entry['text'], plan)
        else:
            prompt = self.fix_prompt(rec, entry)
            previous = next(a['dispatchId'] for a in reversed(rec['dispatches']) if a['role'] == 'implementer')
            result = self.call(rec, 'continue_implementer', rec['id'], prompt, plan, rec['worktree'], previous)
        self.note_promotion(rec, plan)
        self.register(rec, 'implementer', result)
        rec['plan'], rec['feedback'] = plan, None
        if rec['worktree'] is not None:
            self.append_audit('retries', rec, f"{plan['implementer']['model']} | {plan['implementer']['effort']} | retry")
        self.move(rec, 'implementing', f"dispatched {plan['implementer']['model']}")

    def note_promotion(self, rec, plan):
        previous = rec['plan']['tier'] if rec['plan'] else rec['base_tier']
        if plan['tier'] != previous:
            rec['promotions'].append({'from': previous, 'to': plan['tier'], 'failures': rec['failures'],
                                      'rounds': rec['rounds_total']})
            rec['review_round'] = 0
            self.append_audit('promotions', rec, f"{previous} | {plan['tier']} | failures={rec['failures']}")

    def register(self, rec, role, result):
        dispatch = (result or {}).get('dispatchId')
        if not dispatch or not isinstance(dispatch, str):
            raise ValueError(f'{role} dispatch returned no dispatchId')
        rec['dispatch_id'] = dispatch
        rec['task_id'] = result.get('taskId') or rec['task_id']
        rec['dispatches'].append({'role': role, 'dispatchId': dispatch, 'taskId': result.get('taskId')})

    def fix_prompt(self, rec, entry):
        feedback = rec['feedback'] or {}
        return (f"{self.role_text('implementer')}\n\n# Fix request ({feedback.get('kind', 'unknown')})\n"
                f"{feedback.get('body', '')}\n\n# Ticket card\n{entry['text']}")

    # ---- implementation, gate ---------------------------------------
    def on_implementing(self, rec, entry):
        status = self.call(rec, 'poll', rec['dispatch_id'])
        rec['task_id'] = status.get('taskId') or rec['task_id']
        rec['worktree'] = status.get('worktree') or rec['worktree']
        if status['state'] == 'running':
            rec['pending_call'] = None
            self.save()
            return
        if status['state'] != 'succeeded':
            rec['failures'] += 1
            rec['feedback'] = {'kind': 'worker_failure', 'body': f"Previous implementer {status['state']}"}
            return self.route_failure(rec, 'implementation worker failed or timed out')
        self.run_gate(rec, entry)

    def worktree_of(self, rec):
        path = rec['worktree']
        if not path or not Path(path).is_dir():
            raise ValueError('worker worktree path unknown; refusing to gate')
        if Path(path).resolve() == self.root.resolve():
            raise ValueError('worktree equals coordinator checkout; refusing to gate')
        return path

    def run_gate(self, rec, entry):
        self.move(rec, 'gating', 'implementer finished')
        worktree = self.worktree_of(rec)
        self.require_clean(worktree)
        before = self.git(worktree, 'rev-parse', 'HEAD')
        code, output = self.gate(worktree)
        self.require_clean(worktree)
        if self.git(worktree, 'rev-parse', 'HEAD') != before:
            raise ValueError('HEAD changed during gate; refusing stale evidence')
        log = self.write_gate_log(rec, output)
        if code != 0:
            rec['failures'] += 1
            rec['feedback'] = {'kind': 'gate_failure', 'body': output[-FEEDBACK_LIMIT:], 'log': str(log)}
            return self.route_failure(rec, 'gate failed')
        sha = self.git(worktree, 'rev-parse', 'HEAD')
        if not SHA_PATTERN.fullmatch(sha):
            raise ValueError('worktree HEAD sha unreadable')
        branch = self.git(worktree, 'rev-parse', '--abbrev-ref', 'HEAD')
        rec['evidence'] = {'gate': {'passed': True, 'head_sha': sha, 'log': str(log)}, 'branch': branch}
        self.start_review(rec, entry)

    def require_clean(self, worktree):
        """Uncommitted or untracked changes mean HEAD does not represent the implemented work."""
        dirty = self.git(worktree, 'status', '--porcelain')
        if dirty:
            raise ValueError(f'worktree has uncommitted changes; commit before gate: {dirty[:200]!r}')

    def write_gate_log(self, rec, output):
        folder = self.shared / 'gate-logs'
        folder.mkdir(parents=True, exist_ok=True)
        path = folder / f"{rec['id']}-{len(rec['history'])}.log"
        path.write_text(output)
        return path

    def route_failure(self, rec, reason):
        plan = self.plan_for(rec)
        if plan['action'] == 'escalate':
            rec['target'] = plan['target']
            return self.move(rec, 'escalated', reason)
        self.move(rec, 'needs_fix', reason)

    # ---- review ------------------------------------------------------
    def report_path(self, rec, kind):
        folder = Path(self.worktree_of(rec)) / '.pipeline/logs/runtime/reports' / rec['id']
        folder.mkdir(parents=True, exist_ok=True)
        return folder / f"{kind}-{len(rec['dispatches']) + 1}.json"

    def start_review(self, rec, entry):
        plan = rec['plan']
        if plan['reviewer']['vendor'] == plan['implementer']['vendor']:
            raise ValueError('same-vendor review forbidden')
        path = self.report_path(rec, 'review')
        prompt = self.review_prompt(rec, entry, path)
        result = self.call(rec, 'start_reviewer', rec['id'], prompt, plan['reviewer'], rec['worktree'])
        self.register(rec, 'reviewer', result)
        rec['report'], rec['evidence']['review_round'] = str(path), rec['review_round'] + 1
        self.move(rec, 'reviewing', f"review round {rec['review_round'] + 1}")

    def review_prompt(self, rec, entry, path):
        self.require_gated_head(rec)
        sha = rec['evidence']['gate']['head_sha']
        diff = self.git(self.worktree_of(rec), 'diff', f"{project_settings(self.root)['base_ref']}...{sha}")
        schema = {'schema': SCHEMA_REVIEW, 'ticket': rec['id'], 'dispatch_id': '<your dispatch id>', 'head_sha': sha,
                  'round': rec['review_round'] + 1, 'decision': 'approve|request_changes',
                  'findings': [{'id': 'F1', 'severity': 'blocking|nit', 'file': 'path',
                                'evidence': 'why', 'repeated': False}]}
        previous = (rec['feedback'] or {}).get('body', 'none')
        return (f"{self.role_text('reviewer')}\n\n# Ticket card\n{entry['text']}\n\n# Diff\n{diff}\n\n"
                f"# Previous blocking findings\n{previous}\n\n# Machine report\nWrite JSON to {path}:\n"
                f"{json.dumps(schema, indent=2)}")

    def on_review(self, rec, entry):
        status = self.call(rec, 'poll', rec['dispatch_id'])
        if status['state'] == 'running':
            rec['pending_call'] = None
            self.save()
            return
        if status['state'] != 'succeeded':
            return self.block(rec, f"reviewer worker {status['state']}")
        self.require_gated_head(rec)
        expect = {'ticket': rec['id'], 'dispatch_id': rec['dispatch_id'],
                  'round': rec['review_round'] + 1, 'head_sha': rec['evidence']['gate']['head_sha']}
        report = parse_review(rec['report'], expect)
        rec['review_round'] += 1
        rec['rounds_total'] += 1
        if report['decision'] == 'approve':
            rec['evidence']['review'] = {'approved': True, 'head_sha': rec['evidence']['gate']['head_sha']}
            return self.after_approval(rec, entry)
        self.after_blocking(rec, entry, report)

    def after_approval(self, rec, entry):
        if 'judge_approved' not in rec['plan']['merge_requires']:
            return self.move(rec, 'merging', 'approved; judgment skipped for low tier')
        self.start_judge(rec, entry)

    def after_blocking(self, rec, entry, report):
        blocking = [f for f in report['findings'] if f['severity'] == 'blocking']
        rec['failures'] += 1
        rec['evidence']['review'] = {'approved': False, 'blocking': blocking,
                                     'head_sha': rec['evidence']['gate']['head_sha']}
        rec['feedback'] = {'kind': 'review_blocking', 'body': json.dumps(blocking, indent=2)}
        limit = self.config['limits']['review_rounds']
        if rec['review_round'] >= limit and rec['plan']['tier'] != 'low':
            rec['exhausted'] = True
            return self.start_judge(rec, entry)
        self.route_failure(rec, f"blocking findings in round {rec['review_round']}")

    # ---- merge judgment ---------------------------------------------
    def start_judge(self, rec, entry):
        role_plan = rec['plan']['merge_judge']
        if not role_plan:
            raise ValueError('merge judge requested for a tier without one')
        path = self.report_path(rec, 'judge')
        prompt = self.judge_prompt(rec, entry, path)
        role = dict(role_plan, vendor=vendor_of(role_plan['model']))
        result = self.call(rec, 'start_judge', rec['id'], prompt, role, rec['worktree'])
        self.register(rec, 'judge', result)
        rec['report'] = str(path)
        self.move(rec, 'judging', 'exhausted review rounds' if rec['exhausted'] else 'review approved')

    def judge_prompt(self, rec, entry, path):
        self.require_gated_head(rec)
        sha = rec['evidence']['gate']['head_sha']
        diff = self.git(self.worktree_of(rec), 'diff', f"{project_settings(self.root)['base_ref']}...{sha}")
        facts = {'gate': rec['evidence']['gate'], 'requirements': rec['requirements'],
                 'interfaces_produced': rec['produces'], 'review': rec['evidence'].get('review'),
                 'review_rounds_exhausted': rec['exhausted']}
        schema = {'schema': SCHEMA_JUDGE, 'ticket': rec['id'], 'dispatch_id': '<your dispatch id>', 'head_sha': sha,
                  'decision': 'merge|merge_with_followup|reject', 'requirements_met': ['REQ-?'],
                  'followup_ticket': 'T-? (merge_with_followup only)',
                  'followup_card': 'Complete ticket markdown when creating a followup', 'rationale': 'why'}
        rule = 'Only merge_with_followup or reject are allowed.' if rec['exhausted'] else ''
        return (f"{self.role_text('merge-judge')}\n\n# Verifiable facts\n{json.dumps(facts, indent=2)}\n\n"
                f"# Diff\n{diff}\n\n# Machine report {rule}\nWrite JSON to {path}:\n{json.dumps(schema, indent=2)}")

    def on_judge(self, rec, entry):
        status = self.call(rec, 'poll', rec['dispatch_id'])
        if status['state'] == 'running':
            rec['pending_call'] = None
            self.save()
            return
        if status['state'] != 'succeeded':
            return self.block(rec, f"judge worker {status['state']}")
        self.require_gated_head(rec)
        expect = {'ticket': rec['id'], 'dispatch_id': rec['dispatch_id'],
                  'head_sha': rec['evidence']['gate']['head_sha']}
        report = parse_judge(rec['report'], expect, rec['requirements'])
        if report['decision'] == 'reject':
            rec['target'] = 'spec_or_ticket_revision'
            return self.move(rec, 'rejected', f"judge rejected: {report['rationale']}")
        if rec['exhausted'] and report['decision'] == 'merge':
            raise ReportError('review rounds exhausted: judge must choose merge_with_followup or reject')
        if report['decision'] == 'merge_with_followup':
            self.ensure_followup(report)
        rec['evidence']['judge'] = {'decision': report['decision'], 'followup_ticket': report.get('followup_ticket')}
        self.move(rec, 'merging', f"judge: {report['decision']}")

    def ensure_followup(self, report):
        identifier = report['followup_ticket']
        path = self.root / f'.pipeline/tickets/{identifier}.md'
        if not path.exists():
            card = report.get('followup_card')
            if not isinstance(card, str) or not card.strip():
                raise ReportError('merge_with_followup requires an existing ticket or complete followup_card')
            temporary = self.shared / f'{identifier}-followup.md'
            temporary.write_text(card)
            ticket = load_ticket(temporary)
            if ticket['id'] != identifier:
                raise ReportError('followup_card ID mismatch')
            with path.open('x') as destination:
                destination.write(card)
        if load_ticket(path)['id'] != identifier:
            raise ReportError('followup ticket ID mismatch')

    # ---- merge -------------------------------------------------------
    def merge_gaps(self, rec):
        evidence, gaps = rec['evidence'], []
        sha = (evidence.get('gate') or {}).get('head_sha')
        if not (evidence.get('gate') or {}).get('passed') or not sha:
            gaps.append('gate')
        judge = evidence.get('judge') or {}
        review = evidence.get('review') or {}
        judged = judge.get('decision') in ('merge', 'merge_with_followup')
        if not (review.get('approved') or (rec['exhausted'] and judged)) or review.get('head_sha') != sha:
            gaps.append('review')
        if 'judge_approved' in rec['plan']['merge_requires'] and not judged:
            gaps.append('judge')
        return gaps

    def on_merge(self, rec, entry):
        gaps = self.merge_gaps(rec)
        if gaps:
            raise ReportError(f"missing merge evidence: {', '.join(gaps)}")
        sha = rec['evidence']['gate']['head_sha']
        pr = self.forge.find_pr(rec['evidence']['branch'])
        if pr is None:
            return self.block(rec, 'no PR for the work branch; the implementer must push and open a draft PR')
        if pr['head_sha'] != sha:
            return self.block(rec, 'PR head differs from the gated and reviewed sha')
        if pr.get('draft'):
            self.call_on(rec, self.forge, 'mark_ready', pr['number'])
            rec['pending_call'] = None
            self.save()
            return
        state = self.forge.ci_state(pr['number'], sha)
        if state == 'pending' or self.waiting_for_checks(rec, state):
            return
        if state != 'success':
            return self.block(rec, f'CI not verified: {state}')
        self.call_on(rec, self.forge, 'merge', pr['number'], sha)
        rec['evidence']['merged_pr'] = pr['number']
        self.move(rec, 'merged', f"PR {pr['number']} merged after CI success")
        self.release_claim(rec)

    def require_gated_head(self, rec):
        worktree = self.worktree_of(rec)
        self.require_clean(worktree)
        if self.git(worktree, 'rev-parse', 'HEAD') != rec['evidence']['gate']['head_sha']:
            raise ValueError('HEAD differs from gate evidence')

    def release_claim(self, rec):
        with dispatch_lock(self.shared):
            path = self.shared / 'claims.json'
            if not path.exists():
                return
            claims = json.loads(path.read_text())
            claim = claims.get(rec['id'])
            owned = {attempt['dispatchId'] for attempt in rec['dispatches']}
            if claim and claim.get('dispatchId') in owned:
                claim.update(state='merged', merged_pr=rec['evidence']['merged_pr'])
                write_json(path, claims)

    def waiting_for_checks(self, rec, state):
        """Checks may re-register right after mark_ready; wait a bounded number of ticks, then fail closed."""
        if state != 'missing' or rec.get('ci_missing', 0) >= CI_MISSING_GRACE_TICKS:
            return False
        rec['ci_missing'] = rec.get('ci_missing', 0) + 1
        self.save()
        return True


def build_config(root):
    return load_policy()


def main():
    parser = argparse.ArgumentParser(description='Deterministic pipeline coordinator')
    parser.add_argument('--run', required=True, help='Existing Orca Run ID')
    parser.add_argument('--repo', required=True, help='Exact Orca repo selector')
    parser.add_argument('--once', action='store_true')
    parser.add_argument('--interval', type=int, default=30)
    parser.add_argument('--max-ticks', type=int, default=0, help='0 = until idle')
    args = parser.parse_args()
    root = project_root()
    project_settings(root)
    config = build_config(root)
    backend = OrcaBackend(root, args.run, args.repo, config['limits']['concurrent_implementers'])
    coordinator = Coordinator(root, backend, GhForge(root), config)
    ticks = 0
    while True:
        report = coordinator.tick()
        ticks += 1
        print(json.dumps(report, ensure_ascii=False))
        if args.once or report['idle'] or (args.max_ticks and ticks >= args.max_ticks):
            return
        time.sleep(args.interval)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, RuntimeError, yaml.YAMLError) as error:
        print(f'failed: {error}', file=sys.stderr)
        sys.exit(2)
