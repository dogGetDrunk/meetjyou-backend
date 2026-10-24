# AI 에이전트 작업 검증 프로세스 — meetjyou 적용분

> **2026-10-13 vgate 적용 해제** — `.claude/vgate.json`·`.claude/vgate-guard.py`·Stop guard hook 제거. 원장·완료 게이트·PR 전 검증자·갭 프로토콜은 더 이상 hook으로 강제되지 않음.
> 해제 전 설정: 이 커밋 직전의 `.claude/vgate.json`. plugin 이관 전 버전: 태그 `agent-process-inrepo-final`.
> 남은 것: 아래 L2(빌드에서 강제)와 갭 기록(이력).

## L2 규칙 강제 (프로젝트 몫)

| 장치 | 내용 |
|---|---|
| `.claude/settings.json` permissions | 자격증명 조회 명령 `deny` (G5). PR 생성·머지 `ask`(G6)는 2026-10-24 제거 — CLAUDE.md Git Workflow의 PR 생성·머지 무확인 정책과 상충 |
| detekt | `config/detekt/detekt.yml`, 기존 위반은 `baseline.xml`에 동결 — 추가 금지 |
| Konsist `ConventionTest` | `!!`, `requireNotNull`, 컨트롤러의 DTO `.of()`, 서비스의 `SecurityUtil` 직접 호출 금지 |
| skip 테스트 차단 | skip된 테스트가 1개라도 있으면 빌드 실패 (G10) |

- 모두 `./gradlew build`에서 강제

## 한계 (프로젝트 고유)

- **프로젝트 `ask`는 로컬 `allow`로 못 덮음**: 권한 규칙은 범위 무관 deny → ask → allow 순 판정. 개인 환경에서만 확인을 끄는 방법이 없어, 커밋·PR 정책은 CLAUDE.md Git Workflow가 기준

## 갭 기록

- [gap-log.md](gap-log.md) — 이 레포의 도메인·인프라 갭
- 과거 항목의 `vgate@<sha>` 인용은 vgate 개발 레포(`~/orca/projects/vgate`) 커밋
