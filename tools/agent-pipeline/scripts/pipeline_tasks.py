"""Per-task document folders: layout, ticket lookup, intent approval and task base refs.

Each task owns `.pipeline/tasks/<key>/` (intent, approval, spec, tickets, logs). The folder is
versioned with the code: an intent counts as approved only when its approval record matches the
intent bytes AND both files are present, byte-identical, on the task's base ref. M/L tasks reach
that state by merging an intent PR into the project base; S tasks push a `task/<key>` branch that
implementers then branch from, so docs and code land in one PR.
"""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess

import yaml

from pipeline_config import ENGINE, project_settings

TASK_KEY = re.compile(r'\d{8}-[a-z0-9]+(?:-[a-z0-9]+)*')
SIZES = ('S', 'M', 'L')
TASKS = Path('.pipeline/tasks')
S_BRANCH_PREFIX = 'task/'
TEMPLATE_MARKER = '<!-- agent-pipeline:template -->'


def validate_key(key):
    if not isinstance(key, str) or not TASK_KEY.fullmatch(key):
        raise ValueError('Task key must look like YYYYMMDD-slug (lowercase, digits, dashes)')
    return key


def task_dir(root, key):
    return Path(root) / TASKS / validate_key(key)


def ticket_prefix(key):
    return f'T-{validate_key(key)}-'


def task_key_of(path):
    """Return the task key for a ticket stored under .pipeline/tasks/<key>/tickets/, else None."""
    parts = Path(path).resolve().parts
    for index in range(len(parts) - 4):
        if parts[index:index + 2] == ('.pipeline', 'tasks') and parts[index + 3] == 'tickets':
            return parts[index + 2]
    return None


def ticket_paths(root):
    return sorted((Path(root) / TASKS).glob('*/tickets/T-*.md'))


def find_ticket(root, ticket_id):
    # A file whose ID lacks its folder's prefix is invalid and must not shadow the real ticket.
    matches = [path for path in sorted((Path(root) / TASKS).glob(f'*/tickets/{ticket_id}.md'))
               if TASK_KEY.fullmatch(path.parent.parent.name)
               and ticket_id.startswith(ticket_prefix(path.parent.parent.name))]
    if len(matches) > 1:
        raise ValueError(f'Ticket {ticket_id} exists in more than one task')
    return matches[0] if matches else None


def require_ticket(root, ticket_id):
    path = find_ticket(root, ticket_id)
    if path is None:
        raise ValueError(f'Ticket file not found in any task: {ticket_id}')
    return path


def load_task(root, key):
    path = task_dir(root, key) / 'task.yaml'
    if not path.is_file():
        raise ValueError(f'Task metadata missing: {path}')
    data = yaml.safe_load(path.read_text())
    if not isinstance(data, dict) or data.get('key') != key or data.get('size') not in SIZES:
        raise ValueError(f'Invalid task.yaml for {key}')
    return data


def task_base_ref(root, key):
    """M/L tasks build on the project base; S tasks build on their own pushed docs branch."""
    base = project_settings(root)['base_ref']
    if load_task(root, key)['size'] != 'S':
        return base
    remote = base.split('/', 1)[0]
    return f'{remote}/{S_BRANCH_PREFIX}{key}'


def ticket_base_ref(root, ticket_id):
    return task_base_ref(root, task_key_of(require_ticket(root, ticket_id)))


def approved_intent(root, key, show=None):
    folder = task_dir(root, key)
    intent, record = folder / 'intent.md', folder / 'approval.json'
    if not record.is_file():
        raise ValueError(f'Human intent approval is missing for task {key}')
    approval = json.loads(record.read_text())
    if hashlib.sha256(intent.read_bytes()).hexdigest() != approval.get('intent_sha256'):
        raise ValueError(f'Intent changed since human approval for task {key}')
    require_on_base(root, key, (intent, record), show or git_show)


def require_on_base(root, key, files, show):
    base = task_base_ref(root, key)
    for path in files:
        relative = path.relative_to(Path(root)).as_posix()
        if show(root, base, relative) != path.read_bytes():
            raise ValueError(f'{relative} is not on {base} as approved; merge or push it first')


def git_show(root, ref, relative):
    process = subprocess.run(['git', 'show', f'{ref}:{relative}'], cwd=root, capture_output=True)
    return process.stdout if process.returncode == 0 else None


def ticket_intent(root, ticket_id):
    approved_intent(root, task_key_of(require_ticket(root, ticket_id)))


def new_task(root, key, title, size, links=None):
    if size not in SIZES:
        raise ValueError('size must be S, M or L')
    folder = task_dir(root, key)
    if folder.exists():
        raise ValueError(f'Task already exists: {key}')
    shutil.copytree(ENGINE / 'templates/task', folder)
    meta = {'key': key, 'title': title, 'size': size,
            'created': datetime.now(timezone.utc).date().isoformat(), 'links': links or {}}
    (folder / 'task.yaml').write_text(yaml.safe_dump(meta, allow_unicode=True, sort_keys=False))
    return {'task': key, 'path': str(folder), 'ticket_prefix': ticket_prefix(key),
            'base_ref': task_base_ref(root, key)}


def approve_intent(root, key, approver, evidence):
    """Record a human approval that already happened; never call this on an agent's own judgment."""
    folder = task_dir(root, key)
    intent, record = folder / 'intent.md', folder / 'approval.json'
    if not intent.is_file():
        raise ValueError(f'intent.md missing for task {key}')
    if TEMPLATE_MARKER in intent.read_text():
        raise ValueError('intent.md is still the template; write it with the human first')
    if record.exists():
        raise ValueError('Task already has an approval; approved intents are immutable')
    if not approver.strip() or not evidence.strip():
        raise ValueError('approver and evidence are required')
    data = {'approved_by': approver, 'approved_at': datetime.now(timezone.utc).isoformat(),
            'evidence': evidence, 'intent_sha256': hashlib.sha256(intent.read_bytes()).hexdigest()}
    record.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    return data
