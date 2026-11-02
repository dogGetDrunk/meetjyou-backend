---
id: T-20261102-post-party-sync-03
title: 수용 테스트 보조 단언 보강 + REQ-007 근거 문단 정합
tier: low
effort_override: null
depends_on: []
requirements: [REQ-001, REQ-002, REQ-004, REQ-007]
acceptance_tests: [AT-001-3, AT-002-2, AT-002-3, AT-004-1, AT-007-2]
touches:
  - src/test/java/com/dogGetDrunk/meetjyou/post/PostPartySyncIntegrationTest.kt
  - src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PostUpdatePartyCompleteDeadlockDockerTest.kt
  - src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PartyAggregateDeadlockDockerTest.kt
consumes:
  - CompPreferenceRepository (Kotlin by 위임 테스트 대역)
produces:
  - 보조 단언 (plan·isPlanPublic·compPreference 보존, 일정 보존, marker 삭제)
status: blocked
---

## 목표
T-01 머지 판단의 후속 (merge_with_followup, 리뷰 nit F1 대응). 프로덕션 코드 변경 없음

## 구현 범위
- AT-001-3: seed에 plan 링크·compPreference C0, 실패 주입은 실제 repository를 `by` 위임으로 감싼 대역(saveAll만 throw), 롤백 후 plan·C0 보존 재조회
- AT-002-2/3: 정원 거부 뒤 plan·isPlanPublic·compPreference 보존 단언
- AT-004-1: 편집 거부 뒤 itinStart/itinFinish 보존 단언
- AT-007-2: deletePlan 승리 시 marker 삭제 단언
- 보호 테스트이므로 test-writer 세션이 tests/code에 작성, 구현자는 그대로 복사
- 하지 말 것: 락 순서·deletePlan 구조 변경, 프로덕션 코드

## 구현자 작업 (복사만)
- `git fetch origin` 후 `origin/docs/result-final-20261102-post-party-sync`의 `.pipeline/tasks/20261102-post-party-sync/tests/code/` 아래 3파일을 같은 src 경로에 바이트 동일 복사 (`git show <ref>:<path> > <src path>`)
  - `src/test/java/com/dogGetDrunk/meetjyou/post/PostPartySyncIntegrationTest.kt`
  - `src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PostUpdatePartyCompleteDeadlockDockerTest.kt`
  - `src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PartyAggregateDeadlockDockerTest.kt`
- 수정 금지. gate·dockerTest 실패 시 고치지 말고 escalation
- PR 본문에 `Refs #223`
- 메인 설계 사전 검증: `./gradlew build` 596 tests 0 fail, `./gradlew dockerTest` 47 tests 0 fail, 회귀 주입 red 확인

## 완료 기준
- `./gradlew build dockerTest` 통과, 회귀 주입 시 보강 단언 red 확인

## 출처
- 판단 보고서 logs/reports/T-01-judge.json, 리뷰 review-2.json F1
- F2(spec REQ-007 근거 문단)는 메인 설계가 result PR에서 직접 정정 (티켓 범위 아님)
