---
id: T-20261105-invalid-input-errors-01
title: 마커 요청 Bean Validation과 기존 마커 보존 검증
tier: high
effort_override: null
depends_on: []
requirements: [REQ-001]
acceptance_tests: [AT-001-1, AT-001-2]
touches:
  - src/main/java/com/dogGetDrunk/meetjyou/plan/dto/CreateMarkerRequest.kt
  - src/main/java/com/dogGetDrunk/meetjyou/plan/dto/CreatePlanRequest.kt
  - src/main/java/com/dogGetDrunk/meetjyou/plan/dto/ReplaceMarkersRequest.kt
  - src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerRequestValidationTest.kt
  - src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerInputValidationIntegrationTest.kt
consumes:
  - CreatePlanRequest.markers
  - ReplaceMarkersRequest.markers
  - CreateMarkerRequest
  - jakarta.validation.Validator
produces:
  - 마커 입력 위반에 대한 400 INVALID_INPUT_VALUE
  - 정상·경계·빈 리스트의 기존 API 계약 유지
status: merged
---

## 목표

- 마커 요청의 중첩 DTO 검증과 개수·필드 제약 추가, 잘못된 생성·교체의 무변경 처리

## 구현 범위

- CreatePlanRequest.markers·ReplaceMarkersRequest.markers에 `@field:Valid`·`@field:Size(max = 200)`
- CreateMarkerRequest: place `@NotBlank`·100자, nullable memo 500자, lat −90~90·lng −180~180(경계 포함), dayNum >= 1·idx >= 0
- 반복 제한값 companion 상수화; 서비스·엔티티·스키마 변경 없음
- 생성 실패는 플랜·마커 건수 변화 0, 교체 실패는 기존 마커 uuid·내용·개수 보존을 요청 후 독립 조회로 확인
- centerLat/centerLng·UpdatePlanRequest 등 마커 밖 검증 제외

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/plan/dto/CreatePlanRequest.kt` — 기존 `@field:NotBlank`·`@field:Size` 표기
- `src/main/java/com/dogGetDrunk/meetjyou/plan/MarkerController.kt` — `replaceMarkers`의 `@Valid @RequestBody`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/GlobalExceptionHandler.kt` — `handleMethodArgumentNotValid` → 400 `INVALID_INPUT_VALUE`
- `src/test/java/com/dogGetDrunk/meetjyou/post/PostIdempotencyIntegrationTest.kt` — RANDOM_PORT 통합 테스트·인증 헤더 구성

## 참조 파일 (읽기 허용)

- `.pipeline/tasks/20261105-invalid-input-errors/spec.md`
- `CLAUDE.md`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/PlanController.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/MarkerController.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/MarkerService.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/Marker.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/Plan.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/MarkerRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/PlanRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/dto/MarkerResponse.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/dto/CreatePlanResponse.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/GlobalExceptionHandler.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/ErrorResponse.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/ErrorCode.kt`
- `src/main/resources/db/migration/V1__initial_schema.sql`
- `src/test/java/com/dogGetDrunk/meetjyou/plan/support/PlanFixtures.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/user/support/UserFixtures.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/post/PostIdempotencyIntegrationTest.kt`
- `src/test/resources/application-test.yml`

## 관련 spec 발췌

- REQ-001: 두 리스트 최대 200개·중첩 검증, place 100자·memo 500자, 좌표 범위·dayNum/idx 제약
- 201개·길이 초과·공백 place·범위 초과·dayNum 0·idx −1은 생성/교체 모두 400 INVALID_INPUT_VALUE
- 경계값은 생성·교체 모두 기존 200 응답 그대로(상태 코드 변경 없음), 빈 리스트·memo null 허용

## 분류 근거

- 공유 요청 DTO/API 제약 변경으로 blast radius high; 다른 기준의 단순성보다 high 조건 우선
- effort_override 없음; max 사용 없음

## 실행 계획·DAG

- `T-20261105-invalid-input-errors-01 ∥ T-20261105-invalid-input-errors-02 ∥ T-20261105-invalid-input-errors-03`, 선행 티켓 없음
- 01은 plan DTO 3개, 02는 멱등 서비스·post/plan 서비스·컨트롤러, 03은 `AppleJwtDecoder`만 수정 — touches 교집합 없음

## 검증·증거 담당

- AT-001-1: `./gradlew test --tests "*MarkerRequestValidationTest"`
- AT-001-2: `./gradlew test --tests "*MarkerInputValidationIntegrationTest"`
- test-writer: 위반 입력의 base assertion 실패 확인; 경계·빈 리스트·null memo는 동작 유지로 구분
- 구현 워커: 보호 테스트 불변, 수정 후 GREEN 및 intent의 수정 되돌림 RED 확인·복원, 최종 구현 SHA의 `./gradlew test build` 출력 제출
- 기존 엔진 로그 활용; 새 검증 절차 신설 없음; 위 명령은 실행 예정이며 이번 설계에서 미실행
