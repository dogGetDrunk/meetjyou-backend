# 결과

> 태스크 종료 시 메인 설계 에이전트가 작성. 포트폴리오·회고 재료.

## 결과 요약

- 모집글 수정 시 연결 파티의 정원·일정·장소·모집 상태를 함께 반영 (이슈 #223)
- 정원 < 참여 인원 축소는 409 `PARTY_CAPACITY_BELOW_JOINED`로 거부
- 락 순서를 party → post로 통일(`updatePost`·`updatePostStatus`·`deletePlan`), 파티 락 이후 Post 최신 읽기(`PostRowLock`)
- 엔드포인트·DTO·DB 스키마·Flyway 변경 없음
- 구현 티켓 2개 모두 엔진 blocked → 사람 결정으로 수동 판정·머지 (엔진 결함 6건 도출)

## 머지된 PR

| 티켓 | PR | 등급 | 구현 / 리뷰 모델 | 라운드 |
|---|---|---|---|---|
| T-01 동기화 + 락 순서 정렬 | #244 (5714657) | high | claude-opus-5-5 / gpt-6.1-sol high, 판정 claude-opus-5-5 수동 | 리뷰 1 (approve, nit 2) |
| T-02 MySQL 최신 읽기·직렬화 검증 | — (T-01로 충족) | high | 미실행 | — |
| T-03 수용 테스트 보조 단언 보강 | #245 (1928e0e) | low | 테스트 gpt-6.1-terra + 복사·수정 claude-opus-5-5 수동 / gpt-6.1-sol high 수동 | 리뷰 2 (R1 blocking → approve) |

- 수용 테스트 작성: gpt-6.1-terra (T-01분 3회, T-03분 2회 실행)
- spec 비평: gpt-6.1-sol, 2라운드 (blocking 2·nit 2 전부 수용)
- 최종 검증: `./gradlew build` 596 tests 0 fail/0 skip, `./gradlew dockerTest` 47 tests 0 fail, CI build-and-test SUCCESS (#244 a68fb18, #245 6200967)
- 정책 이탈: T-03은 low 등급이라 엔진 배정은 haiku 구현 / luna 리뷰였으나 수동 경로로 Opus 복사·sol 리뷰. terra·sol 모두 OpenAI라 테스트 작성자 기준 반대 진영 리뷰 미충족

## 핵심 결정과 기각한 대안

- D1 모집글 = 원본, `updatePost`/`updatePostStatus`가 파티 필드 갱신
  - 기각: 파티 생성 후 모집글 정원·일정·장소 수정 금지 — 수정 UI 경로가 모집글뿐이라 기능 축소
- D2 정원 < 현재 `joined` → 409
  - 근거: "현재 상태로 거부 → 409, 형식 오류 → 400" 규칙, 값 자체(1~10)는 유효 입력
- D3 모집 완료 후 기존 PENDING 승인 허용 (팀 논의 필요, 노션 태스크에 기록)
  - 기각: `approveJoinRequest`에 모집 상태 검사 추가 — 보드 범위 밖, 정원 초과는 기존 capacity 검사로 방어
- D4 모집 재개 시 파티도 `OPEN` 대칭 복귀
- C1-1 파티 락 이후 Post stale snapshot → `PostRowLock`(PESSIMISTIC_WRITE + locked refresh) 도입
  - 기각: 락 없는 `refresh` — 같은 RR snapshot 재조회, party.md 금지 패턴
- C1-2 새 순서로 `deletePlan ↔ updatePost` 교착 재발 → `deletePlan` detach를 party → post로 정렬
  - 기각: 409 수용 목록 등재 — #201로 제거한 교착 부활
- plan 전환 편집 ↔ `deletePlan` 실제 InnoDB 교착(plan_id 보조 인덱스) → accepted deadlock 409
  - 근거: CLAUDE.md "lock-order 교착은 409 사후 처리, 구조 예방은 알림 관측 후"
  - 기각: `deletePlan`이 연결 party PK 선잠금 — spec 밖 락 추가, 후속 후보로 party.md 기록
- 수용 테스트 반입 = 태스크 폴더 `tests/code` 경유 바이트 복사 (사람 결정)
  - 기각: terra 생략(세션 분리 위반), 엔진 개선 선행(범위 확대), main 선반영(빨간 CI)
- 엔진 blocked 후 수동 완료 (사람 결정) — `coordinator.json`·`claims.json` 미수정, 새 Run 우회 안 함
- 전체 결정 표: `decisions.md`

## spec 대비 검증

- REQ-001~007 전부 충족 (`logs/reports/T-01-judge.json` requirements_met)
- AT 보조 단언 공백(AT-001-3·002-2·002-3·004-1·007-2)은 T-03에서 해소
- 회귀 주입 확인
  - 정원 검증을 plan 변경 뒤로 이동 → H2 AT-002 보강 단언 red
  - `deletePlan` marker 삭제 제거 → AT-007-2 2건 red (단, FK RESTRICT 위반 경유 — 새 `existsById` 단언 단독 증명은 아님)
- 리뷰 R1: marker 삭제 단언이 plan UUID 경유라 plan 삭제 후 항상 빈 결과(거짓 green) → ID 기준 `existsById`로 수정

## 남은 후속 작업

- 프론트엔드 공유: 모집글 수정 409 `PARTY_CAPACITY_BELOW_JOINED`
- D3(마감 후 PENDING 승인 허용) 팀 논의
- accepted deadlock(plan 전환 편집 ↔ `deletePlan`) 알림 빈도 관측 → 발생 시 party PK 선잠금 검토
- 엔진 결함 6건 수정 (blocked 해제·claim 해제·gate 결과 전달·이슈 링크 전달·ID 할당·입력 경로 첨부)
