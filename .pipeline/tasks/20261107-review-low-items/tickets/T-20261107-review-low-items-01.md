---
id: T-20261107-review-low-items-01
title: 공개 GET 실패 토큰 익명 통과 + userUuid 클레임 형식 오류 401
tier: high
effort_override: null
depends_on: []
requirements: [REQ-001, REQ-002]
acceptance_tests: [AT-001-1, AT-001-2, AT-002-1, AT-002-2]
touches:
  - src/main/java/com/dogGetDrunk/meetjyou/config/PublicReadEndpoints.kt
  - src/main/java/com/dogGetDrunk/meetjyou/config/SecurityConfig.kt
  - src/main/java/com/dogGetDrunk/meetjyou/auth/jwt/JwtAuthFilter.kt
  - src/main/java/com/dogGetDrunk/meetjyou/auth/jwt/JwtProvider.kt
consumes:
  - JwtProvider.validateAccessTokenOrThrow
  - AuthenticatedUserCache.findActive
  - BusinessException(errorCode, value)
  - InvalidJwtException
  - org.springframework.security.web.util.matcher.RequestMatcher
produces:
  - PublicReadEndpoints (Kotlin object, config 패키지) — GET 공개 5개의 RequestMatcher 목록 + matches(request)
  - 'JwtAuthFilter: 대상 공개 GET에서 BusinessException 시 SecurityContext 비우고 익명 진행'
  - 'JwtProvider.getUserUuid: 클레임 누락·형식 오류 → InvalidJwtException'
  - JwtProvider.getExpiration 삭제
status: merged
---

## 목표

- 공개 GET 5개에 만료·무효·탈퇴 유저·refresh 토큰이 붙어도 토큰 없는 요청과 같은 결과로 응답
- `userUuid` 클레임이 UUID가 아니면 500 대신 401 `INVALID_JWT`

## 구현 범위

- 할 것
  - `config/PublicReadEndpoints.kt`: Kotlin `object`로 GET + 패턴 `RequestMatcher` 5개(`$V1/notices/**`, `$V1/terms/**`, `$V1/*/versions/check`, `$V1/*/versions/latest`, `$V1/users/is-duplicate-nickname`)와 `matches(request: HttpServletRequest): Boolean`
  - `SecurityConfig`: "Public read-only" 5줄을 이 객체의 매처로 교체(`requestMatchers(*PublicReadEndpoints.matchers...)` 등) — 문자열 패턴 중복 금지
  - `JwtAuthFilter.doFilterInternal`의 `catch (e: BusinessException)`: `PublicReadEndpoints.matches(request)`면 `SecurityContextHolder.clearContext()` 후 INFO 로그(경로·`errorCode`, 토큰 값 금지) 남기고 `filterChain.doFilter` 진행, 아니면 기존 오류 응답
  - `JwtProvider.getUserUuid`: 클레임 누락·`UUID.fromString` 실패 → WARN 로그(클레임 값 금지) + `InvalidJwtException`
  - `JwtProvider.getExpiration` 삭제 (+ 남는 미사용 import 정리)
- 하지 말 것
  - `JwtAuthFilter` 생성자 시그니처 변경 금지 — 공개 목록은 `object` 직접 참조 (기존 `JwtAuthFilterTest`·AT-001-1이 3인자 생성자 사용)
  - `/auth/**`·인프라 경로·GET 외 메서드 동작 변경 금지
  - `ChatStompInterceptor`·`UserAuthService` 수정 금지 (spec: 수정 불필요)
  - 기존 `JwtAuthFilterTest`·`JwtProviderTokenTypeTest` 수정 금지 (통과 유지)
  - 매처는 `requestURI` 기준(컨텍스트 경로 제외)으로 판정 — `AntPathRequestMatcher(pattern, HttpMethod.GET.name(), true, UrlPathHelper.defaultInstance)` 형태. `AntPathRequestMatcher.antMatcher(...)` 팩토리·2인자 생성자 금지: spring-security-web 6.4.2에서 `servletPath`+`pathInfo`로만 경로를 만들어 `MockHttpServletRequest(method, path)`(servletPath 빈 값)에서 매칭 실패 (B-r1-02). `SecurityConfig`와 필터가 같은 인스턴스를 사용

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/config/SecurityConfig.kt` — 현재 "Public read-only" `requestMatchers(HttpMethod.GET, ...)` 5줄
- `src/main/java/com/dogGetDrunk/meetjyou/auth/jwt/JwtProvider.kt` — `getJti`의 `?: throw InvalidJwtException(...)`
- `src/test/java/com/dogGetDrunk/meetjyou/auth/jwt/JwtAuthFilterTest.kt` — 필터 단위 테스트 구성
- `src/test/java/com/dogGetDrunk/meetjyou/auth/AuthSecurityReproIntegrationTest.kt` — RANDOM_PORT 통합 + `@MockBean` 외부 의존

## 참조 파일 (읽기 허용)

- `src/main/java/com/dogGetDrunk/meetjyou/preference/Preference.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/preference/UserPreference.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/preference/PreferenceRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/preference/UserPreferenceRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notice/NoticeRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notice/Notice.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/version/AppVersionService.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/version/AppVersionDto.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/auth/CustomUserPrincipal.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/user/User.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/auth/jwt/AuthenticatedUserCache.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/business/BusinessException.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/business/jwt/CustomJwtException.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/business/jwt/InvalidJwtException.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/business/user/UserWithdrawnException.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/ErrorCode.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/config/ApiVersionConfig.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/chat/connection/ChatStompInterceptor.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/user/UserAuthService.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/version/AppVersionController.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/version/Platform.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notice/NoticeController.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/terms/TermsController.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/user/UserController.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/auth/jwt/JwtAuthFilterTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/auth/jwt/JwtProviderTokenTypeTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/auth/AuthSecurityReproIntegrationTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/user/support/UserFixtures.kt`
- `src/test/resources/application-test.yml`
- `.claude/rules/auth.md`

## 관련 spec 발췌

- REQ-001: 대상 = `GET /api/v1/notices/**`, `GET /api/v1/terms/**`, `GET /api/v1/*/versions/check`, `GET /api/v1/*/versions/latest`, `GET /api/v1/users/is-duplicate-nickname`. 한 곳의 메서드 + 패턴 `RequestMatcher` 목록을 `SecurityConfig.permitAll`과 `JwtAuthFilter`가 `matches(request)`로만 사용(C-r1-02)
- REQ-001: 필터에서 오류 응답을 쓰는 분기는 `catch (e: BusinessException)` 하나 — 만료·서명·형식(`CustomJwtException` 하위), refresh 토큰 Bearer, 탈퇴·미존재 유저(`UserWithdrawnException`) 모두 여기로 옴. 대상 공개 요청 → 응답 미작성 + `SecurityContext` 비움 + 다음 필터, 그 외 → 기존 응답(C-r1-01)
- REQ-001: 유효 토큰이면 공개 요청에서도 인증 정보 설정. `*` 세그먼트 `ios`·`android`, `/api/v1/notices`·`/api/v1/notices/{uuid}` 모두 포함. 비공개 API 만료 → 401 `EXPIRED_TOKEN`, 형식 오류 → 401 `INVALID_JWT` 유지. 대상 경로의 GET 외 메서드 기존 유지
- REQ-002: `getUserUuid` 클레임 없음/UUID 아님 → `InvalidJwtException`(401 `INVALID_JWT`), WARN(값 금지). 호출부 3곳 수정 불필요. `getExpiration` 삭제(C-r1-03)

## 분류 근거

- 인증 필터·보안 설정 변경 → Cost of failure(인증) high, 보안은 테스트로 다 잡기 어려움(Verifiability high) → high
- REQ-002·`getExpiration` 삭제는 같은 `JwtProvider` 파일이라 같은 티켓(spec 마일스톤 T-01, 충돌 방지)
- effort_override 없음

## 실행 계획·DAG

- 선행 없음. T-01 ∥ T-02 ∥ T-03 ∥ T-04 ∥ T-05
- touches 교집합 없음: T-01만 `config/SecurityConfig.kt`·`config/PublicReadEndpoints.kt`·`auth/jwt/*`, T-02의 config 파일은 `ScheduleConfig.kt`·`ScheduledTaskErrorHandler.kt`

## 검증·증거 담당

- AT-001-1: `./gradlew test --tests "*JwtAuthFilterPublicReadTest"`
- AT-001-2: `./gradlew test --tests "*PublicReadFailedTokenIntegrationTest"`
- AT-002-1: `./gradlew test --tests "*JwtProviderUserUuidClaimTest"`
- AT-002-2: `./gradlew test --tests "*MalformedUserUuidClaimIntegrationTest"`
- 구현 워커: 보호 테스트 불변, 최종 SHA에서 `./gradlew test build` 실제 출력 제출
