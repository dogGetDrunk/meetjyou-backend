# 결과

> 태스크 종료 시 메인 설계 에이전트가 작성. 포트폴리오·회고 재료.

## 결과 요약

- **취소** (2026-11-04, 사람 결정) — 머지된 구현 PR 없음, 이슈 #225·#228 미해결
- 취소 사유: 엔진 결함으로 구현 gate 진행 불가, 엔진 안에서 복구 경로 없음
  - 보호 테스트 hash를 최초 dispatch 때만 기록 → 설계 쪽 테스트 수정 후 재시도가 gate에서 항상 실패 (damiannlee/agent-pipeline#41)
  - `escalated` 티켓 복구 경로 부재, `unblock`은 `blocked` 전용 (#42)
- 경과: 구현 3개(T-01~03) 완료 → 보호 테스트 lint 위반으로 CI 실패 → T-01·T-02 effort max까지 재시도 → 메인 설계가 테스트 수정 → hash 불일치로 T-01·T-02 escalated, T-03 max 재시도 → 중지·취소

## 머지된 PR

| 티켓 | PR | 등급 | 구현 / 리뷰 모델 | 라운드 |
|---|---|---|---|---|
| T-01 채팅방 안 읽은 수 단일 집계 | #260 draft, 닫힘 (`7a6dcc8`) | high | claude-opus-5-5 / 리뷰 미도달 | 재시도 7회(medium→max) |
| T-02 게시글 상세 커넥션 1개 | #261 draft, 닫힘 (`c6497c7`) | high | claude-opus-5-5 / 리뷰 미도달 | 재시도 7회(medium→max) |
| T-03 브로드캐스트 일괄 조회·실패 처리·dedup | 없음 (로컬 `896a9ef`, push GitHub 500) | high | claude-opus-5-5 / 리뷰 미도달 | 재시도 6회(medium→max) |
| T-04 재발송 API | 미착수 (T-03 의존) | high | — | — |

- 브랜치·worktree 보존: `damiannlee/chat-notice-post-perf-T-01`·`-T-02`(원격), T-03은 worktree 로컬 커밋
- 수용 테스트 작성: gpt-6.1-terra, 12개 파일, 재시도 16회(high→xhigh, Codex 한도 대기 1회)
- spec 비평: 1라운드, C-r1-01~08 (수용 6·기각 2)

## 핵심 결정과 기각한 대안

- D1 브로드캐스트 = 단일 REQUIRES_NEW 트랜잭션 유지 + 실패 알림 + 재발송 API
  - 기각: 청크별 독립 트랜잭션 — 부분 발송 상태·"빠진 사람만" 재발송 로직 필요
- D2 운영 부하 테스트 = 배포 후 사람 작업 (범위 제외)
- D3 재발송 API = 이번 태스크에서 구현
- 중복 발송 방지 = 원천 row `PESSIMISTIC_WRITE` + dedup 접두 존재 확인 + 비유니크 인덱스 V43
  - 기각: `dedup_key` 유니크 인덱스 — 운영 기존 중복 row가 있으면 migration 실패로 기동 차단
- 보호 테스트 수정(설계 쪽, 단언 불변): detekt 62건, `@DirtiesContext` 제거, AT-002-3 spy 실제 호출 위임 — 상세 spec-changelog.md
- 태스크 취소 vs 엔진 우회(사람 직접 리뷰·머지) — 취소 선택, 엔진 수정 후 재착수

## spec 대비 검증

- 직접 확인(검사용 worktree, 각 구현 커밋 + 수정 보호 테스트): T-01·T-02·T-03 `./gradlew detekt test` 통과
- AT-002-3 회귀 확인: `runCatching` 제거 시 RED, 복구 시 GREEN
- T-04 보호 테스트 3개: detekt만 통과, 대상 API 부재로 컴파일 미검증
- dockerTest·CI: 미확인 (T-03 워커 자체 보고 dockerTest 53/53, 미검증)

## 남은 후속 작업

- 엔진 이슈 damiannlee/agent-pipeline#41~#44 수정 후 재착수 (설치본도 PR #22 이후 6건 미반영 → 재설치)
  - #43 check-tests 보호 테스트 lint, #44 구현 무관 실패의 effort 상향
- 재착수 시 재사용 가능: spec·티켓·수정된 보호 테스트(`807f3fd`), 구현 커밋 3개
- 노션 보드 태스크 상태 변경, worktree·브랜치 정리 — 사람 확인 후
