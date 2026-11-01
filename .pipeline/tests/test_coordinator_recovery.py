"""Crash-injection regressions for state/receipt atomicity and commit evidence."""
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_coordinator import CoordinatorCase, SHA, fake_git


class RecoveryTest(CoordinatorCase):
    def at_merging(self):
        self.ticket('T-1', 'low')
        self.to_review('T-1')
        self.review('T-1')
        self.c.tick()

    def test_crash_after_followup_dispatch_never_launches_a_duplicate(self):
        self.ticket('T-1', 'low')
        self.to_review('T-1')
        rec = self.rec('T-1')
        self.c.move(rec, 'needs_fix', 'test fix')
        original = self.backend.continue_implementer

        def crash(*args):
            original(*args)
            raise SystemExit('after external dispatch')
        self.backend.continue_implementer = crash
        with self.assertRaises(SystemExit):
            self.c.tick()
        self.c = self.make()
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertEqual(len(self.backend.of('continue_implementer')), 1)

    def test_crash_during_gate_blocks_without_an_infinite_holding_state(self):
        self.ticket('T-1')
        self.c.tick()
        self.finish('T-1')
        self.c.gate = lambda path: (_ for _ in ()).throw(SystemExit('gate interrupted'))
        with self.assertRaises(SystemExit):
            self.c.tick()
        self.c = self.make()
        self.assertTrue(self.c.tick()['idle'])
        self.assertEqual(self.rec('T-1')['state'], 'blocked')

    def test_head_changed_during_gate_never_receives_passed_evidence(self):
        self.ticket('T-1')
        self.c.tick()
        self.finish('T-1')
        head = [SHA]
        self.c.git = lambda path, *args: head[0] if args == ('rev-parse', 'HEAD') else fake_git(path, *args)

        def gate(path):
            head[0] = 'b' * 40
            return 0, 'gate passed on old head'
        self.c.gate = gate
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertFalse(self.backend.of('start_reviewer'))

    def test_wrong_review_commit_blocks_and_report_lives_inside_worker(self):
        self.ticket('T-1')
        self.to_review('T-1')
        rec = self.rec('T-1')
        self.assertTrue(Path(rec['report']).is_relative_to(rec['worktree']))
        self.review('T-1', head_sha='b' * 40)
        self.c.tick()
        self.assertEqual(self.rec('T-1')['state'], 'blocked')
        self.assertFalse(self.forge.merges)

    def test_successful_merge_releases_shared_ticket_claim(self):
        self.at_merging()
        rec = self.rec('T-1')
        claim = {'dispatchId': rec['dispatches'][0]['dispatchId'], 'state': 'review', 'touches': rec['touches']}
        ledger = self.shared / 'claims.json'
        ledger.write_text(json.dumps({'T-1': claim}))
        self.c.tick()
        self.assertEqual(json.loads(ledger.read_text())['T-1']['state'], 'merged')

    def test_crash_after_merge_mutation_is_not_replayed(self):
        self.at_merging()
        original = self.forge.merge

        def crash(*args):
            original(*args)
            raise SystemExit('after merge')
        self.forge.merge = crash
        with self.assertRaises(SystemExit):
            self.c.tick()
        self.c = self.make()
        self.c.tick()
        self.assertEqual(len(self.forge.merges), 1)
        self.assertEqual(self.rec('T-1')['state'], 'blocked')

    def test_unapproved_config_cannot_merge_an_existing_ticket(self):
        self.at_merging()
        self.approved = False
        self.c.tick()
        self.assertFalse(self.forge.merges)
