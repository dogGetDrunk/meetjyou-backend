"""Shared ticket ownership and approved configuration checks."""
import json
import re
from pathlib import Path
import subprocess
import uuid


from pipeline_runtime import Orca, dispatch_lock
from pipeline_config import load_policy, verify_policy, role_text, project_settings
from pipeline_tasks import require_ticket, ticket_base_ref, ticket_intent


def state_dir(root):
    process = subprocess.run(['git', 'rev-parse', '--git-common-dir'], cwd=root,
                             capture_output=True, text=True, check=True)
    common = Path(process.stdout.strip())
    if not common.is_absolute():
        common = Path(root) / common
    path = common.resolve() / 'pipeline'
    path.mkdir(parents=True, exist_ok=True)
    return path


def write_json(path, value):
    temporary = path.with_name(f'{path.name}.{uuid.uuid4()}.tmp')
    temporary.write_text(json.dumps(value, indent=2))
    temporary.replace(path)


def approved_config(root):
    verify_policy()
    project_settings(root)


def normalize_touches(paths):
    if not paths:
        raise ValueError('touches cannot be empty')
    result = []
    for name in paths:
        path = Path(name)
        if not name or path.is_absolute() or '..' in path.parts or str(path) == '.':
            raise ValueError('Invalid repository-relative touch path')
        result.append(path.as_posix())
    return result


def overlaps(first, second):
    first, second = normalize_touches(first), normalize_touches(second)
    return any(a == b or a.startswith(f'{b}/') or b.startswith(f'{a}/')
               for a in first for b in second)


def reserve(claims, ticket, limit):
    touches = normalize_touches(ticket['touches'])
    if ticket['id'] in claims:
        raise ValueError('Ticket already claimed; use pipeline_watch.py --retry-ticket after inspecting exit')
    active = [claim for claim in claims.values() if claim['state'] not in ('merged', 'failed', 'stopped')]
    editors = [claim for claim in active if claim['state'] in ('unknown', 'in_progress')]
    if len(editors) >= limit:
        raise ValueError('Concurrent implementer limit reached')
    if any(overlaps(ticket['touches'], claim['touches']) for claim in active):
        raise ValueError('Ticket touches overlap an existing editor')
    claims[ticket['id']] = {'state': 'unknown', 'touches': touches}


def execute(root, ticket, text, plan, run, repo, limit):
    approved_config(root)
    ticket_intent(root, ticket['id'])
    shared = state_dir(root)
    with dispatch_lock(shared):
        ledger = shared / 'claims.json'
        claims = json.loads(ledger.read_text()) if ledger.exists() else {}
        runtime = Orca(root)
        reconcile(root, runtime, claims)
        reserve(claims, ticket, limit)
        write_json(ledger, claims)
        ticket_path = require_ticket(root, ticket['id'])
        content = ticket_path.read_text()
        ticket_path.write_text(re.sub(r'^status: ready\b', 'status: in_progress', content, flags=re.MULTILINE))
        role = role_text(root, 'implementer')
        base = ticket_base_ref(root, ticket['id'])
        subprocess.run(['git', 'fetch', base.split('/', 1)[0]], cwd=root, check=True)
        receipt = runtime.start(role + '\n' + text, plan, run, repo, base_ref=base)
        claims[ticket['id']].update(receipt, state='in_progress', implementer=plan['implementer'])
        write_json(ledger, claims)
        return receipt


def reconcile(root, runtime, claims):
    for identifier, claim in claims.items():
        if claim['state'] == 'merged':
            continue
        if 'dispatchId' not in claim:
            continue
        snapshot = runtime.call('worker-show', '--dispatch', claim['dispatchId'])
        worker = snapshot['worker']
        if worker['state'] == 'succeeded':
            claim['state'] = 'review'
        elif worker['state'] in ('failed', 'stopped'):
            liveness = snapshot.get('projection', {}).get('liveness') or {}
            if liveness.get('verdict') == 'exited':
                claim['state'] = worker['state']


def continue_owned(root, ticket_id, previous_dispatch, plan, launch):
    approved_config(root)
    ticket_intent(root, ticket_id)
    shared = state_dir(root)
    with dispatch_lock(shared):
        ledger = shared / 'claims.json'
        claims = json.loads(ledger.read_text())
        old = claims.get(ticket_id, {})
        if old.get('dispatchId') != previous_dispatch or old.get('state') == 'unknown':
            raise ValueError('Continuation does not own a proven ticket claim')
        config = load_policy()
        del claims[ticket_id]
        ticket = {'id': ticket_id, 'touches': old['touches']}
        reserve(claims, ticket, config['limits']['concurrent_implementers'])
        claims[ticket_id]['previous_dispatch'] = previous_dispatch
        write_json(ledger, claims)
        receipt = launch()
        claims[ticket_id].update(receipt, state='in_progress', implementer=plan['implementer'])
        write_json(ledger, claims)
        return receipt
