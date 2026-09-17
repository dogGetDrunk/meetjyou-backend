"""Scenario tests for the agent-process hooks (see docs/agent-process/README.md).

Usage: python3 .claude/hooks/test_hooks.py [hooks_dir]  — defaults to this file's directory.
Each scenario runs the real hook scripts against a throwaway git repository.
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time

HOOKS = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
MTIME_STEP_SECONDS = 1.1
CHALLENGES_BEFORE_TRIAGE = 8
results = []


def run(script, payload):
    proc = subprocess.run(["python3", os.path.join(HOOKS, script)], input=json.dumps(payload),
                          capture_output=True, text=True)
    return json.loads(proc.stdout) if proc.stdout.strip() else {}


def check(name, condition, detail=""):
    results.append((name, condition))
    print(f"{'PASS' if condition else 'FAIL'}  {name}  {detail if not condition else ''}")


def sh(repo, *args):
    subprocess.run(["git", "-C", repo, *args], check=True, capture_output=True)


def write(repo, path, text, append=True):
    time.sleep(MTIME_STEP_SECONDS)
    full = os.path.join(repo, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "a" if append else "w", encoding="utf-8") as f:
        f.write(text)


def stop(repo, message, active=False):
    return run("completion-gate.py", {"cwd": repo, "last_assistant_message": message, "stop_hook_active": active})


def blocked(out, *fragments):
    return out.get("decision") == "block" and all(f in out["reason"] for f in fragments)


def prompt(repo, text):
    return run("gap-detector.py", {"cwd": repo, "prompt": text})


def handback(repo, report):
    return run("record-verifier-gaps.py", {"cwd": repo, "tool_input": {"message": report}})


def gradle(repo, command, stdout="BUILD SUCCESSFUL in 1m"):
    response = {"stdout": stdout, "stderr": "", "interrupted": False, "isImage": False}
    run("record-full-test.py", {"cwd": repo, "tool_input": {"command": command}, "tool_response": response})


def triage(repo, verdicts):
    write(repo, ".claude/work/gap-triage-feat-x.md", verdicts, append=False)


def ledger(repo, rows):
    write(repo, ".claude/work/ledger-feat-x.md", f"| ID | 상태 |\n|---|---|\n{rows}", append=False)


def new_repo():
    repo = tempfile.mkdtemp()
    sh(repo, "init", "-q", "-b", "main")
    sh(repo, "config", "user.email", "t@t")
    sh(repo, "config", "user.name", "t")
    write(repo, "src/main/A.kt", "a\n")
    write(repo, "src/main/B.kt", "b\n")
    write(repo, "docs/agent-process/gap-log.md", "# log\n\n### G1. old\n- 놓친 층: L1\n- 추가한 장치: 이슈 #1\n")
    write(repo, ".gitignore", ".claude/work/\n")
    sh(repo, "add", "-A")
    sh(repo, "commit", "-qm", "init")
    sh(repo, "update-ref", "refs/remotes/origin/main", "HEAD")
    sh(repo, "checkout", "-qb", "feat/x")
    return repo


def gap_entry(number, mechanism, layer="- 놓친 층: L1\n"):
    return f"\n### G{number}. gap\n{layer}- 추가한 장치: {mechanism}\n"


def scenarios_claim_detection(repo):
    check("no claim -> silent", stop(repo, "조사 결과입니다") == {})
    check("question is not a claim", stop(repo, "이 부분 확인해 드릴까요?") == {})
    check("claim without src change -> silent", stop(repo, "result: 끝") == {})
    write(repo, "src/main/A.kt", "x\n")
    claims = ["result: 수정", "수정했습니다", "구현했습니다", "추가했습니다", "반영했습니다", "적용했습니다",
              "테스트가 통과했습니다", "작업 완료", "완료됨", "335개 모두 성공", "Done."]
    missed = [c for c in claims if not blocked(stop(repo, c), "전체 테스트")]
    check("every completion phrasing triggers the gate", not missed, f"missed: {missed}")


def scenarios_test_evidence(repo):
    gradle(repo, "./gradlew test --tests Foo")
    gradle(repo, "./gradlew test 2>&1 | tail -5")
    gradle(repo, "./gradlew test", stdout="BUILD FAILED")
    gradle(repo, "./gradlew --dry-run test")
    gradle(repo, "./gradlew test", stdout="")
    check("partial/piped/failed/dry-run/background runs -> no marker",
          blocked(stop(repo, "result: 완료했습니다"), "전체 테스트"))
    gradle(repo, "./gradlew clean test --no-daemon")
    check("one production file + fresh full test -> silent (no ledger needed)", stop(repo, "result: 완료") == {})
    write(repo, "src/main/A.kt", "y\n")
    check("source edited after test -> block", blocked(stop(repo, "result: 완료"), "전체 테스트"))
    out = stop(repo, "result: 완료", active=True)
    check("already continuing -> systemMessage only", "systemMessage" in out and "decision" not in out)
    gradle(repo, "./gradlew build")
    sh(repo, "rm", "-q", "src/main/B.kt")
    check("deleting a production file still needs a test run", blocked(stop(repo, "result: 완료"), "전체 테스트"))
    sh(repo, "restore", "--source=HEAD", "--staged", "--worktree", "src/main/B.kt")
    gradle(repo, "./gradlew build")


def scenarios_ledger(repo):
    write(repo, "src/main/B.kt", "y\n")
    gradle(repo, "./gradlew build")
    check("two production files, no ledger -> block", blocked(stop(repo, "result: 완료"), "원장 없음"))
    ledger(repo, "")
    check("empty ledger -> block", blocked(stop(repo, "result: 완료"), "요구사항 행이 없음"))
    ledger(repo, "| R1 | ✅ |\n| R2 | 🟡 |\n")
    check("pending ledger row -> block", blocked(stop(repo, "result: 완료"), "미검증 행 1개"))
    ledger(repo, "| R1 | ✅ |\n| R2 | ⏸ |\n")
    check("ledger without ⬜/🟡 -> silent", stop(repo, "result: 완료") == {})


def scenarios_gap_detection(repo):
    prompt(repo, "이 PR 리뷰 요청해줘")
    check("neutral prompt -> no record", not os.path.exists(f"{repo}/.claude/work/gap-pending-feat-x.jsonl"))
    for text in ["수정할 부분 저게 진짜 끝이야? 너무 적은데", "enum에 없는 값이 왜 통과하는거야", "이 부분 사실이야?",
                 "재발하지 않을지 다시 확인해봐", "왜 그걸 그냥 넘겼어", "이거 동작 안 하는데?"]:
        check(f"challenge detected: {text}", "gap-protocol" in json.dumps(prompt(repo, text)))
    check("clean verifier report -> not recorded", handback(repo, "| R1 | 충족 | 근거 | — |") == {})
    check("verifier gap report -> recorded", "systemMessage" in handback(repo, "| R5 | 부분 | 근거 | 반례 |"))
    check("same report twice -> recorded once", handback(repo, "| R5 | 부분 | 근거 | 반례 |") == {})
    check("subagent final message path -> recorded",
          "systemMessage" in run("record-verifier-gaps.py",
                                 {"cwd": repo, "last_assistant_message": "R7 미충족: 층 분류 미검사"}))


def scenarios_gap_protocol(repo):
    check("challenges without verdicts -> block",
          blocked(stop(repo, "result: 완료"), f"{CHALLENGES_BEFORE_TRIAGE}건 중 판정은 0건"))
    triage(repo, "")
    check("touching triage file is not a verdict", blocked(stop(repo, "result: 완료"), "판정은 0건"))
    others = "- 갭 아님: 질문\n" * (CHALLENGES_BEFORE_TRIAGE - 1)
    triage(repo, others)
    check("one verdict short -> block", blocked(stop(repo, "result: 완료"), f"판정은 {CHALLENGES_BEFORE_TRIAGE - 1}건"))
    triage(repo, f"{others}- 갭: 영향 분석 누락\n")
    check("real gap without layer -> block", blocked(stop(repo, "result: 완료"), "놓친 층 표기 없음"))
    triage(repo, f"{others}- 갭: 영향 분석 누락 → L1\n")
    check("real gap without new gap-log entry -> block", blocked(stop(repo, "result: 완료"), "유효한 새 항목은 0건"))
    write(repo, "docs/agent-process/gap-log.md", gap_entry(2, ""))
    check("entry with empty mechanism -> block", blocked(stop(repo, "result: 완료"), "G2"))
    write(repo, "docs/agent-process/gap-log.md", gap_entry(3, "`src/main/Unchanged.kt`"))
    check("mechanism pointing at an unchanged file -> block", blocked(stop(repo, "result: 완료"), "G3"))
    write(repo, "docs/agent-process/gap-log.md", gap_entry(4, "`docs/agent-process/gap-log.md`"))
    check("gap-log itself is not a mechanism -> block", blocked(stop(repo, "result: 완료"), "G4"))
    write(repo, "docs/agent-process/gap-log.md", gap_entry(5, "`src/main/A.kt` 체크 추가", layer=""))
    check("entry without the missed layer -> block", blocked(stop(repo, "result: 완료"), "G5"))
    write(repo, "docs/agent-process/gap-log.md", gap_entry(6, "PR #130 참고"))
    check("bare issue-like number is not a mechanism -> block", blocked(stop(repo, "result: 완료"), "G6"))
    write(repo, "docs/agent-process/gap-log.md", gap_entry(7, "`src/main/A.kt` 체크 추가"))
    check("entry citing a changed file -> silent", stop(repo, "result: 완료") == {}, str(stop(repo, "result: 완료")))
    prompt(repo, "빠진 거 없어?")
    triage(repo, f"{others}- 갭: 영향 분석 누락 → L1\n- 갭: 또 누락 → L4\n")
    check("earlier gap-log entry does not cover a later gap -> block", blocked(stop(repo, "result: 완료"), "'갭' 판정 2건"))
    write(repo, "docs/agent-process/gap-log.md", gap_entry(8, "이슈 #131"))
    check("entry citing an issue -> silent", stop(repo, "result: 완료") == {})


def scenarios_branch_isolation(repo):
    sh(repo, "add", "-A")
    sh(repo, "commit", "-qm", "work")
    sh(repo, "checkout", "-qb", "feat/y", "main")
    check("gap state of another branch does not block", stop(repo, "result: 완료") == {})
    write(repo, "src/main/Untracked.kt", "u\n")
    gradle(repo, "./gradlew test")
    sh(repo, "checkout", "-q", "feat/x")
    check("test marker from another branch does not count",
          blocked(stop(repo, "result: 완료"), "전체 테스트"))


def main():
    repo = new_repo()
    scenarios_claim_detection(repo)
    scenarios_test_evidence(repo)
    scenarios_ledger(repo)
    scenarios_gap_detection(repo)
    scenarios_gap_protocol(repo)
    scenarios_branch_isolation(repo)
    check("non-git cwd -> silent", stop(tempfile.mkdtemp(), "result: 완료") == {})
    shutil.rmtree(repo)
    failed = [name for name, ok in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
