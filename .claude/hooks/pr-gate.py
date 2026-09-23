#!/usr/bin/env python3
"""PreToolUse(Bash): refuse `gh pr create` on a ledger branch until requirement-verifier has
checked the current diff.

"Verified" = the working-tree tree id stored by record-verifier-run.py equals the current one.
Staging or committing does not change it, so verify -> add -> commit -> PR passes; any content
edit after the verification (docs, gap-log, deletions and renames included — the verifier reads
those too) needs a re-run. File mtimes are not used: a commit rewrites the git index, which made
branches with a deleted file look edited after every commit.
Branches without a ledger are small changes and are not gated. Rows whose only evidence is
CI stay 🟡 and are judged "CI 대기" by the verifier; completion-gate.py holds the completion
claim until they turn ✅.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from agent_work import repo_root, work_file, worktree_tree_hash  # noqa: E402

PR_CREATE = re.compile(r"(^|[\s;&|(])gh\s+pr\s+create\b")
MARKER_STEM = "last-verifier"


def resolve_problem(root):
    if not os.path.exists(work_file(root, "ledger", "md")):
        return None
    marker = work_file(root, MARKER_STEM, "json")
    if not os.path.exists(marker):
        return "requirement-verifier 검증 기록 없음"
    with open(marker, encoding="utf-8") as f:
        verified = json.load(f).get("tree")
    if verified != worktree_tree_hash(root):
        return "마지막 requirement-verifier 검증 이후 파일이 변경됨 (재검증 필요)"
    return None


def main():
    data = json.load(sys.stdin)
    if not PR_CREATE.search(data.get("tool_input", {}).get("command", "")):
        return
    root = repo_root(data.get("cwd", "."))
    problem = resolve_problem(root) if root else None
    if not problem:
        return
    reason = (f"PR 생성 차단: {problem}. 원장 경로를 넘겨 requirement-verifier를 실행하고, "
              "CI 로그가 증거인 행은 🟡 유지(검증자가 'CI 대기'로 판정) 후 다시 시도할 것. "
              "검증자가 판정 표를 냈는데도 '검증 기록 없음'이 반복되면 hook 입력(agent_type) 문제이니 "
              "우회하지 말고 사용자에게 보고할 것.")
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse", "permissionDecision": "deny", "permissionDecisionReason": reason,
    }}, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # fail open but visibly: a gate bug must not block every PR
        print(json.dumps({"systemMessage": f"⚠ pr-gate 오류로 검사 생략: {error}"}, ensure_ascii=False))
