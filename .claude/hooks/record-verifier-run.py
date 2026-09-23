#!/usr/bin/env python3
"""PostToolUse(SubagentHandback) / SubagentStop: record that requirement-verifier finished a
verification on this branch.

The verdict table usually travels in the hand-back message while the final assistant message that
SubagentStop carries is often a one-liner ("Final report delivered…"), so both events are
registered, as for record-verifier-gaps.py.

The marker stores the working tree's git tree id at that moment; pr-gate.py compares it with the
tree at PR time, so a PR can only be opened for content the verifier has actually seen. A run
counts only when it produced its verdict table — an aborted run verified nothing.
"""
import json
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from agent_work import repo_root, work_file, worktree_tree_hash  # noqa: E402

VERIFIER = "requirement-verifier"
VERDICT_TABLE = re.compile(r"^\|\s*ID\s*\|", re.MULTILINE)
MARKER_STEM = "last-verifier"


def report_text(data):
    handback = data.get("tool_input", {}).get("message")
    return handback or data.get("last_assistant_message", "")


def main():
    data = json.load(sys.stdin)
    if data.get("agent_type") != VERIFIER or not VERDICT_TABLE.search(report_text(data)):
        return
    root = repo_root(data.get("cwd", "."))
    if not root:
        return
    # Compute before opening the marker: a failure must not leave an empty marker behind.
    entry = {"agent_id": data.get("agent_id"), "finished_at": time.time(), "tree": worktree_tree_hash(root)}
    marker = work_file(root, MARKER_STEM, "json")
    os.makedirs(os.path.dirname(marker), exist_ok=True)
    with open(marker, "w", encoding="utf-8") as f:
        json.dump(entry, f)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # a broken recorder must be visible, not silent
        print(json.dumps({"systemMessage": f"⚠ record-verifier-run 오류: {error}"}, ensure_ascii=False))
