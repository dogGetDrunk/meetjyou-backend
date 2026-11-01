"""End-to-end execution ownership with a fake Orca transport."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from pipeline_execution import execute, state_dir


class ExecutionTest(unittest.TestCase):
    def prepare(self, root):
        (root / '.pipeline/logs').mkdir(parents=True)
        (root / '.pipeline/project.yaml').write_text(
            'version: 1\nbase_ref: origin/main\nmerge_method: merge\ngate: [[echo, test]]\n')
        intent = root / '.pipeline/intent.md'
        intent.write_text('Approved test intent')
        record = {'intent_sha256': hashlib.sha256(intent.read_bytes()).hexdigest()}
        (root / '.pipeline/logs/intent-approval.json').write_text(json.dumps(record))
        shared = root / 'shared'
        shared.mkdir()
        return shared

    def test_second_run_cannot_duplicate_ticket(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared = self.prepare(root)
            ticket = {'id': 'T-001', 'touches': ['src/domain']}
            with patch('pipeline_execution.state_dir', return_value=shared), \
                 patch('pipeline_execution.subprocess.run'), \
                 patch('pipeline_execution.Orca') as runtime:
                runtime.return_value.start.return_value = {'dispatchId': 'ctx_one'}
                runtime.return_value.call.return_value = {'worker': {'state': 'ready'}}
                execute(root, ticket, 'ticket', {'implementer': {}}, 'run_one', 'repo', 2)
                with self.assertRaises(ValueError):
                    execute(root, ticket, 'ticket', {'implementer': {}}, 'run_two', 'repo', 2)
                self.assertEqual(runtime.return_value.start.call_count, 1)

    def test_lost_receipt_keeps_unknown_claim_and_blocks_duplicate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared = self.prepare(root)
            ticket = {'id': 'T-001', 'touches': ['src/domain']}
            with patch('pipeline_execution.state_dir', return_value=shared), \
                 patch('pipeline_execution.subprocess.run'), \
                 patch('pipeline_execution.Orca') as runtime:
                runtime.return_value.start.side_effect = RuntimeError('unknown')
                with self.assertRaises(RuntimeError):
                    execute(root, ticket, 'ticket', {'implementer': {}}, 'run_one', 'repo', 2)
                with self.assertRaises(ValueError):
                    execute(root, ticket, 'ticket', {'implementer': {}}, 'run_two', 'repo', 2)
            self.assertEqual(json.loads((shared / 'claims.json').read_text())['T-001']['state'], 'unknown')


class RetryOwnershipTest(unittest.TestCase):
    def test_paths_normalized_before_overlap_check(self):
        from pipeline_execution import overlaps, normalize_touches
        self.assertTrue(overlaps(['./src/domain/'], ['src/domain/File.kt']))
        for paths in ([], [''], ['.'], ['../outside'], ['/outside']):
            with self.assertRaises(ValueError):
                normalize_touches(paths)

    def test_wrong_owner_and_unknown_retry_are_rejected_before_mutation(self):
        from types import SimpleNamespace
        import pipeline_watch
        with tempfile.TemporaryDirectory() as directory:
            shared = Path(directory)
            ledger = shared / 'claims.json'
            args = SimpleNamespace(retry_ticket='ticket', dispatch='ctx_owner', failures=0)
            for claim in ({'dispatchId': 'ctx_other', 'state': 'failed'},
                          {'dispatchId': 'ctx_owner', 'state': 'unknown'}):
                ledger.write_text(json.dumps({'T-001': claim}))
                with patch('pipeline_watch.load_ticket', return_value={'id': 'T-001'}):
                    with self.assertRaises(ValueError):
                        pipeline_watch.prepare_retry(args, shared)
                self.assertEqual(json.loads(ledger.read_text())['T-001'], claim)


class ContinuationTest(unittest.TestCase):
    def test_lost_continuation_receipt_cannot_be_replayed(self):
        from pipeline_execution import continue_owned
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared = ExecutionTest.prepare(self, root)
            ledger = shared / 'claims.json'
            ledger.write_text(json.dumps({'T-001': {'state': 'review', 'dispatchId': 'ctx_old', 'touches': ['src/a']}}))
            with patch('pipeline_execution.state_dir', return_value=shared):
                def lost():
                    raise RuntimeError('receipt lost')
                with self.assertRaises(RuntimeError):
                    continue_owned(root, 'T-001', 'ctx_old', {'implementer': {}}, lost)
                with self.assertRaises(ValueError):
                    continue_owned(root, 'T-001', 'ctx_old', {'implementer': {}}, lambda: {})
            claim = json.loads(ledger.read_text())['T-001']
            self.assertEqual(claim['state'], 'unknown')
            self.assertEqual(claim['previous_dispatch'], 'ctx_old')

    def test_continuation_rechecks_global_capacity_before_launch(self):
        from pipeline_execution import continue_owned
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared = ExecutionTest.prepare(self, root)
            claims = {f'T-00{i}': {'state': 'in_progress', 'dispatchId': f'ctx_{i}', 'touches': [f'src/{i}']}
                      for i in range(1, 4)}
            claims['T-001']['state'] = 'review'
            ledger = shared / 'claims.json'
            ledger.write_text(json.dumps(claims))
            launched = []
            with patch('pipeline_execution.state_dir', return_value=shared):
                with self.assertRaises(ValueError):
                    continue_owned(root, 'T-001', 'ctx_1', {'implementer': {}}, lambda: launched.append(True))
            self.assertFalse(launched)
            self.assertEqual(json.loads(ledger.read_text()), claims)


class IntentApprovalTest(unittest.TestCase):
    def test_missing_or_changed_intent_cannot_be_approved_by_config_alone(self):
        from pipeline_execution import approved_intent
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared = ExecutionTest.prepare(self, root)
            approved_intent(root)
            (root / '.pipeline/intent.md').write_text('Changed intent')
            with self.assertRaises(ValueError):
                approved_intent(root)
            (root / '.pipeline/logs/intent-approval.json').unlink()
            with self.assertRaises(ValueError):
                approved_intent(root)
