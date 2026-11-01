"""Regressions based on the actual Orca receipt shapes seen during rehearsal."""
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from pipeline_coordinator import OrcaBackend


class ReceiptTest(unittest.TestCase):
    def test_failed_settlement_releases_before_waiting_for_process_exit(self):
        snapshot = {
            'dispatch': {'id': 'ctx_failed', 'taskId': 'task_failed'},
            'worker': {'state': 'failed', 'worktreeId': 'repo::/original'},
            'projection': {'liveness': {'verdict': 'unverifiable', 'reason': 'missing_status'}},
        }
        transport = Mock()
        transport.call.side_effect = [snapshot, {'state': 'released'}]
        with tempfile.TemporaryDirectory() as directory:
            backend = OrcaBackend(directory, 'run', 'repo', 2, orca=transport)
            result = backend.poll('ctx_failed')
        self.assertEqual(result['state'], 'failed')
        self.assertEqual(transport.call.call_args.args, ('worker-release', '--dispatch', 'ctx_failed'))


    def test_expired_missing_heartbeat_only_fences_a_proven_exited_worker(self):
        from unittest.mock import Mock
        snapshot = {'dispatch': {'id': 'ctx_dead', 'taskId': 'task_dead',
                                 'dispatchedAt': '2000-01-01 00:00:00', 'lastHeartbeatAt': None},
                    'worker': {'state': 'ready', 'worktreeId': 'repo::/original'},
                    'projection': {'liveness': {'verdict': 'exited'}}}
        stopped = dict(snapshot, worker=dict(snapshot['worker'], state='stopped'))
        transport = Mock()
        transport.call.side_effect = [snapshot, snapshot, {'state': 'stopped'}, stopped, {'state': 'released'}]
        with tempfile.TemporaryDirectory() as directory:
            result = OrcaBackend(directory, 'run', 'repo', 2, orca=transport).poll('ctx_dead')
        self.assertEqual(result['state'], 'failed')
        commands = [call.args[0] for call in transport.call.call_args_list]
        self.assertEqual(commands, ['worker-show', 'worker-show', 'worker-stop', 'worker-show', 'worker-release'])
