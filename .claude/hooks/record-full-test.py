#!/usr/bin/env python3
"""PostToolUse(Bash): record a marker when the FULL test suite finished successfully.

PostToolUse fires only for successful tool calls (a failing command goes to
PostToolUseFailure), and the Gradle output is checked as a second guard.
Ignored on purpose: pipes (can hide Gradle's exit code), `--tests` (partial run),
dry-run and task-exclusion flags (tests never run), and background runs (no stdout).
The marker is per branch and lives at the repo root, so checking out another branch
never makes its changes look tested. It stores the src/ tree hash at the end of the run,
which the completion gate compares instead of mtimes (a commit rewrites the git index, so
mtime-based checks voided the evidence of branches that delete a source file — #146).
"""
import json
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from agent_work import repo_root, source_tree_hash, work_file  # noqa: E402

FULL_RUN = re.compile(r"^\s*\./gradlew(\s+(clean|cleanTest|--[\w-]+))*\s+(test|check|build)(\s+--[\w-]+)*\s*$")
NOT_A_FULL_RUN = ("--tests", "--dry-run", "--exclude-task", " -m", " -x")
SUCCESS_LINE = "BUILD SUCCESSFUL"


def is_full_run(command, stdout):
    if not FULL_RUN.match(command) or any(flag in command for flag in NOT_A_FULL_RUN):
        return False
    return SUCCESS_LINE in stdout


def main():
    data = json.load(sys.stdin)
    command = data.get("tool_input", {}).get("command", "")
    response = data.get("tool_response") or {}
    stdout = response.get("stdout", "") if isinstance(response, dict) else str(response)
    root = repo_root(data.get("cwd", "."))
    if not root or not is_full_run(command, stdout):
        return
    marker = work_file(root, "last-full-test", "json")
    os.makedirs(os.path.dirname(marker), exist_ok=True)
    with open(marker, "w", encoding="utf-8") as f:
        json.dump({"command": command, "finished_at": time.time(), "src_tree": source_tree_hash(root)}, f)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # a broken recorder must be visible, not silent
        print(json.dumps({"systemMessage": f"⚠ record-full-test 오류: {error}"}, ensure_ascii=False))
