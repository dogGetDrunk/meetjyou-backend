---
id: T-20261102-post-party-sync-02
title: MySQL 최신 Post 읽기와 승인·정원 수정 직렬화 수용 검증
tier: high
effort_override: null
depends_on:
- T-20261102-post-party-sync-01
requirements:
- REQ-002
- REQ-004
- REQ-006
acceptance_tests:
- AT-002-2
- AT-006-1
- AT-006-2
touches:
- src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PostLockFreshReadDockerTest.kt
consumes:
- 'PostService.updatePost / updatePostStatus: party → post lock'
- 'PostRowLock.lockByUuid(uuid: UUID): Post? / PartyRowLock.lockByUuid(uuid: UUID): Party?'
- PartyService.approveJoinRequest(partyUuid, applicantUuid)
- SqlPausePoint.Point / arm / disarm
- com.dogGetDrunk.meetjyou.support.assertWaitedOnLock
- MySQL REPEATABLE READ / performance_schema.data_lock_waits
produces:
- 'PostLockFreshReadDockerTest: AT-006-1 capacity 3→10→3'
- 'PostLockFreshReadDockerTest: AT-006-2 RECRUITING→RECRUITMENT_COMPLETED→RECRUITING'
- 'PostLockFreshReadDockerTest: AT-002-2 approval commits before shrink validation'
status: pending
---

## 목표
REQ-006의 stale snapshot·영속성 컨텍스트 재사용 회귀 및 REQ-002/004의 승인 후 최신 joined 검증을 MySQL에서 결정적으로 재현.

## 구현 범위
- 신규 `src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PostLockFreshReadDockerTest.kt`만 구현; T-01의 서비스/DTO/스키마 수정 금지, 실패 시 원인과 재현 증거를 메인 설계로 escalation
- MySQL 8.0.41 Testcontainers, 독립 connection·트랜잭션, 기본 REPEATABLE READ 유지; H2 대체 및 isolation/session SQL 변경 금지
- AT-006-1: A 정원 3→10 미커밋 + B 정원 3 요청의 파티 락 대기 → A 커밋 → B 성공 → 최종 Post·Party 모두 3
- AT-006-2: A 모집 완료 미커밋 + B 모집 재개 요청의 파티 락 대기 → A 커밋 → B 성공 → 최종 RECRUITING/OPEN
- 두 AT 모두 일반 RR snapshot 경로와 B가 Post를 미리 로드한 경로를 분리 실행; 잠금 조회만으로 managed instance를 재사용하는 잘못된 구현도 검출
- AT-002-2 MySQL 보강: joined=2/capacity=3에서 A 기존 PENDING 승인 후 미커밋(joined=3), B capacity=2 수정 대기; 승인 커밋 뒤 B가 최신 joined=3으로 409 거부, 편집 부분 반영 없음
- 락 대기 확인은 performance_schema.data_lock_waits 및 대상 party 행/connection 식별 기준; CountDownLatch/SqlPausePoint로 커밋 시점 제어; sleep만으로 경쟁 순서 추정 금지
- bounded timeout, finally에서 release·executor shutdown·SqlPausePoint disarm; 최종 상태는 새 트랜잭션에서 재조회
- 제외: 공용 테스트 지원 파일 변경·기존 수용 문서 수정·production code 수정·Docker 외 DB 사용

## 참고할 기존 패턴
- `src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PartyLockFreshReadDockerTest.kt`: 같은 영속성 컨텍스트 preload·최신 행 검증
- `src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PartyAggregateDeadlockDockerTest.kt`: SqlPausePoint·독립 tx·락 대기 관측
- `src/dockerTest/java/com/dogGetDrunk/meetjyou/support/LockWaitAssertions.kt`
- `src/dockerTest/java/com/dogGetDrunk/meetjyou/support/SqlPausePoint.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/party/ApproveJoinRequestTest.kt`: HOST/PENDING 승인 준비
- `.claude/rules/party.md`, `.claude/rules/post-plan.md`: 최신 잠금 읽기·순서 규칙

## 관련 spec 발췌
- REQ-006: “findUuidByPostUuid가 REPEATABLE READ snapshot 생성”; “PESSIMISTIC_WRITE 쿼리 + entityManager.refresh(post, PESSIMISTIC_WRITE)”
- AT-006-1: “정원 3→10 수정 커밋 중 락 대기하던 정원 3 수정 → 커밋 후 Post·Party 모두 3”
- AT-006-2: “모집 완료 커밋 중 락 대기하던 모집 재개 → 커밋 후 Post RECRUITING, Party OPEN”
- REQ-002·004: locked refresh 이후 joined로 판정, approveJoinRequest와 정원 수정 직렬화

## 분류·결합도·실행 계획
- high 근거: 락·트랜잭션·RR snapshot 경쟁 조건 검증의 상 조건 충족, 테스트 전용 작업이라는 이유로 low 하향 금지
- effort_override null: tier 기본 effort 유지, max 금지
- depends_on T-01: 신규 PostRowLock과 변경된 서비스 경로 소비; 독립 티켓 아님, 병렬 실행 금지
- base_ref `origin/main`, T-01 산출물 반영 후 시작; 추가 설계·변경 파일 필요 시 escalation

## 검증·완료 기준
- `./gradlew dockerTest --tests 'com.dogGetDrunk.meetjyou.party.PostLockFreshReadDockerTest'`: 세 AT의 모든 변형 통과, 실제 lock wait 증거 포함
- `./gradlew test build` + `./gradlew dockerTest`: 최종 M1 전체 gate 통과, skip/미실행 없이 실제 실행 결과 기록
- `git diff --check`: 문서/소스 공백 오류 없음
- AT-006-1·2 및 AT-002-2 MySQL 부분 충족; 코드 실패는 이 티켓에서 임의 수정하지 않고 blocking 보고

## 수용 테스트 코드 (보호 대상)
- 원본: 이 태스크 폴더 `tests/code/` 아래 src와 같은 경로 구조 (별도 세션 Terra 작성)
- 구현 첫 커밋: 이 티켓 acceptance_tests의 보호 대상 파일을 `tests/code/<경로>` → `<경로>`로 그대로 복사 (diff 0)
- 복사본 수정 금지. 테스트 오류 의심 시 수정하지 말고 escalation
- 리뷰어: `diff .pipeline/tasks/20261102-post-party-sync/tests/code/<경로> <경로>` 무출력 확인
