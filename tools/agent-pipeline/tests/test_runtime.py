"""Runtime receipt and liveness safety tests, without provider calls."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from pipeline_runtime import Orca, recovery_action


class RuntimeTest(unittest.TestCase):
    def test_absence_never_authorizes_duplicate_editor(self):
        for verdict in ('live', 'unverifiable', None):
            self.assertEqual(recovery_action(verdict, True, True), 'inspect')
        self.assertEqual(recovery_action('exited', True, True), 'inspect_then_fence_then_retry')
        self.assertEqual(recovery_action('exited', False, True), 'wait')
        self.assertEqual(recovery_action('exited', True, False), 'wait')

    def test_receipt_persisted_with_mutation_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = Orca(directory)
            process = subprocess.CompletedProcess([], 0, '{"ok": true, "result": {"state": "ready"}}', '')
            with patch('pipeline_runtime.subprocess.run', return_value=process) as run:
                self.assertEqual(runtime.call('worker-start', '--spec', 'dummy')['state'], 'ready')
                self.assertIn('--retry-request', run.call_args.args[0])
            receipts = list(runtime.receipts.glob('*.json'))
            self.assertEqual(len(receipts), 1)
            self.assertEqual(json.loads(receipts[0].read_text())['state'], 'received')

    def test_worker_show_requests_full_record(self):
        # Orca's concise view drops the nested `worker` record; reading it raised KeyError: 'worker'.
        with tempfile.TemporaryDirectory() as directory:
            runtime = Orca(directory)
            process = subprocess.CompletedProcess([], 0, '{"ok": true, "result": {"worker": {"state": "succeeded"}}}', '')
            with patch('pipeline_runtime.subprocess.run', return_value=process) as run:
                runtime.call('worker-show', '--dispatch', 'ctx_test')
                self.assertIn('--full', run.call_args.args[0])
                self.assertNotIn('--retry-request', run.call_args.args[0])
                runtime.call('worker-start', '--spec', 'dummy')
                self.assertNotIn('--full', run.call_args.args[0])

    def test_unknown_timeout_is_not_retried(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = Orca(directory)
            with patch('pipeline_runtime.subprocess.run', side_effect=subprocess.TimeoutExpired('orca', 60)) as run:
                with self.assertRaises(RuntimeError):
                    runtime.call('worker-start', '--spec', 'dummy')
                self.assertEqual(run.call_count, 1)
            receipt = json.loads(next(runtime.receipts.glob('*.json')).read_text())
            self.assertEqual(receipt['state'], 'unknown')


class WatchTest(unittest.TestCase):
    def test_timeout_with_fresh_heartbeat_does_not_reassign(self):
        from datetime import datetime, timezone
        from pipeline_watch import inspect
        snapshot = {'dispatch': {'dispatchedAt': '2000-01-01 00:00:00',
                                  'lastHeartbeatAt': datetime.now(timezone.utc).isoformat()},
                    'worker': {'state': 'ready'},
                    'projection': {'liveness': {'verdict': 'exited'}}}
        with tempfile.TemporaryDirectory() as directory:
            runtime = Orca(directory)
            with patch.object(runtime, 'call', return_value=snapshot):
                self.assertEqual(inspect(runtime, 'ctx_test', 60, 60)[1], 'wait')
            snapshot['dispatch']['lastHeartbeatAt'] = None
            with patch.object(runtime, 'call', return_value=snapshot):
                self.assertEqual(inspect(runtime, 'ctx_test', 60, 60)[1], 'inspect_then_fence_then_retry')

    def test_retry_preserves_task_and_worktree_and_requires_exit(self):
        snapshot = {'dispatch': {'taskId': 'task_original', 'runId': 'run_original'},
                    'worker': {'state': 'failed', 'worktreeId': 'repo::/original'},
                    'projection': {'liveness': {'verdict': 'exited'}}}
        plan = {'implementer': {'vendor': 'openai', 'model': 'gpt-6-luna', 'effort': 'low'}}
        with tempfile.TemporaryDirectory() as directory:
            runtime = Orca(directory)
            with patch.object(runtime, 'call', side_effect=[snapshot, {'state': 'ready'}]) as call:
                runtime.retry('ctx_original', plan)
                argv = call.call_args.args
                self.assertIn('id:repo::/original', argv)
                self.assertIn('task_original', argv)
                self.assertNotIn('new-child', argv)
            for verdict in ('live', 'unverifiable'):
                snapshot['projection']['liveness']['verdict'] = verdict
                with patch.object(runtime, 'call', return_value=snapshot) as call:
                    with self.assertRaises(ValueError):
                        runtime.retry('ctx_original', plan)
                    self.assertEqual(call.call_count, 1)


class ClaimsTest(unittest.TestCase):
    def test_global_ticket_identity_overlap_and_capacity(self):
        from pipeline_execution import reserve
        claims = {}
        first = {'id': 'T-001', 'touches': ['src/domain']}
        reserve(claims, first, 2)
        with self.assertRaises(ValueError):
            reserve(claims, first, 2)
        with self.assertRaises(ValueError):
            reserve(claims, {'id': 'T-002', 'touches': ['src/domain/File.kt']}, 2)
        reserve(claims, {'id': 'T-002', 'touches': ['src/other']}, 2)
        with self.assertRaises(ValueError):
            reserve(claims, {'id': 'T-003', 'touches': ['src/third']}, 2)
        claims['T-001']['state'] = 'review'
        reserve(claims, {'id': 'T-003', 'touches': ['src/third']}, 2)

    def test_atomic_ledger_and_configuration_approval(self):
        from pipeline_execution import approved_config, write_json
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / '.pipeline/logs').mkdir(parents=True)
            (root / '.pipeline/agents.yaml').write_text('roles: {}')
            (root / '.pipeline/defaults-approval.json').write_text('{"agents_sha256": "wrong"}')
            with patch('pipeline_config.DEFAULTS', root / '.pipeline'):
                with self.assertRaises((ValueError, FileNotFoundError)):
                    approved_config(root)
            path = root / 'claims.json'
            write_json(path, {'T-001': {'state': 'unknown'}})
            self.assertEqual(json.loads(path.read_text())['T-001']['state'], 'unknown')
            self.assertEqual(list(root.glob('*.tmp')), [])


class LivenessTest(unittest.TestCase):
    def test_exact_host_observation_can_resolve_local_status_gap(self):
        from pipeline_watch import liveness_verdict
        snapshot = {'projection': {'liveness': {'verdict': 'unverifiable', 'reason': 'missing_status'}},
                    'observation': {'status': 'exited', 'exactWorker': True}}
        self.assertEqual(liveness_verdict(snapshot), 'exited')
        snapshot['observation']['exactWorker'] = False
        self.assertEqual(liveness_verdict(snapshot), 'unverifiable')
        snapshot['observation']['exactWorker'] = True
        snapshot['projection']['liveness']['reason'] = 'host_unavailable'
        self.assertEqual(liveness_verdict(snapshot), 'unverifiable')
