"""Separate installed policy assets from repository-owned project state."""
import hashlib
import json
from pathlib import Path
import re
import subprocess

import yaml

ENGINE = Path(__file__).resolve().parents[1]
DEFAULTS = ENGINE / 'defaults'


def project_root(path=None):
    process = subprocess.run(['git', 'rev-parse', '--show-toplevel'], cwd=path,
                             capture_output=True, text=True, check=True)
    return Path(process.stdout.strip()).resolve()


def load_policy():
    return yaml.safe_load((DEFAULTS / 'agents.yaml').read_text())


def verify_policy():
    approval = json.loads((DEFAULTS / 'defaults-approval.json').read_text())
    digest = hashlib.sha256((DEFAULTS / 'agents.yaml').read_bytes()).hexdigest()
    if digest != approval.get('agents_sha256'):
        raise ValueError('agents.yaml changed since human approval')


def project_settings(root):
    path = Path(root) / '.pipeline/project.yaml'
    if not path.is_file():
        raise ValueError('Project is not initialized; run agent-pipeline init with explicit gate commands')
    data = yaml.safe_load(path.read_text())
    if not isinstance(data, dict) or data.get('version') != 1:
        raise ValueError('Unsupported project.yaml version')
    base = data.get('base_ref', '')
    if not isinstance(base, str) or not re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9_.-]*/[A-Za-z0-9_][A-Za-z0-9_./-]*', base):
        raise ValueError('base_ref must be a remote-tracking branch')
    if '..' in base or '//' in base or base.endswith(('/', '.')):
        raise ValueError('Invalid base_ref')
    commands = data.get('gate')
    if not isinstance(commands, list) or not commands or any(not valid_command(c) for c in commands):
        raise ValueError('gate must contain nonempty argv lists')
    if data.get('merge_method') not in ('merge', 'squash', 'rebase'):
        raise ValueError('merge_method must be merge, squash or rebase')
    return data


def valid_command(command):
    return isinstance(command, list) and bool(command) and all(
        isinstance(arg, str) and bool(arg) and '\x00' not in arg for arg in command)


def role_text(root, role):
    text = (DEFAULTS / f'prompts/roles/{role}.md').read_text()
    settings = project_settings(root)
    rules = [{'path': name, 'content': (Path(root) / name).read_text()}
             for name in ('AGENTS.md', 'CLAUDE.md') if (Path(root) / name).is_file()]
    context = {'project_rules': rules, 'base_ref': settings['base_ref'], 'gate': settings['gate']}
    return text + '\n\nProject context (explicitly supplied):\n' + json.dumps(context)
