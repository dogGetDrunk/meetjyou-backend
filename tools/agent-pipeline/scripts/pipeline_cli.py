"""Shared pipeline entry point, invoked from any initialized Git repository."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

import yaml

from pipeline_config import ENGINE, load_policy, project_root, project_settings, verify_policy
from pipeline_gate import run_project_gate
from pipeline_tasks import TASKS, approve_intent, approved_intent, load_task, new_task, ticket_paths

COMMANDS = ('init', 'new-task', 'approve-intent', 'tasks', 'dispatch', 'coordinator', 'watch',
            'gate', 'policy', 'self-test', 'guide')


def initialize(root, gate, base_ref, merge_method):
    folder = root / '.pipeline'
    if (folder / 'project.yaml').exists():
        raise ValueError('Project already initialized; existing files were preserved')
    config = {'version': 1, 'base_ref': base_ref, 'merge_method': merge_method, 'gate': gate}
    # Validate before writing any project files.
    from tempfile import TemporaryDirectory
    with TemporaryDirectory() as directory:
        probe = Path(directory)
        (probe / '.pipeline').mkdir()
        (probe / '.pipeline/project.yaml').write_text(yaml.safe_dump(config))
        project_settings(probe)
    folder.mkdir(exist_ok=True)
    templates = ENGINE / 'templates/project'
    for source in templates.rglob('*'):
        target = folder / source.relative_to(templates)
        if source.is_file() and not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
    (folder / 'logs').mkdir(exist_ok=True)
    (folder / 'tasks').mkdir(exist_ok=True)
    (folder / 'tasks/.gitkeep').touch()
    (folder / 'project.yaml').write_text(yaml.safe_dump(config, sort_keys=False))
    return {'project': str(root), 'initialized': True, 'intent_approved': False}


def init_command(arguments):
    parser = argparse.ArgumentParser(description='Initialize project state without approving intent')
    parser.add_argument('--gate-json', required=True, help='JSON array of argv arrays')
    parser.add_argument('--base-ref', required=True, help='Exact remote/default branch, e.g. origin/trunk')
    parser.add_argument('--merge-method', choices=['merge', 'squash', 'rebase'], default='merge')
    args = parser.parse_args(arguments)
    root = project_root()
    result = initialize(root, json.loads(args.gate_json), args.base_ref, args.merge_method)
    print(json.dumps(result, ensure_ascii=False))


def new_task_command(arguments):
    parser = argparse.ArgumentParser(description='Create .pipeline/tasks/<key>/ from the task template')
    parser.add_argument('key', help='YYYYMMDD-slug')
    parser.add_argument('--title', required=True)
    parser.add_argument('--size', required=True, choices=['S', 'M', 'L'])
    parser.add_argument('--notion', help='Notion task URL')
    parser.add_argument('--issue', help='Issue reference, e.g. owner/repo#223')
    args = parser.parse_args(arguments)
    links = {name: value for name, value in (('notion', args.notion), ('issue', args.issue)) if value}
    root = project_root()
    project_settings(root)
    print(json.dumps(new_task(root, args.key, args.title, args.size, links), ensure_ascii=False))


def approve_intent_command(arguments):
    parser = argparse.ArgumentParser(description='Record a human intent approval that already happened')
    parser.add_argument('key')
    parser.add_argument('--approver', required=True)
    parser.add_argument('--evidence', required=True, help='Where the human approved, e.g. chat quote or PR')
    args = parser.parse_args(arguments)
    record = approve_intent(project_root(), args.key, args.approver, args.evidence)
    print(json.dumps(record, ensure_ascii=False))


def tasks_command(arguments):
    if arguments:
        raise ValueError('tasks takes no arguments')
    root = project_root()
    from pipeline_coordinator import refresh_remote
    refresh_remote(root)
    rows = []
    for folder in sorted(p for p in (root / TASKS).glob('*') if (p / 'task.yaml').is_file()):
        meta = load_task(root, folder.name)
        try:
            approved_intent(root, folder.name)
            approval = 'approved'
        except (ValueError, OSError) as error:
            approval = f'not ready: {error}'
        tickets = [p for p in ticket_paths(root) if p.parent.parent == folder]
        rows.append({'key': folder.name, 'title': meta.get('title'), 'size': meta['size'],
                     'links': meta.get('links', {}), 'intent': approval, 'tickets': len(tickets)})
    print(json.dumps(rows, ensure_ascii=False, indent=2))


HANDLERS = {'init': init_command, 'new-task': new_task_command,
            'approve-intent': approve_intent_command, 'tasks': tasks_command}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=COMMANDS)
    args, rest = parser.parse_known_args()
    if args.command in HANDLERS:
        return HANDLERS[args.command](rest)
    if args.command in ('dispatch', 'coordinator', 'watch'):
        module = f'pipeline_{args.command}.py'
        return subprocess.call([sys.executable, str(ENGINE / 'scripts' / module), *rest])
    if args.command == 'self-test':
        return subprocess.call([sys.executable, '-m', 'unittest', 'discover',
                                '-s', str(ENGINE / 'tests'), '-p', 'test_*.py', *rest])
    if rest:
        parser.error('Unexpected arguments')
    if args.command == 'guide':
        print((ENGINE / 'GUIDE.md').read_text())
    elif args.command == 'policy':
        verify_policy()
        print(json.dumps(load_policy(), ensure_ascii=False, indent=2))
    else:
        code, output = run_project_gate(project_root())
        print(output)
        return code
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, KeyError, OSError, RuntimeError, subprocess.SubprocessError, yaml.YAMLError) as error:
        print(f'failed: {error}', file=sys.stderr)
        sys.exit(2)
