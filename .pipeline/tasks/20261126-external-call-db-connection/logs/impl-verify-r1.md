### V-r1-01 Social auth tests bypass production provider selection

- **Kind** An accepted critique item not implemented.
- **Decision** `C-r1-01`, `AT-001`, and `AT-002`. `decisions.md:8` requires “세 provider 독립 케이스와 검증기 1회 호출 확인” and `spec.md:129` specifies “세 검증기(`KakaoVerifier`, `GoogleVerifier`, `AppleVerifier`)를 `@MockBean` 으로 두고”.
- **Grounds** The primary test registry overrides `get` and returns a generic verifier that records the provider passed to it. The assertions check that recorded value and the call count. `spec-changelog.md:13` records the substitution. [ExternalCallTestConfig.kt:33](/Users/serendipity/projects/meetjyou-backend-tasks/20261126-external-call-db-connection/src/test/java/com/dogGetDrunk/meetjyou/support/ExternalCallTestConfig.kt:33) · [SocialAuthConnectionIntegrationTest.kt:75](/Users/serendipity/projects/meetjyou-backend-tasks/20261126-external-call-db-connection/src/test/java/com/dogGetDrunk/meetjyou/user/SocialAuthConnectionIntegrationTest.kt:75)
- **Impact** All six cases can pass if the production registry dispatches a request to the wrong provider verifier.

### V-r1-02 Login acceptance tests omit the token pair assertion

- **Kind** An acceptance criterion not met.
- **Decision** `REQ-001` and `AT-001`. `spec.md:22` requires “응답 200과 토큰 쌍 발급”.
- **Grounds** Each provider’s login case checks the verifier observation and HTTP 200. It leaves the response body unchecked. [SocialAuthConnectionIntegrationTest.kt:91](/Users/serendipity/projects/meetjyou-backend-tasks/20261126-external-call-db-connection/src/test/java/com/dogGetDrunk/meetjyou/user/SocialAuthConnectionIntegrationTest.kt:91)
- **Impact** A 200 response missing either `accessToken` or `refreshToken` can pass `AT-001`.

### V-r1-03 The HTTP test can pass with the default Kakao read timeout

- **Kind** An acceptance criterion not met.
- **Decision** `C-r1-02` and `AT-003`. `decisions.md:9` requires “수신 latch로 응답 보류 중 timeout임을 증명” and `spec.md:48` specifies “read timeout을 200ms로 주입”.
- **Grounds** The HTTP test checks request receipt, HTTP 503, and `errorCode`. The server automatically ends its hold after five seconds, which is also the client’s default read timeout. The test has no assertion that distinguishes the configured 200 ms timeout from the five-second fallback. [KakaoLoginTimeoutIntegrationTest.kt:52](/Users/serendipity/projects/meetjyou-backend-tasks/20261126-external-call-db-connection/src/test/java/com/dogGetDrunk/meetjyou/auth/social/kakao/KakaoLoginTimeoutIntegrationTest.kt:52) · [HeldResponseServer.kt:22](/Users/serendipity/projects/meetjyou-backend-tasks/20261126-external-call-db-connection/src/test/java/com/dogGetDrunk/meetjyou/auth/social/kakao/HeldResponseServer.kt:22) · [KakaoOidcProperties.kt:18](/Users/serendipity/projects/meetjyou-backend-tasks/20261126-external-call-db-connection/src/main/java/com/dogGetDrunk/meetjyou/auth/social/kakao/KakaoOidcProperties.kt:18)
- **Impact** If Spring stops applying the 200 ms override, the default timeout can still produce the expected 503 before the server sends its response.

### V-r1-04 The completion record omits both gate results

- **Kind** An accepted critique item not implemented.
- **Decision** `C-r1-05`. `spec.md:138` requires “두 실행 결과를 `result.md` 에 기록” for `./gradlew test build` and `./gradlew dockerTest`.
- **Grounds** The gate section is empty. Existing XML reports in the checkout show 888 `test` cases and 176 `dockerTest` cases with zero failures, errors, or skips. [result.md:15](/Users/serendipity/projects/meetjyou-backend-tasks/20261126-external-call-db-connection/.pipeline/tasks/20261126-external-call-db-connection/result.md:15)
- **Impact** The task record lacks the required evidence for the build gate and the separate MySQL suite.

### V-r1-05 The completion record omits the Apple revocation follow-up

- **Kind** An accepted critique item not implemented.
- **Decision** `C-r1-06` and `REQ-008`. `spec.md:105` requires recording that the two excluded routes “Apple 연동 해제 호출 동안 OSIV로 커넥션을 잡는다는 점과 제외 근거(사용자 결정 2026-11-26)”.
- **Grounds** The follow-up section is empty. It omits `DELETE /users/me`, `DELETE /users/me/social-accounts/APPLE`, the remaining connection hold, and the approved exclusion rationale. [result.md:19](/Users/serendipity/projects/meetjyou-backend-tasks/20261126-external-call-db-connection/.pipeline/tasks/20261126-external-call-db-connection/result.md:19)
- **Impact** The completion record leaves out the known connection-pool exposure during Apple revocation.