"""Inspect a dispatch deadline and safely reassign only a proven exited worker."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import sys

from pipeline_runtime import Orca, dispatch_lock, recovery_action
from pipeline_execution import approved_config, state_dir, write_json, reserve
from pipeline_dispatch import load_ticket, resolve
import yaml

ROOT = Path(__file__).resolve().parents[1]


def inspect(runtime, dispatch, timeout_seconds, heartbeat_seconds):
    snapshot = runtime.call('worker-show', '--dispatch', dispatch)
    context = snapshot['dispatch']
    worker = snapshot['worker']
    if worker['state'] in ('succeeded', 'failed', 'stopped'):
        return snapshot, 'settled'
    now = datetime.now(timezone.utc)
    started = datetime.fromisoformat(context['dispatchedAt'].replace('Z', '+00:00'))
    if started.tzinfo is None:
        started = started.replace(tzinfo=timezone.utc)
    heartbeat = context.get('lastHeartbeatAt')
    last = datetime.fromisoformat(heartbeat.replace('Z', '+00:00')) if heartbeat else started
    if last.tzinfo is None:
        last = last.replace(tzinfo=timezone.utc)
    timed_out = (now - started).total_seconds() >= timeout_seconds
    stale = (now - last).total_seconds() >= heartbeat_seconds
    verdict = liveness_verdict(snapshot)
    return snapshot, recovery_action(verdict, timed_out, stale)


def liveness_verdict(snapshot):
    liveness = snapshot.get('projection', {}).get('liveness') or {}
    observation = snapshot.get('observation') or {}
    gaps = ('missing_status', 'capability_unsupported')
    if liveness.get('reason') in gaps and observation.get('exactWorker'):
        if observation.get('status') in ('live', 'exited'):
            return observation['status']
    return liveness.get('verdict')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dispatch', required=True)
    parser.add_argument('--timeout-seconds', type=int, default=1800)
    parser.add_argument('--heartbeat-seconds', type=int, default=600)
    parser.add_argument('--retry-ticket', type=Path)
    parser.add_argument('--failures', type=int, default=0)
    args = parser.parse_args()
    if min(args.timeout_seconds, args.heartbeat_seconds) <= 0:
        raise ValueError('Deadlines must be positive')
    snapshot, action = inspect(Orca(ROOT), args.dispatch, args.timeout_seconds, args.heartbeat_seconds)
    print(json.dumps({'dispatch': args.dispatch, 'action': action,
                      'state': snapshot['worker']['state'],
                      'liveness': snapshot.get('projection', {}).get('liveness')}, indent=2))
    if args.retry_ticket:
        retry_ticket(args, action)



def retry_ticket(args, action):
    if action not in ('settled', 'inspect_then_fence_then_retry'):
        raise ValueError('Retry denied: process exit is not proven')
    approved_config(ROOT)
    runtime, shared = Orca(ROOT), state_dir(ROOT)
    with dispatch_lock(shared):
        claims, ticket, ledger, plan = prepare_retry(args, shared)
        if action == 'inspect_then_fence_then_retry':
            runtime.call('worker-stop', '--dispatch', args.dispatch)
        receipt = runtime.retry(args.dispatch, plan)
        claims[ticket['id']].update(receipt, state='in_progress', implementer=plan['implementer'])
        write_json(ledger, claims)
        print(json.dumps(receipt, indent=2))


def prepare_retry(args, shared):
    ticket = load_ticket(args.retry_ticket)
    ledger = shared / 'claims.json'
    claims = json.loads(ledger.read_text())
    claim = claims.get(ticket['id'], {})
    if claim.get('dispatchId') != args.dispatch or claim.get('state') == 'unknown':
        raise ValueError('Dispatch does not own a proven retryable ticket claim')
    config = yaml.safe_load((ROOT / '.pipeline/agents.yaml').read_text())
    model = claim.get('implementer', {}).get('model')
    plan = resolve(config, ticket, model=model, failures=args.failures)
    if plan['action'] != 'dispatch':
        raise ValueError('Ticket requires main-designer escalation')
    del claims[ticket['id']]
    reserve(claims, ticket, config['limits']['concurrent_implementers'])
    claims[ticket['id']]['retryOf'] = args.dispatch
    write_json(ledger, claims)
    return claims, ticket, ledger, plan


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, RuntimeError) as error:
        print(f'failed: {error}', file=sys.stderr)
        sys.exit(2)
