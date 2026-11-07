---
id: T-20261107-review-low-items-05
title: 미사용 리포지토리 메서드 12개 삭제
tier: medium
effort_override: null
depends_on: []
requirements: [REQ-007]
acceptance_tests: [AT-007-1]
touches:
  - src/main/java/com/dogGetDrunk/meetjyou/party/PartyRepository.kt
  - src/main/java/com/dogGetDrunk/meetjyou/userparty/UserPartyRepository.kt
  - src/main/java/com/dogGetDrunk/meetjyou/preference/UserPreferenceRepository.kt
  - src/main/java/com/dogGetDrunk/meetjyou/preference/PreferenceRepository.kt
  - src/main/java/com/dogGetDrunk/meetjyou/chat/participant/ChatParticipantRepository.kt
  - src/main/java/com/dogGetDrunk/meetjyou/notification/push/PushTokenRepository.kt
  - src/test/java/com/dogGetDrunk/meetjyou/party/PartyQueryOptimizationTest.kt
  - src/test/java/com/dogGetDrunk/meetjyou/party/PartyControllerTest.kt
consumes:
  - PartyRepository.findAllByPlanUuidWithPlan
  - UserPartyRepository.findAllWithPartyByUserUuid
produces:
  - 12개 메서드가 각 리포지토리 인터페이스에서 제거됨
status: merged
---

## 목표

- src/main 호출 0건인 public 리포지토리 메서드 12개 제거, 빌드(detekt·Konsist) 통과

## 구현 범위

- 할 것 — 아래 12개 삭제 (+ 그로 인해 미사용이 된 import 정리)
  - `PartyRepository`: `findAllByPlan_Uuid`, `existsByPlan_Uuid`
  - `UserPartyRepository`: `findAllByUser_Uuid`, `findAllByParty_Uuid`, `existsByParty_UuidAndUser_Uuid`, `existsByParty_UuidAndUser_UuidAndMemberStatus`
  - `UserPreferenceRepository`: `findPreferenceByUserIdAndType`, `findPreferencesByUserIdAndType`
  - `PreferenceRepository`: `findByName`
  - `ChatParticipantRepository`: `findAllByRoom_UuidAndUser_UuidIn`
  - `PushTokenRepository`: `findAllByUserIdAndActiveTrue`, `existsByUserIdAndToken`
  - 테스트 사용처
    - `PartyQueryOptimizationTest` 88·103행 `verify(exactly = 0) { ...findAllByPlan_Uuid / findAllByUser_Uuid... }` 제거 — 메서드 부재로 호출 불가가 컴파일 시점에 보장됨. 같은 케이스의 `verify(exactly = 1)` fetch-join 검증은 유지, 35행 주석·95행 테스트 이름에서 삭제 메서드 언급 갱신
    - `PartyControllerTest` 18행 주석의 `PartyRepository.findAllByPlan_Uuid` → 실제 사용 중인 `findAllByPlanUuidWithPlan`으로 갱신 (sort 검증 내용 유지)
  - 삭제 직전 `git grep -nw <메서드명> -- src` 재검색(`src/main`·`src/test`·`src/dockerTest`·`src/gatling`) — 설계 시점(2026-11-07) 결과는 위 2개 테스트 외 0건. 새 호출이 생겼으면 그 메서드는 남기고 PR 본문에 기록
- 하지 말 것
  - `JwtProvider.getExpiration` 삭제 금지 (T-01 소유)
  - 다른 메서드 정리·리팩터링 금지, detekt `baseline.xml` 추가 금지

## 참고할 기존 패턴

- `src/test/java/com/dogGetDrunk/meetjyou/convention/ConventionTest.kt` — 리플렉션·구조 검사 테스트 위치

## 참조 파일 (읽기 허용)

- `src/main/java/com/dogGetDrunk/meetjyou/party/PartyService.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/party/PartyController.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/convention/ConventionTest.kt`
- `config/detekt/detekt.yml`

## 관련 spec 발췌

- REQ-007: 위 12개 삭제(`getExpiration`은 REQ-002). 테스트 전용 사용처(`PartyQueryOptimizationTest`, `PartyControllerTest` 등)는 남은 메서드·`findAll()` 필터로 바꾸고 검증 내용 유지. 삭제 전 전체 재검색, 새 호출 생기면 남기고 PR 본문 기록
- 수용: 12개가 각 인터페이스에 없음(리플렉션), 테스트 이름·주석에 "#231 정리 확인용, 같은 이름 메서드가 다시 필요하면 이 테스트 삭제"(C-r1-11). `./gradlew build` 통과

## 분류 근거

- 기존 패턴 있음·무상태·AT로 판정 가능·실패 시 다음 티켓에서 수정 가능 = low 조건이나, 5개 모듈 리포지토리 인터페이스에 걸쳐 "One module" 조건 불충족 → medium
- 같은 성격(삭제)의 low급 작업이라 모듈이 달라도 한 티켓으로 묶음, effort_override 없음

## 실행 계획·DAG

- 선행 없음, 다른 티켓과 병렬. touches 교집합 없음(T-01의 `JwtProvider` 제외)

## 검증·증거 담당

- AT-007-1: `./gradlew test --tests "*UnusedRepositoryMethodRemovalTest"`
- 기존 `*PartyQueryOptimizationTest`·`*PartyControllerTest` 통과
- 구현 워커: 보호 테스트 불변, 최종 SHA에서 `./gradlew test build` 실제 출력 제출
