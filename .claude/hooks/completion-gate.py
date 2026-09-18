#!/usr/bin/env python3
"""Stop hook: refuse a completion claim that has no fresh evidence behind it.

Runs only when the last message claims completion.
Always checks the gap protocol (per branch):
  - verdict lines in gap-triage >= challenges recorded by gap-detector / record-verifier-gaps
  - a real gap names the layer that missed it ("- 갭: ... → L<n>")
  - new gap-log entries (vs base) with a layer and a valid mechanism >= real-gap verdicts
    (valid = a backticked file changed on this branch other than gap-log, or "이슈 #<n>")
When src/ changed vs origin/main, also checks:
  - a successful full test run happened after the newest source change
  - if 2+ production files changed: a ledger for the branch with requirement rows and no ⬜/🟡
Blocks once; if Claude is already continuing because of a Stop hook, it lets the turn
end but shows the unmet items to the user instead of looping.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from agent_work import (  # noqa: E402
    base_file_text, branch_key, changed_paths, newest_change_time, repo_root, work_file,
)

CLAIM = re.compile(
    r"(?m)^result:|완료(했|됐|되었|입니다|됨|함)|(구현|수정|추가|반영|적용|작성|보완)했습니다"
    r"|작업\s*완료|(모두|전부|전체)\s*(통과|성공)|통과(했|합니다|확인)|성공했|고쳤습니다|\bdone\b",
    re.IGNORECASE,
)
LEDGER_ROW = re.compile(r"^\|.*\|\s*(⬜|🟡|✅|➖|⏸)\s*\|", re.MULTILINE)
PENDING_ROW = re.compile(r"^\|.*\|\s*(⬜|🟡)\s*\|", re.MULTILINE)
VERDICT_LINE = re.compile(r"^- 갭( 아님)?:", re.MULTILINE)
REAL_GAP_LINE = re.compile(r"^- 갭:", re.MULTILINE)
REAL_GAP_WITH_LAYER = re.compile(r"^- 갭:.*→\s*L\d+", re.MULTILINE)
ENTRY_HEADING = re.compile(r"^### (G\d+)\b", re.MULTILINE)
LAYER_LINE = re.compile(r"^- 놓친 층:\s*L\d+", re.MULTILINE)
MECHANISM_LINE = re.compile(r"^- 추가한 장치:(.*)$", re.MULTILINE)
BACKTICKED = re.compile(r"`([^`\s]+)`")
ISSUE_REF = re.compile(r"이슈\s*#\d+")
GAP_LOG = "docs/agent-process/gap-log.md"
PRODUCTION_SRC = "src/main"
LEDGER_THRESHOLD = 2


def read(path):
    if not os.path.exists(path):
        return ""
    with open(path, encoding="utf-8") as f:
        return f.read()


def split_entries(text):
    """Map entry id -> entry body for every '### G<n>' heading."""
    matches = list(ENTRY_HEADING.finditer(text))
    ends = [m.start() for m in matches[1:]] + [len(text)]
    return {m.group(1): text[m.start():end] for m, end in zip(matches, ends)}


def is_valid_entry(entry, changed):
    if not LAYER_LINE.search(entry):
        return False
    line = MECHANISM_LINE.search(entry)
    if not line or not line.group(1).strip():
        return False
    value = line.group(1)
    cited = {path.split(":")[0] for path in BACKTICKED.findall(value)}
    return bool(ISSUE_REF.search(value)) or bool(cited & changed)


def validate_gap_log(root, real_gaps):
    new_entries = {
        key: body for key, body in split_entries(read(os.path.join(root, GAP_LOG))).items()
        if key not in split_entries(base_file_text(root, GAP_LOG))
    }
    changed = set(changed_paths(root, ".")) - {GAP_LOG}
    valid = [key for key, body in new_entries.items() if is_valid_entry(body, changed)]
    if len(valid) >= real_gaps:
        return []
    invalid = sorted(set(new_entries) - set(valid))
    detail = f" (층 또는 장치 칸이 비었거나 이 브랜치에서 안 바뀐 파일을 가리킴: {', '.join(invalid)})" if invalid else ""
    return [f"'갭' 판정 {real_gaps}건인데 {GAP_LOG}의 유효한 새 항목은 {len(valid)}건{detail}"]


def validate_gap_protocol(root):
    challenges = len(read(work_file(root, "gap-pending", "jsonl")).splitlines())
    triage = read(work_file(root, "gap-triage", "md"))
    verdicts = len(VERDICT_LINE.findall(triage))
    if verdicts < challenges:
        return [f"감지된 추궁·갭 보고 {challenges}건 중 판정은 {verdicts}건 (gap-triage-{branch_key(root)}.md 기록 필요)"]
    real_gaps = len(REAL_GAP_LINE.findall(triage))
    if len(REAL_GAP_WITH_LAYER.findall(triage)) < real_gaps:
        return ["'- 갭:' 판정에 놓친 층 표기 없음 (예: '- 갭: 영향 분석 누락 → L1')"]
    return validate_gap_log(root, real_gaps) if real_gaps else []


def validate_ledger(root):
    ledger = work_file(root, "ledger", "md")
    if not os.path.exists(ledger):
        return [f"프로덕션 파일 {LEDGER_THRESHOLD}개 이상 변경인데 요구사항 원장 없음: {os.path.relpath(ledger, root)}"]
    text = read(ledger)
    if not LEDGER_ROW.search(text):
        return ["원장에 요구사항 행이 없음 (빈 원장은 인정하지 않음)"]
    pending = PENDING_ROW.findall(text)
    return [f"원장에 미검증 행 {len(pending)}개(⬜/🟡) 남음"] if pending else []


def validate_test_evidence(root, sources):
    marker = work_file(root, "last-full-test", "json")
    if not os.path.exists(marker) or os.path.getmtime(marker) < newest_change_time(root, sources):
        return ["마지막 소스 변경 이후 성공한 전체 테스트 기록 없음 (./gradlew test를 포그라운드로, 파이프·--tests 없이)"]
    return []


def resolve_problems(root):
    problems = validate_gap_protocol(root)
    sources = changed_paths(root, "src")
    if sources:
        problems += validate_test_evidence(root, sources)
    if len(changed_paths(root, PRODUCTION_SRC)) >= LEDGER_THRESHOLD:
        problems += validate_ledger(root)
    return problems


def build_output(problems, already_continuing):
    summary = "; ".join(problems)
    if already_continuing:
        return {"systemMessage": f"⚠ 완료 게이트 미충족 상태로 종료됨: {summary}"}
    return {
        "decision": "block",
        "reason": f"완료 주장에 증거가 부족함: {summary}. 충족시키거나, 못 하면 완료 주장을 철회하고 미충족 항목을 보고할 것.",
    }


def main():
    data = json.load(sys.stdin)
    if not CLAIM.search(data.get("last_assistant_message", "")):
        return
    root = repo_root(data.get("cwd", "."))
    if not root:
        return
    problems = resolve_problems(root)
    if problems:
        print(json.dumps(build_output(problems, data.get("stop_hook_active", False)), ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:  # surface gate crashes instead of silently passing
        print(json.dumps({"systemMessage": f"⚠ completion-gate 오류로 검사 생략: {error}"}, ensure_ascii=False))
