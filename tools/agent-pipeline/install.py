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
