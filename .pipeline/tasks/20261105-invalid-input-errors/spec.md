# Spec

> 근거: 승인된 intent.md (PR #267). 요구사항은 intent 성공 기준을 그대로 쪼갠 것이며 범위를 넓히지 않는다.

## 요구사항

### REQ-001 마커 요청 Bean Validation

- 요구사항
  - 상수: 마커 최대 개수 200, `place` 최대 100자, `memo` 최대 500자 (DB `marker.place VARCHAR(100) NOT NULL`, `marker.memo VARCHAR(500)`과 동일), 매직넘버 금지 → companion 상수
  - `CreatePlanRequest.markers`, `ReplaceMarkersRequest.markers`: `@field:Valid` + `@field:Size(max = 200)`
    - 둘 다 컨트롤러에서 이미 `@Valid @RequestBody` → 위반 시 `MethodArgumentNotValidException` → 기존 핸들러 400 `INVALID_INPUT_VALUE`
  - `CreateMarkerRequest` 필드 제약
    - `place`: `@NotBlank`, `@Size(max = 100)`
    - `memo`: `@Size(max = 500)` (null 허용 유지)
    - `lat`: −90.0 이상 90.0 이하 (`@DecimalMin`/`@DecimalMax`, 경계 포함)
    - `lng`: −180.0 이상 180.0 이하
    - `dayNum`: 1 이상, `idx`: 0 이상
  - 서비스·엔티티·스키마 변경 없음
- 수용 기준
  - 생성(`POST /api/v1/plans`)·일괄 교체(`PUT /api/v1/plans/{planUuid}/markers`) 각각: 201개 마커, `memo` 501자, `place` 101자, `place` 공백, `lat` 90.000001, `lng` −180.000001, `dayNum` 0, `idx` −1 → 400 `INVALID_INPUT_VALUE`
    - 생성 실패: 요청 전후 플랜·마커 건수 변화 0
    - 교체 실패: 기존 마커가 있는 플랜 fixture에서 기존 마커의 uuid·내용·개수 그대로, 신규 행 없음 (요청 후 독립 조회로 확인) (C-r1-01)
  - 경계값 통과: 마커 200개, `memo` 500자, `place` 100자, `lat` ±90, `lng` ±180, `dayNum` 1, `idx` 0, `memo` null → 정상 응답(생성·교체 모두 기존 200 유지, B-r1-01)
  - 빈 마커 리스트는 지금처럼 허용
- 수용 테스트 ID: AT-001-1(단위, DTO 제약 위반·경계 — `jakarta.validation.Validator`), AT-001-2(통합, 생성·교체 API 400과 미저장, 경계 통과)

### REQ-002 `Idempotency-Key` 길이 검증

- 요구사항
  - 최대 255자 (DB `idempotency_key.idempotency_key VARCHAR(255)`과 동일), 상수 1곳 (`IdempotencyKeyService` companion 등)
  - 검증 위치: `PostService.createPost`·`PlanService.createPlan` 시작부, `resolveExisting`·생성보다 먼저 → 초과 시 `InvalidInputException`(400 `INVALID_INPUT_VALUE`)
    - 검증 로직은 `IdempotencyKeyService`의 메서드 하나로 두고 두 서비스가 호출 (중복 구현 금지)
  - 키 없음(null)은 지금처럼 멱등 처리 생략
- 수용 기준
  - 256자 키로 게시글·플랜 생성 → 400 `INVALID_INPUT_VALUE` (404 아님), 리소스·키 행 저장 0건
  - 255자 키 → 정상 생성(기존 200), 같은 키·같은 바디 재요청 → 같은 리소스
- 수용 테스트 ID: AT-002-1(단위, 서비스가 256자에서 예외·리포지토리 미호출), AT-002-2(통합, 게시글·플랜 API 256자 400 / 255자 200)

### REQ-003 멱등 충돌 복구에서 키 경합이 아닌 예외는 원래대로 전파

- 요구사항
  - `PostService.resolveAfterConflict`·`PlanService.resolveAfterConflict`: 기존 키 행이 없으면 `NotFound`를 던지지 않고 null 반환 (반환 타입 nullable)
  - `PostController.createPost`·`PlanController.createPlan`: `resolveAfterConflict(...) ?: throw e` — 잡았던 `DataIntegrityViolationException` 원본을 그대로 재던짐
    - 이후 처리는 기존 `GlobalExceptionHandler.handleDataIntegrityViolation` (유니크 매핑이면 409, 아니면 500)
  - 기존 동작 유지: 키 행 있음 + 같은 해시 → 이긴 쪽 리소스 응답, 다른 해시 → 409 `IDEMPOTENCY_KEY_REUSED`
  - `.claude/rules/post-plan.md` "Idempotent create" 3번 항목에 "키 행이 없으면 원래 예외 재던짐" 반영
- 수용 기준
  - 키 경합이 아닌 `DataIntegrityViolationException`(키 행 없음) → 컨트롤러가 같은 예외 인스턴스를 던짐, `PostNotFoundException`/`PlanNotFoundException` 아님
  - 서비스 `resolveAfterConflict`: `resolveExisting`이 null → null 반환 / UUID → 기존 응답 / 해시 불일치 → `IdempotencyKeyConflictException`
  - 기존 `PostIdempotencyIntegrationTest`(동시 같은 키·같은 바디 → 한 리소스, 다른 바디 → 409) 계속 통과
  - 실제 HTTP 경로: 게시글·플랜 각각 `Idempotency-Key`를 붙인 생성 요청에서 생성 경계가 미매핑 제약의 `DataIntegrityViolationException`을 던지고 키 행이 없으면 → 500 `INTERNAL_SERVER_ERROR`, 404 아님 (C-r1-02)
    - 오류 주입: 생성 서비스 메서드를 `@SpyBean`으로 감싸 예외만 주입, `resolveAfterConflict`·`IdempotencyKeyService`·`GlobalExceptionHandler`는 실제 동작 (선례 `PostDetailConnectionIntegrationTest`의 `@SpyBean`)
- 수용 테스트 ID: AT-003-1(단위, 컨트롤러 — 서비스 mock이 DIVE 후 null 반환 시 원본 재던짐, 게시글·플랜 각각), AT-003-2(단위, 서비스 null 반환·기존 분기 유지), AT-003-3(통합, 게시글·플랜 비경합 DIVE → 500·404 아님)

### REQ-004 Apple ID token 검증 순서와 null 클레임

- 요구사항
  - `AppleJwtDecoder.decode` 순서: 파싱 → `kid` → JWKS 키 조회 → 서명 검증 → issuer → audience → `exp` → `iat`·`sub`
    - 서명 우선 근거: 서명 안 된 토큰의 클레임으로 분기·로그하지 않음. 미지 `kid`의 JWKS 재조회는 `AppleJwksCache`가 이미 30초당 2회로 제한 → 서명을 앞에 둬도 Apple 호출 증폭 없음
  - `exp`·`iat` null, `sub` null 또는 blank → `InvalidJwtException` (401), WARN 로그 (클레임 이름만, 토큰 값 금지)
  - JWKS 장애 시 503(`ExternalServiceException`) 동작은 그대로
- 수용 기준
  - 유효 서명 + `exp` 없음 / `iat` 없음 / `sub` 없음 / `sub` 공백 → `InvalidJwtException` (NPE 아님)
  - 잘못된 서명 + 만료된 `exp` → 서명 실패로 거부 (`InvalidJwtException`, 만료 메시지 `"Token has expired"` 아님)
  - 잘못된 서명 + `exp` 없음 → `InvalidJwtException`
  - 정상 토큰 → 기존과 같은 `Jwt`(subject·issuedAt·expiresAt), 유효 서명 + 만료 → 만료 메시지 `InvalidJwtException`, issuer·audience 불일치 → `InvalidJwtException`
  - 테스트 키: 테스트에서 생성한 RSA 키 + `AppleJwksCache.create(origin = 메모리 JWKSetSource, ...)`, 네트워크 없음
  - JWKS 키 공급 실패(`AppleJwksCache`가 `ExternalServiceException`) → 디코더가 그대로 전파, `InvalidJwtException`으로 바꾸지 않음
  - 실제 HTTP 경로 (C-r1-03): `POST /api/v1/auth/login`에 Apple ID token
    - 유효 서명 + `exp`/`iat`/`sub` 누락, `sub` 공백, 잘못된 서명 + 만료 `exp` → 401 (500 아님)
    - JWKS 공급 실패 → 503 `SOCIAL_PROVIDER_UNAVAILABLE`
    - 방법: `@MockBean AppleJwksCache`가 테스트 RSA 키 반환(또는 `ExternalServiceException` 던짐), 실제 `AppleJwtDecoder`·`AppleVerifier`·`GlobalExceptionHandler` 통과, issuer·client-id는 test 프로필 값(`https://appleid.apple.com`, `test-apple-client`), 로그인 요청 형식·nonce 흐름은 `AuthSecurityReproIntegrationTest` 선례
- 수용 테스트 ID: AT-004-1(단위, `AppleJwtDecoder` 위 케이스 전부), AT-004-2(통합, 로그인 API 401·503)

## 회귀 증명 (C-r1-04)

- 결함 재현 AT — base(수정 전)에서 실패해야 함: AT-001-1·AT-001-2의 위반 입력 케이스, AT-002-1, AT-002-2의 256자 케이스, AT-003-1, AT-003-3, AT-004-1·AT-004-2의 null 클레임·서명 순서 케이스
- 동작 유지 AT — base에서도 통과: 경계값 통과, 빈 리스트, 255자 키, 기존 멱등 분기, 정상 토큰·만료·issuer/audience, JWKS 503
- AT-003-1(컨트롤러 단위)은 base에서 **컴파일 실패가 RED**: 확정 인터페이스(`resolveAfterConflict` nullable)를 stub이 직접 쓰기 때문, 수정 되돌림 시에도 컴파일 실패로 RED (B-r1-02)
- 증거 (2026-11-05 메인 설계자, base `HEAD` 임시 worktree에서 실행)
  - base + 보호 테스트(AT-003-1 제외): 전체 719개 중 실패 36개 전부 새 테스트, 기존 테스트 실패 0 — 결함 재현 케이스만 실패, 동작 유지 케이스 통과
  - AT-004-2 nonce 보정 후 base: `exp`·`iat`·`sub` 누락 500, 공백 `sub` 404로 실패, 잘못된 서명 401·JWKS 503 통과
  - spec대로 최소 구현 적용 시 전체 723개 통과 (보호 테스트 결함 3건 수정 후, decisions 참조) — 구현은 검증용, 폐기
- 구현 PR은 같은 SHA의 `./gradlew test build` 출력

## 확정 인터페이스

### API 시그니처·DTO

- 엔드포인트·요청·응답 형식 변경 없음, 제약만 추가
- `PostService.resolveAfterConflict(request, key): CreatePostResponse?`, `PlanService.resolveAfterConflict(request, key): CreatePlanResponse?`
- `IdempotencyKeyService`: 키 길이 검증 메서드 1개 + 최대 길이 상수 (이름은 구현 재량)

### DB 스키마

- 변경 없음

## 마일스톤

- T-01 마커 검증 (REQ-001), T-02 멱등 키 (REQ-002·003), T-03 Apple 디코더 (REQ-004) — 파일이 겹치지 않아 병렬
