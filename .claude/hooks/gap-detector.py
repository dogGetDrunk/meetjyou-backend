#!/usr/bin/env python3
"""UserPromptSubmit: detect when the user challenges finished work and start the gap protocol.

A match appends the prompt to .claude/work/gap-pending-<branch>.jsonl and reminds Claude of
the protocol. completion-gate.py then refuses a completion claim until every challenge has a
verdict. Detection is keyword-based on purpose: a false positive costs one verdict line, a
miss costs a repeated incident, so the patterns lean broad.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from agent_work import record_challenge, repo_root  # noqa: E402

CHALLENGE = re.compile(
    r"진짜\s*(끝|다|맞|확실)|정말\s*(끝|다\s|맞)|확실해|사실이야|사실인가|근거\s*(있|뭐)"
    r"|빠진|빠뜨|누락|놓친|놓쳤|구멍|빈틈|허점|엣지\s*케이스"
    r"|왜\s*(통과|그걸|저걸|또|안\s*막|안\s*잡)|안\s*막히|통과하는\s*거"
    r"|틀렸|틀린\s*거|잘못\s*(됐|된|했)|거짓|말이\s*달라|아까는"
    r"|다시\s*(확인|봐|살펴)|제대로\s*(된|했|확인)|너무\s*적은데"
    r"|동작\s*안|안\s*(됐|되)잖|검증\s*안|안\s*지켜"
)
# A subagent's report is delivered to the main session as a user turn in this shape. Its
# "미충족 / 누락" wording is not the user speaking; record-verifier-gaps.py records it instead.
SUBAGENT_HANDBACK = re.compile(r"^\s*(<agent-message\b|\[Subagent hand-back\])")
SOURCE = "user"
PROTOCOL = (
    "[gap-protocol] The user may be challenging finished work. Before claiming completion: "
    "(1) answer, and fix if needed; "
    "(2) append one verdict line per challenge to .claude/work/gap-triage-<branch>.md: "
    "'- 갭: <요약> → L<n>' or '- 갭 아님: <이유>'; "
    "(3) for each real gap, add a '### G<n>' entry to docs/agent-process/gap-log.md whose "
    "'- 추가한 장치:' names a file changed on this branch or an issue '#<n>', and add that "
    "mechanism. See docs/agent-process/README.md."
)


def main():
    data = json.load(sys.stdin)
    prompt = data.get("prompt", "")
    root = repo_root(data.get("cwd", "."))
    if not root or SUBAGENT_HANDBACK.match(prompt) or not CHALLENGE.search(prompt):
        return
    record_challenge(root, SOURCE, prompt)
    print(json.dumps({
        "hookSpecificOutput": {"hookEventName": "UserPromptSubmit", "additionalContext": PROTOCOL}
    }, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # a missed detection must be visible, not silent
        print(json.dumps({"systemMessage": f"⚠ gap-detector 오류로 추궁 감지 생략: {error}"}, ensure_ascii=False))
