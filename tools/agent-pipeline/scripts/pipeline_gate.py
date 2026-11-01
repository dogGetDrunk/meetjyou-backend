"""Run repository-declared validation commands without a shell."""
from pathlib import Path
import subprocess

from pipeline_config import project_settings


def run_project_gate(root, worktree=None, timeout=3600):
    commands = project_settings(root)['gate']
    output = []
    for command in commands:
        process = subprocess.run(command, cwd=Path(worktree or root),
                                 capture_output=True, text=True, timeout=timeout)
        output.append(f'$ {command!r}\n{process.stdout}{process.stderr}')
        if process.returncode:
            return process.returncode, '\n'.join(output)
    return 0, '\n'.join(output)
