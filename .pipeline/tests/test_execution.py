"""End-to-end execution ownership with a fake Orca transport."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'scripts'))
from pipeline_execution import execute, state_dir


class ExecutionTest(unittest.TestCase):
    def prepare(self, root):
        (root / '.pipeline/logs').mkdir(parents=True)
        (root / '.pipeline/prompts/roles').mkdir(parents=True)
        config = root / '.pipeline/agents.yaml'
        config.write_text('roles: {}')
        approval = {'agents_sha256': hashlib.sha256(config.read_bytes()).hexdigest()}
        (root / '.pipeline/logs/defaults-approval.json').write_text(json.dumps(approval))
        (root / '.pipeline/prompts/roles/implementer.md').write_text('Only ticket scope')
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
