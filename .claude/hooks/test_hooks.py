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
CHALLENGES_BEFORE_TRIAGE = 8
results = []

# Virtual clock for file mtimes. Scenarios depend on ordering ("source edited after the test
# marker"), which sleeping past the filesystem's mtime granularity used to guarantee at ~1s per
# write. Instead, test-written files and markers get explicit, strictly increasing mtimes in the
# past; anything git itself touches (checkout, index) carries the real current time and so still
# counts as newer than every virtual timestamp.
VIRTUAL_CLOCK_START = time.time() - 86400
VIRTUAL_TICK_SECONDS = 2
virtual_now = VIRTUAL_CLOCK_START


def tick():
    global virtual_now
    virtual_now += VIRTUAL_TICK_SECONDS
    return virtual_now


def stamp(path):
    moment = tick()
    os.utime(path, (moment, moment))


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
    full = os.path.join(repo, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "a" if append else "w", encoding="utf-8") as f:
        f.write(text)
    stamp(full)


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
    # A marker the hook just wrote carries the real time; move it onto the virtual clock. Only
    # markers newer than virtual_now qualify — older ones are already virtual, and restamping them
    # would let a run the hook ignored (failed, partial, piped) look like a fresh full test.
    work = os.path.join(repo, ".claude", "work")
    for name in os.listdir(work) if os.path.isdir(work) else []:
        marker = os.path.join(work, name)
        if name.startswith("last-full-test-") and os.path.getmtime(marker) > virtual_now:
            stamp(marker)


VERIFIER_TABLE = "| ID | 판정 | 근거 | 반례 |\n|---|---|---|---|\n| R1 | 충족 | A.kt:1 | — |"


def verifier_done(repo, agent_type="requirement-verifier", message=VERIFIER_TABLE):
    # The gate compares diff fingerprints, not mtimes, so no virtual-clock restamping is needed.
    run("record-verifier-run.py", {"cwd": repo, "agent_id": f"id-{tick()}", "agent_type": agent_type,
                                   "last_assistant_message": message})


def verifier_table_header():
    """The verdict-table header line exactly as .claude/agents/requirement-verifier.md prescribes."""
    agent = os.path.join(os.path.dirname(HOOKS), "agents", "requirement-verifier.md")
    with open(agent, encoding="utf-8") as f:
        return next(line.strip() for line in f if line.startswith("| ID |"))


def pr(repo, command="gh pr create --fill"):
    return run("pr-gate.py", {"cwd": repo, "tool_name": "Bash", "tool_input": {"command": command}})


def denied(out, *fragments):
    decision = out.get("hookSpecificOutput", {})
    return decision.get("permissionDecision") == "deny" and all(
        f in decision.get("permissionDecisionReason", "") for f in fragments)


def triage(repo, verdicts):
    write(repo, ".claude/work/gap-triage-feat-x.md", verdicts, append=False)


def ledger(repo, rows):
    write(repo, ".claude/work/ledger-feat-x.md", f"| ID | 상태 |\n|---|---|\n{rows}", append=False)


def hook_commands():
    """The command strings Claude Code actually runs, read from .claude/settings.json."""
    settings = os.path.join(os.path.dirname(HOOKS), "settings.json")
    with open(settings, encoding="utf-8") as f:
        events = json.load(f)["hooks"]
    return {
        hook["command"]
        for entries in events.values() for entry in entries for hook in entry["hooks"]
    }


def run_command(command, cwd, project_dir, payload):
    """Run a hook command the way the harness does: through a shell, with CLAUDE_PROJECT_DIR set."""
    environment = dict(os.environ, CLAUDE_PROJECT_DIR=project_dir)
    return subprocess.run(command, shell=True, cwd=cwd, env=environment,
                          input=json.dumps(payload), capture_output=True, text=True)


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
    gradle(repo, "./gradlew test", stdout="BUILD FAILED")
    check("failed run does not refresh a stale marker", blocked(stop(repo, "result: 완료"), "전체 테스트"))
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


def pending_lines(repo):
    path = os.path.join(repo, ".claude", "work", "gap-pending-feat-x.jsonl")
    if not os.path.exists(path):
        return 0
    with open(path, encoding="utf-8") as f:
        return len([line for line in f if line.strip()])


def scenarios_subagent_report_dedupe():
    """One verifier run reaches the hooks up to three times (hand-back turn, SubagentHandback,
    SubagentStop); it must still count as a single challenge (issue #140)."""
    repo = new_repo()
    handback_turn = ('<agent-message from="requirement-verifier">\n[Subagent hand-back]\n'
                     "| R2 | 미충족 | — | 테스트 누락 |")
    check("subagent hand-back turn -> not recorded as a user challenge",
          prompt(repo, handback_turn) == {} and pending_lines(repo) == 0)
    run("record-verifier-gaps.py", {"cwd": repo, "agent_id": "a1", "tool_input": {"message": "| R2 | 부분 | x | y |"}})
    run("record-verifier-gaps.py", {"cwd": repo, "agent_id": "a1", "last_assistant_message": "요약: R2 미충족"})
    check("same agent's hand-back + final summary -> one record", pending_lines(repo) == 1, str(pending_lines(repo)))
    run("record-verifier-gaps.py", {"cwd": repo, "agent_id": "a2", "last_assistant_message": "요약: R2 미충족"})
    check("re-verification by another agent run -> recorded separately", pending_lines(repo) == 2,
          str(pending_lines(repo)))
    check("real user challenge still detected after a hand-back",
          "gap-protocol" in json.dumps(prompt(repo, "진짜 끝이야?")))
    # Real verdict tables share a long identical prefix (header + early rows) across runs; the
    # stored excerpt is truncated, so a new gap found on re-verification must not be merged by text.
    shared = VERIFIER_TABLE + "\n| R2 | 충족 | B.kt:1 | — |" * 20
    run("record-verifier-gaps.py", {"cwd": repo, "agent_id": "b1", "last_assistant_message": f"{shared}\n| R9 | 미충족 | — | x |"})
    run("record-verifier-gaps.py", {"cwd": repo, "agent_id": "b2", "last_assistant_message": f"{shared}\n| R10 | 미충족 | — | y |"})
    # 2 verifier runs + 1 user challenge so far, then b1 and b2.
    check("re-verification sharing a long table prefix -> still recorded separately", pending_lines(repo) == 5,
          str(pending_lines(repo)))
    shutil.rmtree(repo)


def scenarios_pr_gate():
    """A PR from a ledger branch needs a requirement-verifier run newer than every change (issue #141)."""
    repo = new_repo()
    check("no ledger -> PR not gated", pr(repo) == {})
    ledger(repo, "| R1 | 🟡 |\n")
    check("ledger, never verified -> PR denied", denied(pr(repo), "검증 기록 없음"))
    check("other gh pr commands -> not gated", pr(repo, "gh pr view 12") == {} and pr(repo, "gh pr list") == {})
    verifier_done(repo, agent_type="Explore")
    check("another subagent type is not a verification", denied(pr(repo), "검증 기록 없음"))
    verifier_done(repo, message="검증 중단: 컨텍스트 부족")
    check("verifier run without a verdict table is not a verification", denied(pr(repo), "검증 기록 없음"))
    # Real runs often hand the table back via SubagentHandback and end with a one-line message.
    run("record-verifier-run.py", {"cwd": repo, "agent_id": "hb-1", "agent_type": "requirement-verifier",
                                   "tool_input": {"message": VERIFIER_TABLE}})
    run("record-verifier-run.py", {"cwd": repo, "agent_id": "hb-1", "agent_type": "requirement-verifier",
                                   "last_assistant_message": "Final report delivered to the calling agent."})
    check("table delivered only via hand-back -> counts as a verification", pr(repo) == {}, str(pr(repo)))
    write(repo, "src/main/A.kt", "change\n")
    verifier_done(repo)
    check("verified after the last change -> PR allowed", pr(repo) == {})
    sh(repo, "add", "-A")
    sh(repo, "commit", "-qm", "work")
    check("commit after verification -> still allowed", pr(repo) == {})
    write(repo, "docs/agent-process/gap-log.md", gap_entry(9, "이슈 #141"))
    check("docs-only edit after verification -> PR denied", denied(pr(repo), "검증 이후"))
    check("compound command -> PR denied",
          denied(pr(repo, "git push -u origin HEAD && gh pr create --title x --body y"), "검증 이후"))
    verifier_done(repo)
    check("re-verified -> PR allowed", pr(repo) == {})
    check("'CI 대기' verdict is not recorded as a gap",
          handback(repo, "| R3 | CI 대기 | ci.yml:36 | PR 후 CI 로그 필요 |") == {})
    # The header the verifier is told to print lists every verdict name, "미충족" included. Read it
    # from the agent definition so a reworded header is tested as the verifier will print it.
    full_report = (f"{verifier_table_header()}\n"
                   "|---|---|---|---|\n| R1 | 충족 | A.kt:1 | — |\n| R2 | CI 대기 | ci.yml:36 | PR 후 |")
    check("clean report in the verifier's real format -> not recorded as a gap", handback(repo, full_report) == {})
    sh(repo, "rm", "-q", "src/main/B.kt")
    verifier_done(repo)
    sh(repo, "add", "-A")
    sh(repo, "commit", "-qm", "delete B")
    check("branch with a deletion: verify -> commit -> PR allowed", pr(repo) == {}, str(pr(repo)))
    write(repo, "src/main/New.kt", "n\n")
    verifier_done(repo)
    os.rename(os.path.join(repo, "src/main/New.kt"), os.path.join(repo, "src/main/Renamed.kt"))
    check("renaming a new (untracked) file after verification -> PR denied", denied(pr(repo), "검증 이후"))
    verifier_done(repo)
    sh(repo, "add", "-A")
    sh(repo, "commit", "-qm", "add new file")
    check("new file: verify -> add + commit -> PR allowed", pr(repo) == {}, str(pr(repo)))
    write(repo, "docs/메모.md", "비ASCII 경로\n")
    verifier_done(repo)
    check("non-ASCII untracked path -> verification recorded and PR allowed", pr(repo) == {}, str(pr(repo)))
    shutil.rmtree(repo)


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
    # feat/x's own marker goes stale here; only feat/y's later full test could "rescue" it.
    write(repo, "src/main/A.kt", "edited after feat/x's last full test\n")
    edited_at = virtual_now
    sh(repo, "add", "-A")
    sh(repo, "commit", "-qm", "work")
    sh(repo, "checkout", "-qb", "feat/y", "main")
    check("gap state of another branch does not block", stop(repo, "result: 완료") == {})
    write(repo, "src/main/Untracked.kt", "u\n")
    gradle(repo, "./gradlew test")
    sh(repo, "checkout", "-q", "feat/x")
    # checkout rewrote feat/x's files with the real current time, which alone would block; put
    # them back before feat/y's test so that only the marker's branch decides the outcome.
    for path in ("src/main/A.kt", "src/main/B.kt"):
        os.utime(os.path.join(repo, path), (edited_at, edited_at))
    check("test marker from another branch does not count",
          blocked(stop(repo, "result: 완료"), "전체 테스트"))


def scenarios_post_merge_base():
    """A completion claim made after this branch's own PR already merged into BASE_REF
    must not degenerate merge-base(HEAD, BASE_REF) to HEAD itself (see gap-log G17)."""
    repo = new_repo()
    triage(repo, "- 갭: 영향 분석 누락 → L1\n")
    write(repo, "deploy.yml", "check: true\n")
    write(repo, "docs/agent-process/gap-log.md", gap_entry(2, "`deploy.yml` 체크 추가"))
    sh(repo, "add", "-A")
    sh(repo, "commit", "-qm", "work")
    check("gap entry citing a changed file -> silent, before merge", stop(repo, "result: 완료") == {})
    sh(repo, "checkout", "-q", "main")
    sh(repo, "merge", "-q", "--no-ff", "-m", "merge feat/x", "feat/x")
    sh(repo, "update-ref", "refs/remotes/origin/main", "main")
    sh(repo, "checkout", "-q", "feat/x")
    out = stop(repo, "result: 완료")
    check("gap entry still valid after BASE_REF absorbs this branch's merge commit",
          out == {}, str(out))
    shutil.rmtree(repo)


def scenarios_hook_commands():
    """A worktree session's CLAUDE_PROJECT_DIR points at the main checkout, not the worktree."""
    repo = new_repo()
    shutil.copytree(HOOKS, os.path.join(repo, ".claude", "hooks"), dirs_exist_ok=True)
    elsewhere = tempfile.mkdtemp()
    payload = {"cwd": repo, "tool_input": {"command": "./gradlew test"},
               "tool_response": {"stdout": "BUILD SUCCESSFUL", "stderr": "", "interrupted": False, "isImage": False}}
    failures = [c for c in hook_commands() if run_command(c, repo, elsewhere, payload).returncode != 0]
    check("hook commands run from a worktree with a foreign CLAUDE_PROJECT_DIR", not failures,
          f"{len(failures)} command(s) failed")
    marker = os.path.join(repo, ".claude", "work", "last-full-test-feat-x.json")
    check("hook command resolves scripts in the current repo", os.path.exists(marker))
    bare = new_repo()
    failures = [c for c in hook_commands() if run_command(c, bare, elsewhere, payload).returncode != 0]
    check("hook commands exit quietly where the scripts are absent", not failures)
    shutil.rmtree(repo)
    shutil.rmtree(bare)


def main():
    repo = new_repo()
    scenarios_claim_detection(repo)
    scenarios_test_evidence(repo)
    scenarios_ledger(repo)
    scenarios_gap_detection(repo)
    scenarios_gap_protocol(repo)
    scenarios_branch_isolation(repo)
    scenarios_post_merge_base()
    scenarios_subagent_report_dedupe()
    scenarios_pr_gate()
    scenarios_hook_commands()
    check("non-git cwd -> silent", stop(tempfile.mkdtemp(), "result: 완료") == {})
    shutil.rmtree(repo)
    failed = [name for name, ok in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
