---
id: T-20261105-invalid-input-errors-02
title: 멱등 키 길이 검증과 비경합 무결성 예외 전파
tier: high
effort_override: null
depends_on: []
requirements: [REQ-002, REQ-003]
acceptance_tests: [AT-002-1, AT-002-2, AT-003-1, AT-003-2, AT-003-3]
touches:
  - src/main/java/com/dogGetDrunk/meetjyou/common/idempotency/IdempotencyKeyService.kt
  - src/main/java/com/dogGetDrunk/meetjyou/post/PostService.kt
  - src/main/java/com/dogGetDrunk/meetjyou/post/PostController.kt
  - src/main/java/com/dogGetDrunk/meetjyou/plan/PlanService.kt
  - src/main/java/com/dogGetDrunk/meetjyou/plan/PlanController.kt
  - .claude/rules/post-plan.md
  - src/test/java/com/dogGetDrunk/meetjyou/common/idempotency/IdempotencyKeyLengthServiceTest.kt
  - src/test/java/com/dogGetDrunk/meetjyou/common/idempotency/IdempotencyKeyLengthIntegrationTest.kt
  - src/test/java/com/dogGetDrunk/meetjyou/common/idempotency/NonConflictIntegrityControllerTest.kt
  - src/test/java/com/dogGetDrunk/meetjyou/common/idempotency/ConflictRecoveryServiceTest.kt
  - src/test/java/com/dogGetDrunk/meetjyou/common/idempotency/NonConflictIntegrityIntegrationTest.kt
consumes:
  - IdempotencyKeyService.resolveExisting
  - PostService.createPost
  - PlanService.createPlan
  - PostController.createPost
  - PlanController.createPlan
  - GlobalExceptionHandler.handleDataIntegrityViolation
produces:
  - IdempotencyKeyService 공통 길이 검증 메서드와 최대 255 상수
  - "PostService.resolveAfterConflict(request, key): CreatePostResponse?"
  - "PlanService.resolveAfterConflict(request, key): CreatePlanResponse?"
  - 키 행 없음 시 원본 DataIntegrityViolationException 재던짐
status: merged
---

## 목표

- 256자 키의 400 응답과 비경합 무결성 오류의 기존 409/500 처리 보장, 멱등 정상 분기 유지

## 구현 범위

- IdempotencyKeyService 한 곳의 최대 255 상수·검증 메서드; 두 생성 서비스 시작부에서 resolveExisting·생성보다 먼저 호출
- 256자는 InvalidInputException/400 INVALID_INPUT_VALUE, null은 멱등 생략, 255자는 정상 생성·재시도 유지
- 두 resolveAfterConflict: 키 행 없음이면 null, 존재하면 기존 응답 또는 해시 불일치 예외 유지
- 두 컨트롤러: `resolveAfterConflict(...) ?: throw e`로 잡았던 예외 인스턴스 그대로 재던짐
- `.claude/rules/post-plan.md`의 Idempotent create 3번에 키 행 없음 시 원래 예외 재던짐 반영
- 기존 핸들러의 유니크 매핑 409/미매핑 500 유지; 스키마·핸들러 재설계·키 정리 제외
- 기존 PostIdempotencyIntegrationTest 회귀; 새 플랜 동시성 통합 테스트 신설 제외(decisions C-r1-02)
- 공통 멱등 서비스·Post/Plan 서비스와 컨트롤러의 결합으로 REQ-002·003을 한 티켓에 배치

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/post/PostController.kt` `createPost` — 현재 `DataIntegrityViolationException` catch → `resolveAfterConflict`
- `src/main/java/com/dogGetDrunk/meetjyou/post/PostService.kt` `resolveAfterConflict` — 해시 재검증 주석 유지
- `src/test/java/com/dogGetDrunk/meetjyou/plan/CreatePlanIdempotencyTest.kt` — PlanService 단위 테스트 생성자·mock 구성
- `src/test/java/com/dogGetDrunk/meetjyou/post/PostIdempotencyIntegrationTest.kt` — 동시 요청·키 행 조회
- `src/test/java/com/dogGetDrunk/meetjyou/post/PostDetailConnectionIntegrationTest.kt` — `@SpyBean` 주입 선례

## 참조 파일 (읽기 허용)

- `.pipeline/tasks/20261105-invalid-input-errors/spec.md`
- `CLAUDE.md`
- `src/main/java/com/dogGetDrunk/meetjyou/common/idempotency/IdempotencyKey.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/idempotency/IdempotencyKeyRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/idempotency/IdempotencyScope.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/business/InvalidInputException.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/business/idempotency/IdempotencyKeyConflictException.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/business/notFound/PostNotFoundException.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/business/notFound/PlanNotFoundException.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/GlobalExceptionHandler.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/UniqueConstraintConflicts.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/ErrorCode.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/ErrorResponse.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/post/dto/CreatePostRequest.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/post/dto/CreatePostResponse.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/post/PostRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/dto/CreatePlanRequest.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/dto/CreatePlanResponse.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/PlanRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/MarkerRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/discord/DiscordAlertService.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/plan/CreatePlanIdempotencyTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/post/PostIdempotencyIntegrationTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/post/PostDetailConnectionIntegrationTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/plan/support/PlanFixtures.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/user/support/UserFixtures.kt`
- `src/test/resources/application-test.yml`

## 관련 spec 발췌

- REQ-002: 두 생성 서비스의 resolveExisting 이전 길이 검증, 256자는 400·리소스/키 행 미저장, 255자는 같은 키 재시도 시 같은 리소스
- REQ-003: 키 행 없음이면 null 및 원본 예외 재던짐, 실제 HTTP 미매핑 DIVE는 500 INTERNAL_SERVER_ERROR·404 아님
- 같은 키·같은 해시는 기존 리소스, 다른 해시는 409 IDEMPOTENCY_KEY_REUSED

## 분류 근거

- 여러 계층·두 모듈의 nullable 공유 인터페이스 변경으로 blast radius high, 멱등 복구로 state/concurrency high
- high 조건 우선; effort_override 없음, max 사용 없음

## 실행 계획·DAG

- 01·02·03은 선행 티켓 없이 독립 실행 후보; 02 내부 REQ-002·003은 함께 구현·검증
- 렌더링된 구현 지시서 검토는 후속 brief 단계

## 검증·증거 담당

- `./gradlew test --tests "*IdempotencyKeyLengthServiceTest" --tests "*IdempotencyKeyLengthIntegrationTest" --tests "*NonConflictIntegrityControllerTest" --tests "*ConflictRecoveryServiceTest" --tests "*NonConflictIntegrityIntegrationTest" --tests "*PostIdempotencyIntegrationTest"`
- base RED: AT-003-1(`NonConflictIntegrityControllerTest`)은 base에서 컴파일 실패가 RED — `resolveAfterConflict` nullable 반환이 확정 인터페이스이기 때문, 수정 되돌림 RED도 컴파일 실패로 충족. 나머지 AT의 base assertion 실패는 메인 설계자가 이 파일을 제외한 실행으로 확인 완료(spec 회귀 증명 절)
- 정상 경계·기존 멱등 분기는 동작 유지로 구분
- 구현 워커: 보호 테스트 불변, GREEN·intent의 수정 되돌림 RED 확인 후 복원; 최종 구현 SHA의 `./gradlew test build` 출력 제출
- 기존 엔진 로그 활용, 새 절차 신설 없음; 위 명령은 실행 예정이며 현재 미실행
