# AI 에이전트 작업 검증 프로세스

> 목적: 에이전트(Claude Code)가 사용자 요구사항을 빠짐없이 구현했다는 것을 **사용자가 매번 의심하지 않고도** 신뢰할 수 있게 하는 것.
> 원칙: 완료 주장은 증거 없이는 통과하지 못함 / 규칙은 문장이 아니라 장치로 강제 / 작성자와 검증자 분리.
> 도입 경위와 근거 사건: [gap-log.md](gap-log.md) G1~G9.

## 층 구조

| 층 | 역할 | 장치 |
|---|---|---|
| L1 요구사항 원장 | 구현 전에 요구사항·수용 기준·영향 분석을 고정 | `.claude/templates/ledger.md` → `.claude/work/ledger-<branch>.md` |
| L2 규칙 강제 | 항상 작동하는 권한·정적 분석 | `.claude/settings.json` permissions (PR 생성·머지 ask, 자격증명 deny), detekt·Konsist `ConventionTest`·skip 테스트 차단(`worktree-static-analysis` 브랜치에서 도입) |
| L3 완료 게이트 | 증거 없는 완료 주장 차단 | `.claude/hooks/completion-gate.py`(Stop), `record-full-test.py`(PostToolUse) |
| L4 독립 검증 | 새 컨텍스트에서 원장 대비 반증 시도, PR 전 실행 강제 | `.claude/agents/requirement-verifier.md`, `.claude/hooks/record-verifier-run.py`(PostToolUse `SubagentHandback` + SubagentStop), `pr-gate.py`(PreToolUse) |
| L5 보고 계약 | 정직한 보고 형식, 추정 표기 | CLAUDE.md "Verification Workflow" |
| L6 갭 회고 | 발견된 빈틈 → 층 분류 → 장치 추가 | `.claude/hooks/gap-detector.py`(사용자 발화), `record-verifier-gaps.py`(서브에이전트 보고) + 게이트 + `gap-log.md` |

## 작업 흐름

1. **원장 작성 (L1)** — 프로덕션 파일 2개 이상 변경(게이트가 강제) 또는 설계에 영향을 주는 작업
   - 템플릿을 `.claude/work/ledger-<branch의 / 를 - 로>.md`로 복사
   - 행 출처: `R` 명시 요구(원문 인용) / `C` 컨벤션 / `I` 영향 분석
   - 버그 수정: 수정 전 실패하는 테스트 출력(red)을 증거로 기록
   - 사용자에게 원장 요약을 보여주고 승인받은 뒤 구현
2. **구현** — 행 상태를 ⬜ → 🟡(구현) → ✅(증거 확보)로 갱신
3. **전체 테스트** — `./gradlew test`(또는 `build`)를 포그라운드로, 파이프·`--tests`·`--dry-run` 없이 실행 (그래야 마커가 기록됨. 백그라운드 실행은 출력이 없어 기록 안 됨)
4. **독립 검증 (L4)** — `requirement-verifier` 서브에이전트에 원장 경로 전달, 판정 표를 PR 본문에 첨부
   - **PR 생성 전 필수** — 아래 "PR 게이트" 참조. 검증 후 파일을 고치면(문서·gap-log 포함) 재검증
   - CI 로그가 증거인 행: PR 전엔 🟡 유지, 검증자는 `CI 대기`로 판정(갭 아님) → PR의 CI 결과로 ✅ 전환
5. **완료 보고 (L5)** — `result:` 줄로 끝맺음 + 원장 표 + 증거 + "확인하지 못한 것". 게이트(L3)가 형식·최신성 확인

## 완료 게이트 동작 (L3)

- 발동 조건: 마지막 메시지의 완료 주장 (`result:` 줄, "완료했/됐/되었", "수정했습니다", "고쳤습니다", "통과했", "전체 통과", "done" 등)
- 항상 검사: 갭 프로토콜 (아래)
- `src/`가 `origin/main` 대비 변경됐을 때: 마지막 소스 변경 이후 성공한 전체 테스트 마커 존재
- 프로덕션 파일(`src/main`) 2개 이상 변경됐을 때: 원장 존재, ⬜/🟡 행 없음
- 미충족 시 `decision: block`으로 1회 되돌림. 재진입(`stop_hook_active`) 시엔 반복하지 않고 사용자 화면에 경고(`systemMessage`)
- hook 스크립트 자체가 오류 나면 조용히 통과하지 않고 사용자 화면에 경고
- 한계: 형식·최신성만 확인. ✅ 표시의 정직성은 L4와 사용자의 원장 훑어보기로 확인. 한 파일짜리 설계 변경은 원장 강제 대상이 아님(판단 규칙으로만 적용)

## PR 게이트 (L4)

- 기록: `record-verifier-run.py`(PostToolUse `SubagentHandback` + SubagentStop)가 `agent_type`이 `requirement-verifier`이고 판정 표(`| ID |`)를 낸 실행만 `.claude/work/last-verifier-<branch>.json`으로 기록 (중단된 실행은 검증으로 인정 안 함)
  - 두 경로를 모두 보는 이유: SubagentStop은 매번 발생하지만, 실제 검증 3회 중 2회는 그 마지막 메시지가 한 줄("Final report delivered…")이고 표는 hand-back에만 있었음 (G25)
  - 기록 내용: 그 시점 **작업 트리의 git 트리 해시** — 임시 index에 `add -A` 후 `write-tree` (`.gitignore` 반영, 실제 index 무변경)
- 차단: `pr-gate.py`(PreToolUse, Bash)가 명령에 `gh pr create`가 있고(복합 명령 포함) 브랜치 원장이 있을 때
  - 검증 기록 없음 → deny
  - 기록된 트리 해시 ≠ 현재 트리 해시 → deny (문서·gap-log·삭제·이름 변경 포함)
  - 파일은 미추적·staged·커밋 상태와 무관하게 같은 해시 → "검증 → add → 커밋 → PR"은 통과
  - 리베이스로 base 내용이 바뀌면 트리가 달라져 재검증
- 판정 방식 변천 (G23·G24)
  - mtime 기각: 삭제 파일의 변경 시각을 git index mtime으로 대신 → 커밋이 index를 다시 써서 삭제 포함 브랜치는 커밋만 해도 차단
  - "base 대비 diff + 미추적 파일" 해시 기각: 새 파일이 add 전엔 미추적 목록, add 후엔 diff 패치로 해시돼 커밋만 해도 차단. 비ASCII 경로는 `ls-files` 따옴표 처리로 해시 실패
- 원장 없는 브랜치는 대상 아님. 통과 후엔 기존 `ask` 권한 확인이 이어짐
- hook 오류 시 경고 후 통과(fail-open) — hook 버그 하나로 모든 PR이 막히지 않게

## 갭 프로토콜 (L6)

사용자가 요청하지 않아도 에이전트가 스스로 수행.

- **발동**
  - 자동(사용자): `gap-detector.py`가 발화의 추궁 표현("진짜 끝이야", "왜 통과", "사실이야", "누락", "다시 확인", "동작 안" 등)을 감지 → `.claude/work/gap-pending-<branch>.jsonl` 기록 + 프로토콜 주입
  - 자동(검증자): `record-verifier-gaps.py`가 서브에이전트 보고에서 "미충족 / 부분 / 근거 부족 / - 갭:"을 감지해 같은 목록에 기록
    - 한 서브에이전트 실행(`agent_id`)은 1건 — hand-back(PostToolUse)과 마지막 요약(SubagentStop)이 둘 다 와도 중복 기록 안 함. `CI 대기`는 갭 아님
    - 보고가 메인 세션에 `<agent-message …>` / `[Subagent hand-back]` 사용자 턴으로 도착해도 `gap-detector.py`는 건너뜀 (사용자 추궁으로 오인 방지)
  - 수동: 키워드에 안 걸려도 에이전트가 완료 주장 이후 자기 오류·누락을 발견했으면 동일하게 수행
- **절차**
  1. 답변하고 필요하면 수정
  2. `.claude/work/gap-triage-<branch>.md`에 추궁 1건당 판정 한 줄
     - `- 갭: <요약> → L<n>` (놓친 층)
     - `- 갭 아님: <이유>` (단순 질문·오탐)
  3. 갭이면 `gap-log.md`에 `### G<n>` 항목 추가: 사건 / 놓친 층 / 왜 놓쳤나 / **추가한 장치**
  4. 장치를 **같은 브랜치에서** 추가. 우선순위: 결정적 장치(hook·테스트·lint 규칙) > 체크리스트 항목 > 문장 규칙
     - 문장 규칙(CLAUDE.md·메모리)만 추가하는 것은 결정적 장치가 불가능한 이유를 적은 경우에만 허용
     - 장치가 이번 작업 범위를 크게 넘으면 이슈로 등록하고 gap-log에 이슈 번호 기재
- **강제 (완료 게이트, 개수 기반)**
  - 판정 줄 수 < 감지된 추궁·갭 보고 수 → 차단 (파일만 건드리는 건 판정으로 인정 안 함)
  - `- 갭:` 판정에 `→ L<n>`(놓친 층) 없으면 차단, gap-log 항목에 `- 놓친 층:` 없으면 무효
  - base 대비 새 gap-log 항목 중 유효한 것 < `- 갭:` 판정 수 → 차단 (예전 항목이 새 갭을 대신하지 못함)
  - 유효 조건: `- 추가한 장치:`가 이 브랜치에서 바뀐 파일(백틱 경로, gap-log 자체 제외) 또는 이슈 `#<n>`을 가리킴
  - 기록 파일은 브랜치별이라 다른 브랜치 작업을 막지 않음
- 장치의 **품질**(정말 재발을 막는지)은 기계로 판정하지 않음 → L4 검증자와 사용자 리뷰 대상

## 파일 위치

- 커밋 대상: `.claude/settings.json`, `.claude/hooks/`, `.claude/templates/`, `.claude/agents/`, `docs/agent-process/`
- hook 회귀 테스트: `python3 .claude/hooks/test_hooks.py` (CI에서도 실행). hook 수정 시 시나리오 추가
  - 스크립트뿐 아니라 `settings.json`의 **명령 문자열 자체**도 실행해 검증 (worktree에서 `$CLAUDE_PROJECT_DIR`가 원본 체크아웃을 가리키는 문제 — G13)
- 로컬 전용(gitignore): `.claude/work/` — worktree마다 따로
  - `ledger-<branch>.md` 원장 / `last-full-test-<branch>.json` 테스트 마커 / `last-verifier-<branch>.json` 검증자 실행 마커
  - `gap-pending-<branch>.jsonl` 감지된 추궁 / `gap-triage-<branch>.md` 판정

## 한계

- **권한 규칙은 세션 종류를 구분하지 못함**: "background job에서만 커밋·푸시 무확인"은 permission으로 표현할 수 없음. 기준은 CLAUDE.md Git Workflow(명시 요청 시에만)와 사용자의 예외 합의이고, 이 레포에 커밋되는 장치는 PR 생성·머지 ask뿐
- PR 게이트는 "검증자를 최신 diff에 대해 돌렸는가"만 검사 — 판정 결과(갭 유무)는 완료 게이트의 갭 프로토콜이 담당. CI 증거 행을 ✅로 바꾼 뒤의 재검증은 문장 규칙뿐
- PostToolUse(SubagentHandback) 입력의 `agent_type`은 공식 문서 기준 [추정], 병합 전 실측 불가 → 틀리면 검증 기록이 안 남아 "검증 기록 없음" deny가 반복됨(사실상 fail-closed). deny 메시지가 이 경우 사용자 보고를 지시
- 트리 해시는 검증이 **끝난** 시점에 계산 → 검증자가 도는 동안(백그라운드 실행) 메인 세션이 고친 내용도 검증된 것으로 취급됨. 검증 중엔 파일을 고치지 말 것
- `gh`를 절대 경로(`/opt/homebrew/bin/gh pr create`)나 `gh api …/pulls`로 호출하면 우회됨 — 실수 방지용 게이트이지 우회 방지용이 아님
- hand-back 형식 텍스트를 사용자가 그대로 붙여넣으며 추궁하면 `gap-detector.py`가 감지하지 못함 → 수동 발동 규칙으로 보완
- 원장이 요구사항을 잘못 이해하면 모든 층이 틀린 것을 검증함 → 원장 승인 단계가 사용자 개입의 핵심
- 검증자도 같은 계열 모델이라 맹점을 공유할 수 있음
- 추궁 감지는 키워드 기반이라 누락 가능 → 수동 발동 규칙으로 보완
- 설계 판단(예: 400 vs 500)은 자동화 대상이 아님 → 사용자에게 질문
