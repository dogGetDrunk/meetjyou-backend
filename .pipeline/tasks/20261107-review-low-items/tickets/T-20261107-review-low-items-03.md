---
id: T-20261107-review-low-items-03
title: 마커 교체를 plan 행 잠금으로 직렬화
tier: high
effort_override: null
depends_on: []
requirements: [REQ-005]
acceptance_tests: [AT-005-1]
touches:
  - src/main/java/com/dogGetDrunk/meetjyou/plan/PlanRepository.kt
  - src/main/java/com/dogGetDrunk/meetjyou/plan/MarkerService.kt
  - src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerServiceTest.kt
  - .claude/rules/post-plan.md
consumes:
  - MarkerRepository.deleteAllByPlan
  - MarkerRepository.saveAll
  - CurrentUserProvider.uuid
  - org.springframework.data.jpa.repository.Lock(PESSIMISTIC_WRITE)
produces:
  - 'PlanRepository.findForUpdateByUuid(uuid: UUID): Plan? (@Lock PESSIMISTIC_WRITE + uuid 조건 @Query)'
  - 'MarkerService.replaceMarkers: 트랜잭션 첫 DB 읽기 = findForUpdateByUuid'
status: merged
---

## 목표

- 같은 plan의 마커 교체 요청이 겹쳐도 최종 마커가 한 요청분만 남음

## 구현 범위

- 할 것
  - `PlanRepository.findForUpdateByUuid`: `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@Query("SELECT p FROM Plan p WHERE p.uuid = :uuid")` (이름은 `RefreshTokenRepository.findForUpdateByJti` 선례)
  - `MarkerService.replaceMarkers`: 첫 줄 `planRepository.findByUuid` → `findForUpdateByUuid ?: throw PlanNotFoundException`, 이후 소유자 검사·`deleteAllByPlan`·`saveAll` 그대로
  - `MarkerServiceTest`의 `replaceMarkers` 케이스 stub을 `findForUpdateByUuid`로 교체(검증 내용 유지, `getMarkersByPlan` 케이스는 그대로)
  - `.claude/rules/post-plan.md`에 한 줄: 마커 교체는 plan 행 잠금(`findForUpdateByUuid`) 먼저, plan 삭제(마커 → plan)와 순서가 반대라 동시 실행 데드락은 409 `CONCURRENT_REQUEST_CONFLICT` 사후 처리로 허용
- 하지 말 것
  - 잠금 조회 전에 같은 트랜잭션에서 plan·마커 일반 조회 금지 (REPEATABLE READ 스냅샷이 잠금 대기 전에 잡힘)
  - `deleteAllByPlan`을 bulk delete로 교체 금지 (`MarkerRepository` 주석: 갭 락 데드락)
  - `getMarkersByPlan`·plan 삭제 경로 변경 금지
  - `PartyRowLock`식 refresh 구현 불필요 — 첫 읽기가 잠금 조회라 관리 엔티티가 없음

## 검증 의무 (C-r1-09)

- 구현자는 `./gradlew dockerTest`(Docker 데몬 필요)를 직접 실행해 AT-005-1 포함 출력 첨부
- Docker 데몬이 없으면 완료가 아니라 blocking으로 보고 (엔진 gate `./gradlew test build`에는 dockerTest 없음, CI `build-and-test`가 `./gradlew build dockerTest` 실행)

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/auth/refreshtoken/RefreshTokenRepository.kt` — `findForUpdateByJti` `@Lock` 선언
- `src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PostLockFreshReadDockerTest.kt` — `@DataJpaTest` + 서비스 수동 조립 + mockk
- `src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PartyAggregateDeadlockDockerTest.kt` — `performance_schema.data_lock_waits` 폴링

## 참조 파일 (읽기 허용)

- `src/dockerTest/java/com/dogGetDrunk/meetjyou/support/LockWaitAssertions.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/Plan.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/Marker.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/MarkerRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/PlanAccessGuard.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/PlanService.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/dto/CreateMarkerRequest.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/dto/MarkerResponse.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/util/CurrentUserProvider.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/auth/refreshtoken/RefreshTokenRepository.kt`
- `src/dockerTest/java/com/dogGetDrunk/meetjyou/support/SharedMySql.kt`
- `src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PostLockFreshReadDockerTest.kt`
- `src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PartyAggregateDeadlockDockerTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerInputValidationIntegrationTest.kt`
- `.claude/rules/party.md`

## 관련 spec 발췌

- REQ-005: `PlanRepository`에 `PESSIMISTIC_WRITE` 잠금 조회(`@Lock` + uuid). `replaceMarkers`는 잠금 조회를 트랜잭션 첫 DB 읽기로, 그 전 일반 조회 금지. `deleteAllByPlan` 파생 delete 유지. plan 삭제 ↔ 마커 저장 데드락은 409 사후 처리, `post-plan.md` 한 줄
- 수용(dockerTest): 보유 트랜잭션이 plan 행 `FOR UPDATE` + 마커를 X로 교체(미커밋) 동안 `replaceMarkers`(Y)가 잠금 대기(`data_lock_waits`, `innodb_trx.trx_state` 금지) → 커밋 후 성공, 최종 = Y 정확히, X 잔존 0(C-r1-08)

## 분류 근거

- 행 잠금·트랜잭션 스냅샷 순서가 정확성을 결정(State/concurrency high), 경합은 단위 테스트로 못 잡음 → high
- effort_override 없음

## 실행 계획·DAG

- 선행 없음, 다른 티켓과 병렬. plan 패키지는 T-03만 수정

## 검증·증거 담당

- AT-005-1: `./gradlew dockerTest --tests "*MarkerReplaceLockDockerTest"` (Docker 데몬 필요)
- 기존 `*MarkerServiceTest`·`*MarkerInputValidationIntegrationTest` 통과
- 구현 워커: 보호 테스트 불변, 최종 SHA에서 `./gradlew test build`와 `./gradlew dockerTest` 실제 출력 제출
