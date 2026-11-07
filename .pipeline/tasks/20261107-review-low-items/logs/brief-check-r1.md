# Brief 점검 r1

- 대상: `logs/briefs/r1/T-20261107-review-low-items-01~05.md` (구현자 지시문) + 각 지시문이 복사시키는 보호 수용 테스트 11개
- 확인 결과 요약: 5개 지시문의 티켓 카드 = `tickets/` 원본과 동일(끝 빈 줄 차이만). T-03·T-04·T-05 보호 테스트는 호출 심볼·생성자·리포지토리 메서드가 기준 코드와 일치, 줄 길이(120자)·wildcard import·`!!` 위반 없음. 아래 3건만 결함

### B-r1-01 T-02 보호 테스트 `FirebaseConfigTimeoutTest`가 package-private `FirebaseOptions.getCredentials()`를 호출해 컴파일 실패

- 위치: `tests/code/src/test/java/com/dogGetDrunk/meetjyou/notification/sender/FirebaseConfigTimeoutTest.kt` 34행 `options.credentials shouldBe credentials`
- 근거: firebase-admin 9.5.0 jar의 `com/google/firebase/FirebaseOptions.class` 메서드 접근 플래그 확인 — `getCredentials()` = `0x0`(package-private), `getConnectTimeout`·`getReadTimeout`·`getWriteTimeout` = `0x1`(public). 테스트 패키지 `com.dogGetDrunk.meetjyou.notification.sender`에서는 접근 불가 → Kotlin 컴파일 오류(`Cannot access 'credentials'`)
- 영향: T-02 브랜치의 `src/test` 컴파일 전체 실패 → gate `./gradlew test build` 실패. 구현자는 보호 테스트 수정 금지라 진행 불가(`cause: protected_test`로 즉시 반려)
- 제안: 34~36행 "the passed credentials are kept" 케이스 삭제(spec 수용 기준은 타임아웃 3종만 요구), 또는 public API로만 검증. 수정 후 sha256 갱신

### B-r1-02 T-01 카드가 예시로 든 `AntPathRequestMatcher.antMatcher(...)`는 보호 단위 테스트 요청에서 경로를 못 읽어 공개 GET 케이스 전부 실패

- 위치
  - 카드(브리프 01 86행): "`RequestMatcher` 구현체 선택은 자유(예: `AntPathRequestMatcher.antMatcher(HttpMethod.GET, pattern)`)"
  - 보호 테스트 `tests/code/src/test/java/com/dogGetDrunk/meetjyou/auth/jwt/JwtAuthFilterPublicReadTest.kt` 45행 `MockHttpServletRequest(method, path)` — `requestURI`만 설정, `servletPath` = `""`, `pathInfo` = null
- 근거: 프로젝트 Spring Boot 3.4.1 → spring-security-web 6.4.2. 이 버전 `AntPathRequestMatcher`는 `UrlPathHelper`가 없으면(`antMatcher` 팩토리·2인자 생성자) `getServletPath()` + `getPathInfo()`로만 경로를 만듦(jar 바이트코드에 `getRequestURI` 참조 없음, `getServletPath`·`getPathInfo`·`getPathWithinApplication` 참조 확인). 위 요청에서는 경로 `""` → 매칭 실패 → 필터가 기존 401 분기로 감 → AT-001-1 공개 GET 케이스(`/api/v1/notices`, `/api/v1/ios/versions/check`) 실패. `RegexRequestMatcher`도 같은 방식, `PathPatternRequestMatcher`는 6.5부터라 없음
- 실서버(AT-001-2, RANDOM_PORT Tomcat)는 `servletPath`에 전체 경로가 들어가 통과 → 카드 예시대로 구현하면 단위 AT만 깨지고 원인이 안 보임
- 제안: 카드 "하지 말 것/할 것"에 "매처는 `requestURI` 기준(컨텍스트 경로 제외)으로 판정 — 예: `AntPathRequestMatcher(pattern, HttpMethod.GET.name(), true, UrlPathHelper.defaultInstance)`" 명시하고 `antMatcher` 예시 삭제. 테스트 쪽 수정은 불필요

### B-r1-03 보호 테스트가 쓰는 파일이 해당 티켓 참조 파일 목록에 없음 (실패 시 구현자가 원인 확인 불가)

- T-03: `MarkerReplaceLockDockerTest.kt` 7행 `import com.dogGetDrunk.meetjyou.support.assertWaitedOnLock` → 정의 파일 `src/dockerTest/java/com/dogGetDrunk/meetjyou/support/LockWaitAssertions.kt`가 T-03 참조 파일에 없음(`SharedMySql.kt`만 있음). 잠금 대기 단언 실패 메시지 해석에 필요
- T-01: `MalformedUserUuidClaimIntegrationTest.kt` 대조군(147~156행)은 `/users/me/profile` 200이 선호도(성별·나이·성격) 시드에 달려 있음(`user.md`: `UserPreferenceData.from`) — `preference/Preference.kt`·`UserPreference.kt`·`PreferenceRepository.kt`·`UserPreferenceRepository.kt` 미포함. `PublicReadFailedTokenIntegrationTest.kt`의 `notice/NoticeRepository.kt`·`notice/Notice.kt`·`version/AppVersionService.kt`·`version/AppVersionDto.kt`, `JwtAuthFilterPublicReadTest.kt`의 `auth/CustomUserPrincipal.kt`·`user/User.kt`도 미포함
- 영향: 구현 자체엔 불필요하나, 이 테스트가 깨질 때 구현자는 "입력 문서 밖 탐색 금지"로 원인을 볼 수 없어 `cause` 판단(implementation vs protected_test)이 막힘
- 제안: 위 경로를 각 티켓 "참조 파일 (읽기 허용)"에 추가
