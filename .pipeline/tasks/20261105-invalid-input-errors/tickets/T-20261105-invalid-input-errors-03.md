---
id: T-20261105-invalid-input-errors-03
title: Apple ID token 서명 우선과 null 클레임 오류 분류
tier: high
effort_override: null
depends_on: []
requirements: [REQ-004]
acceptance_tests: [AT-004-1, AT-004-2]
touches:
  - src/main/java/com/dogGetDrunk/meetjyou/auth/social/apple/AppleJwtDecoder.kt
  - src/test/java/com/dogGetDrunk/meetjyou/auth/social/apple/AppleInvalidClaimsDecoderTest.kt
  - src/test/java/com/dogGetDrunk/meetjyou/auth/social/apple/AppleInvalidClaimsLoginIntegrationTest.kt
consumes:
  - AppleJwtDecoder.decode
  - AppleJwksCache.create(origin, ...)
  - AppleVerifier
  - POST /api/v1/auth/login
  - GlobalExceptionHandler
produces:
  - AppleJwtDecoder.decode 서명 우선·누락 클레임 방어
  - 잘못된 Apple token 401 및 JWKS 장애 503 유지
status: merged
---

## 목표

- 서명 검증 전 미검증 클레임 분기 방지, exp/iat/sub 누락의 401과 외부 공급 장애의 503 유지

## 구현 범위

- 순서: parsing → kid → JWKS 조회 → 서명 → issuer → audience → exp → iat/sub
- exp/iat null·sub null/blank는 InvalidJwtException; WARN은 클레임 이름만, 토큰 값 금지
- 서명 실패+만료는 서명 실패이며 Token has expired 아님; 정상 Jwt·유효 서명 만료·issuer/audience 검증 유지
- JWKS ExternalServiceException 그대로 전파; HTTP 503 SOCIAL_PROVIDER_UNAVAILABLE 유지
- 단위는 메모리 JWKSetSource·RSA 테스트 키; 통합은 @MockBean AppleJwksCache와 실제 decoder·verifier·handler
- JWKS 캐시/재조회 정책·Google/Kakao·API 형식 변경 제외

## 참고할 기존 패턴

- `src/test/java/com/dogGetDrunk/meetjyou/auth/social/apple/AppleJwksCacheTest.kt` — 메모리 `JWKSetSource`로 `AppleJwksCache.create` 구성
- `src/test/java/com/dogGetDrunk/meetjyou/auth/AuthSecurityReproIntegrationTest.kt` — `/auth/nonce` 발급·세션 쿠키·`/auth/login` 요청, `@MockBean` 구성
- `src/main/java/com/dogGetDrunk/meetjyou/auth/social/apple/AppleJwksCache.kt` — 미지 kid 재조회 30초당 2회 제한(서명 우선 근거)

## 참조 파일 (읽기 허용)

- `.pipeline/tasks/20261105-invalid-input-errors/spec.md`
- `CLAUDE.md`
- `src/main/java/com/dogGetDrunk/meetjyou/auth/social/apple/AppleJwksCache.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/auth/social/apple/AppleVerifier.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/auth/social/apple/AppleOidcProperties.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/config/AppleOidcConfig.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/auth/social/SocialPrincipal.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/user/UserAuthController.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/user/dto/LoginRequest.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/user/dto/NonceResponse.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/business/jwt/InvalidJwtException.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/business/ExternalServiceException.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/GlobalExceptionHandler.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/ErrorCode.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/auth/social/apple/AppleJwksCacheTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/auth/social/apple/AppleVerifierTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/auth/social/apple/AppleTestKeys.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/auth/AuthSecurityReproIntegrationTest.kt`
- `src/test/resources/application-test.yml`
- `.claude/rules/auth.md`

## 관련 spec 발췌

- REQ-004: 누락 exp/iat/sub·blank sub는 401, 서명 검증은 클레임 검사보다 먼저
- 정상 subject/issuedAt/expiresAt 및 만료·issuer/audience 기존 동작 유지
- 실제 Apple 로그인 경로에서 401, JWKS 공급 실패는 503 SOCIAL_PROVIDER_UNAVAILABLE

## 분류 근거

- 인증 경로 변경으로 cost of failure high, 서명 순서의 보안 판단으로 verifiability high
- high 조건 우선; effort_override 없음, max 사용 없음

## 실행 계획·DAG

- 01·02와 선행 관계 없음; Apple decoder 및 별도 보호 테스트만 변경하는 독립 티켓
- 렌더링된 구현 지시서 검토는 후속 brief 단계

## 검증·증거 담당

- `./gradlew test --tests "*AppleInvalidClaimsDecoderTest" --tests "*AppleInvalidClaimsLoginIntegrationTest"`
- test-writer: 누락·서명 순서의 base assertion 실패 확인; 정상·만료·issuer/audience·JWKS 장애는 동작 유지로 구분
- HTTP 401만으로 검사 순서 입증 불가, 단위 AT의 서명 실패 원인 검증 필수
- 구현 워커: 보호 테스트 불변, GREEN·intent의 수정 되돌림 RED 확인 후 복원; 최종 구현 SHA의 `./gradlew test build` 출력 제출
- 기존 엔진 로그 활용, 새 절차 신설 없음; 위 명령은 실행 예정이며 현재 미실행
