"""Shared helpers for the agent-process hooks: git access and per-branch work-file paths."""
import json
import os
import subprocess
import time

WORK_DIR = os.path.join(".claude", "work")
BASE_REF = "origin/main"
PENDING_STEM = "gap-pending"


def git(cwd, *args):
    result = subprocess.run(["git", "-C", cwd, *args], capture_output=True, text=True)
    return result.stdout.strip()


def repo_root(cwd):
    return git(cwd, "rev-parse", "--show-toplevel")


def branch_key(root):
    return git(root, "branch", "--show-current").replace("/", "-") or "detached"


def work_file(root, stem, extension):
    """Per-branch file so state left on one branch never blocks work on another."""
    return os.path.join(root, WORK_DIR, f"{stem}-{branch_key(root)}.{extension}")


def resolve_base(root):
    return git(root, "merge-base", "HEAD", BASE_REF) or "HEAD"


def changed_paths(root, pathspec):
    """Every path changed vs the base, deletions included (they still need verification)."""
    base = resolve_base(root)
    tracked = git(root, "diff", "--name-only", base, "--", pathspec).splitlines()
    untracked = git(root, "ls-files", "--others", "--exclude-standard", "--", pathspec).splitlines()
    return sorted(set(tracked) | set(untracked))


def existing_paths(root, paths):
    return [p for p in paths if os.path.exists(os.path.join(root, p))]


def newest_change_time(root, paths):
    """Newest mtime among changed paths; deletions fall back to the git index."""
    present = existing_paths(root, paths)
    times = [os.path.getmtime(os.path.join(root, p)) for p in present]
    if len(present) < len(paths):
        index = git(root, "rev-parse", "--path-format=absolute", "--git-path", "index")
        if index and os.path.exists(index):
            times.append(os.path.getmtime(index))
    return max(times) if times else time.time()


def base_file_text(root, path):
    return git(root, "show", f"{resolve_base(root)}:{path}")


def record_challenge(root, source, text, excerpt_length=300):
    """Append one challenge to this branch's pending list; ignore an exact repeat."""
    pending = work_file(root, PENDING_STEM, "jsonl")
    os.makedirs(os.path.dirname(pending), exist_ok=True)
    entry = {"at": time.time(), "source": source, "text": text[:excerpt_length]}
    if os.path.exists(pending):
        with open(pending, encoding="utf-8") as f:
            recorded = [json.loads(line)["text"] for line in f if line.strip()]
        if entry["text"] in recorded:
            return False
    with open(pending, "a", encoding="utf-8") as f:
        f.write(f"{json.dumps(entry, ensure_ascii=False)}\n")
    return True
