"""Install a self-contained release; preserve existing global agent instructions."""
import argparse
import hashlib
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile

SOURCE = Path(__file__).resolve().parent
BEGIN = '<!-- agent-pipeline:begin -->'
END = '<!-- agent-pipeline:end -->'


def update_instructions(path):
    text = path.read_text() if path.exists() else ''
    block = (SOURCE / 'global-instructions.md').read_text().strip()
    if text.count(BEGIN) != text.count(END) or text.count(BEGIN) > 1:
        raise ValueError(f'Ambiguous instruction markers: {path}')
    if BEGIN in text:
        start, end = text.index(BEGIN), text.index(END) + len(END)
        text = text[:start] + block + text[end:]
    else:
        text = text.rstrip() + '\n\n' + block + '\n'
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.pipeline.tmp')
    temporary.write_text(text)
    temporary.replace(path)


MANAGED_MARKER = '<!-- agent-pipeline:managed -->'
SHARED_SKILLS = '.agents/skills'      # read by Codex
CLAUDE_SKILLS = '.claude/skills'      # symlinked to the shared copy, one source for both agents
LEGACY_COMMANDS = ('.claude/commands', '.codex/prompts')   # pre-skill install locations


def is_managed(path):
    return path.is_file() and MANAGED_MARKER in path.read_text()


def install_skills(home):
    """Install pipeline skills for Codex and Claude Code; never touch same-named user skills."""
    installed = []
    for source in sorted(p for p in (SOURCE / 'skills').iterdir() if (p / 'SKILL.md').is_file()):
        shared = home / SHARED_SKILLS / source.name
        if shared.exists() and not is_managed(shared / 'SKILL.md'):
            print(f'Skipped (user-owned skill): {shared}')
            continue
        replace_tree(source, shared)
        installed.append(shared)
        link = home / CLAUDE_SKILLS / source.name
        if link_skill(link, shared):
            installed.append(link)
        remove_legacy_commands(home, source.name)
    return installed


def replace_tree(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    staging = target.with_name(f'.{target.name}.pipeline.tmp')
    shutil.rmtree(staging, ignore_errors=True)
    shutil.copytree(source, staging)
    if target.exists():
        shutil.rmtree(target)
    staging.rename(target)


def link_skill(link, shared):
    relative = Path('../..') / SHARED_SKILLS / shared.name
    if link.is_symlink():
        if link.resolve() == shared.resolve():
            return True
        print(f'Skipped (user-owned link): {link}')
        return False
    if link.exists():
        if not is_managed(link / 'SKILL.md'):
            print(f'Skipped (user-owned skill): {link}')
            return False
        shutil.rmtree(link)
    link.parent.mkdir(parents=True, exist_ok=True)
    link.symlink_to(relative, target_is_directory=True)
    return True


def remove_legacy_commands(home, name):
    for folder in LEGACY_COMMANDS:
        legacy = home / folder / f'{name}.md'
        if is_managed(legacy):
            legacy.unlink()
            print(f'Removed legacy command: {legacy}')


def source_files():
    return sorted(p for p in SOURCE.rglob('*') if p.is_file() and
                  '__pycache__' not in p.parts and p.suffix != '.pyc')


def install(prefix, instructions_home=None):
    files = source_files()
    digest = hashlib.sha256()
    for path in files:
        digest.update(str(path.relative_to(SOURCE)).encode() + b'\0' + path.read_bytes())
    store = prefix / 'share/agent-pipeline'
    store.mkdir(parents=True, exist_ok=True)
    release = store / 'releases' / digest.hexdigest()[:16]
    release.parent.mkdir(exist_ok=True)
    if not release.exists():
        stage_release(store, release, files)
    python = store / 'venv/bin/python'
    if not python.exists():
        subprocess.run(['python3', '-m', 'venv', str(store / 'venv')], check=True)
    subprocess.run([str(python), '-m', 'pip', 'install', '-r',
                    str(release / 'defaults/requirements.txt')], check=True)
    publish_launcher(prefix, release, python)
    if instructions_home:
        for name in ('.codex/AGENTS.md', '.claude/CLAUDE.md'):
            update_instructions(instructions_home / name)
        for target in install_skills(instructions_home):
            print(f'Skill: {target}')
    print(f'Installed: {prefix / "bin/agent-pipeline"}\nRelease: {release}')


def stage_release(store, release, files):
    with tempfile.TemporaryDirectory(prefix='install-', dir=store) as temporary:
        stage = Path(temporary) / 'release'
        for source in files:
            target = stage / source.relative_to(SOURCE)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
        stage.rename(release)


def publish_launcher(prefix, release, python):
    binary = prefix / 'bin/agent-pipeline'
    binary.parent.mkdir(parents=True, exist_ok=True)
    temporary = binary.with_name('agent-pipeline.tmp')
    entry = release / 'scripts/pipeline_cli.py'
    temporary.write_text(f'#!/bin/sh\nexec {shlex.quote(str(python))} {shlex.quote(str(entry))} "$@"\n')
    temporary.chmod(0o755)
    temporary.replace(binary)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prefix', type=Path, default=Path.home() / '.local')
    parser.add_argument('--global-instructions', action='store_true')
    args = parser.parse_args()
    install(args.prefix.resolve(), Path.home() if args.global_instructions else None)
