"""Orca transport: persist receipts, never replay an ambiguous mutation."""
import json
import fcntl
from contextlib import contextmanager
from pathlib import Path
import subprocess
import time
import uuid


class Orca:
    def __init__(self, root, executable='orca'):
        self.root = Path(root)
        self.executable = executable
        self.receipts = self.root / '.pipeline/logs/runtime'
        self.receipts.mkdir(parents=True, exist_ok=True)

    def call(self, command, *arguments):
        request = str(uuid.uuid4())
        argv = [self.executable, 'orchestration', command, *arguments, '--json']
        if command in ('worker-start', 'worker-stop', 'worker-release'):
            argv += ['--retry-request', request]
        path = self.receipts / f'{request}.json'
        record = {'argv': argv, 'started_at': time.time(), 'state': 'unknown'}
        path.write_text(json.dumps(record, indent=2))
        try:
            process = subprocess.run(argv, capture_output=True, text=True, timeout=60)
        except (subprocess.TimeoutExpired, OSError):
            raise RuntimeError(f'Orca outcome unknown; inspect before retry: {path}')
        record.update(exit_code=process.returncode, stdout=process.stdout,
                      stderr=process.stderr, state='received')
        path.write_text(json.dumps(record, indent=2))
        try:
            receipt = json.loads(process.stdout)
        except json.JSONDecodeError as error:
            raise RuntimeError(f'Invalid Orca receipt; inspect: {path}') from error
        if process.returncode or not receipt.get('ok'):
            raise RuntimeError(f'Orca refused operation; inspect recovery receipt: {path}')
        return receipt['result']

    def start(self, ticket_text, plan, run, repo, task=None, retry_of=None):
        worker = plan['implementer']
        argv = ['--run', run, '--worktree', 'new-child', '--repo', repo,
                '--base-branch', 'origin/main', '--setup', 'skip',
                '--agent', 'claude' if worker['vendor'] == 'claude' else 'codex',
                '--model', worker['model'], '--effort', worker['effort'],
                '--timeout-ms', '30000']
        if retry_of:
            raise ValueError('Retry must preserve the original worktree; use retry()')
        else:
            argv += ['--spec', ticket_text]
        return self.call('worker-start', *argv)

    def retry(self, dispatch, plan):
        snapshot = self.call('worker-show', '--dispatch', dispatch)
        worker = snapshot['worker']
        verdict = (snapshot.get('projection', {}).get('liveness') or {}).get('verdict')
        if verdict != 'exited' or worker['state'] not in ('failed', 'stopped'):
            raise ValueError('Retry requires failed/stopped state and proven process exit')
        selected = plan['implementer']
        context = snapshot['dispatch']
        return self.call('worker-start', '--task', context['taskId'],
                         '--retry-of', dispatch, '--run', context['runId'],
                         '--worktree', f"id:{worker['worktreeId']}",
                         '--agent', 'claude' if selected['vendor'] == 'claude' else 'codex',
                         '--model', selected['model'], '--effort', selected['effort'],
                         '--timeout-ms', '30000')


def recovery_action(liveness, timed_out, heartbeat_missing):
    if not timed_out or not heartbeat_missing:
        return 'wait'
    if liveness == 'exited':
        return 'inspect_then_fence_then_retry'
    return 'inspect'


@contextmanager
def dispatch_lock(root):
    path = Path(root) / 'dispatch.lock'
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('a') as handle:
        fcntl.flock(handle.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
