#!/usr/bin/env python3
"""PostToolUse(SubagentHandback) / SubagentStop: feed subagent-reported gaps into the protocol.

The user challenging finished work is only one way a gap surfaces; a verification subagent
finding one must go through the same triage. A report carrying a "미충족 / 부분 / 근거 부족"
verdict is recorded as a challenge, so completion-gate.py demands a verdict for it too.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from agent_work import record_challenge, repo_root  # noqa: E402

GAP_VERDICT = re.compile(r"미충족|근거\s*부족|\|\s*부분\s*\||^- 갭:", re.MULTILINE)
SOURCE = "subagent"


def report_text(data):
    """The report arrives as SubagentHandback's message, or as the subagent's final message."""
    handback = data.get("tool_input", {}).get("message")
    return handback or data.get("last_assistant_message", "")


def main():
    data = json.load(sys.stdin)
    report = report_text(data)
    root = repo_root(data.get("cwd", "."))
    if not root or not GAP_VERDICT.search(report):
        return
    if record_challenge(root, SOURCE, report):
        print(json.dumps({"systemMessage": "⚠ 서브에이전트가 갭을 보고함 — gap-triage에 판정 필요"},
                         ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # a missed recording must be visible, not silent
        print(json.dumps({"systemMessage": f"⚠ record-verifier-gaps 오류: {error}"}, ensure_ascii=False))
