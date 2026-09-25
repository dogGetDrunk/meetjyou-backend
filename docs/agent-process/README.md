# AI 에이전트 작업 검증 프로세스 — meetjyou 적용분

> 프로세스 본체(원장·완료 게이트·독립 검증·갭 회고, L1·L3~L6)는 **vgate** Claude Code plugin으로 분리됨 → `~/.claude/skills/vgate/docs/README.md`.
> vgate는 완성도 기준 충족 전까지 로컬 전용 — plugin 없는 환경(리뷰어·타 머신)에선 게이트 미동작.
> 이 레포에 커밋되던 이관 전 버전(hook·검증자·템플릿): 태그 `agent-process-inrepo-final`.
> 이 문서는 이 레포 고유 부분만: 설정값, L2, 프로젝트 갭 기록.

## vgate 설정 (`.claude/vgate.json`)

- 전체 테스트: `gradle` preset — `./gradlew test|check|build`를 포그라운드로, 파이프·`--tests`·`--dry-run`·`-x` 없이
- 소스 트리 `src`, 원장 강제: `src/main` 2개 이상 변경
- 갭 기록: `docs/agent-process/gap-log.md`
- 원장 추가 체크리스트: `.claude/ledger-extra.md` (JPA 쓰기 경로, H2↔MySQL, 배포)
- 검증자 추가 체크(`verifierChecks`): N+1, `@Transactional` 누락, IDOR
- `debug: false` — 이관 직후 `true`로 harness 발동·`agent_type`(`vgate:requirement-verifier`) 실측 완료(2026-09-25) 후 끔. hook 입력 형태를 다시 볼 일이 있으면 켤 것

## L2 규칙 강제 (프로젝트 몫)

| 장치 | 내용 |
|---|---|
| `.claude/settings.json` permissions | PR 생성·머지 `ask`, 자격증명 조회 명령 `deny` (G5·G6) |
| `.claude/vgate-guard.py` (Stop) | 이 세션에서 vgate plugin hook이 한 번도 안 돌았으면(heartbeat 없음) 경고 — plugin 미설치·미로드 시 게이트가 무신호로 사라지는 것 방지 (G33) |
| detekt | `config/detekt/detekt.yml`, 기존 위반은 `baseline.xml`에 동결 — 추가 금지 |
| Konsist `ConventionTest` | `!!`, `requireNotNull`, 컨트롤러의 DTO `.of()`, 서비스의 `SecurityUtil` 직접 호출 금지 |
| skip 테스트 차단 | skip된 테스트가 1개라도 있으면 빌드 실패 (G10) |

- 모두 `./gradlew build`에서 강제

## 한계 (프로젝트 고유)

- **권한 규칙은 세션 종류를 구분하지 못함**: "background job에서만 커밋·푸시 무확인"은 permission으로 표현 불가. 기준은 CLAUDE.md Git Workflow(명시 요청 시에만)와 사용자의 예외 합의. 커밋되는 장치는 PR 생성·머지 ask뿐

## 갭 기록

- [gap-log.md](gap-log.md) — 이 레포의 도메인·인프라 갭
- vgate 자체의 갭(게이트·hook·검증자)은 vgate `docs/gap-log.md`로 이관. 이 레포에서 발견한 vgate 결함은 vgate에서 고치고 여기 항목의 장치 칸에 `vgate@<sha>`로 인용
