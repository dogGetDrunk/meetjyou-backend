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
    for source in (ENGINE / 'templates').rglob('*'):
        target = folder / source.relative_to(ENGINE / 'templates')
        if source.is_file() and not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
    (folder / 'logs').mkdir(exist_ok=True)
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


def main():
    commands = ('init', 'dispatch', 'coordinator', 'watch', 'gate', 'policy', 'self-test', 'guide')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=commands)
    args, rest = parser.parse_known_args()
    if args.command == 'init':
        return init_command(rest)
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
