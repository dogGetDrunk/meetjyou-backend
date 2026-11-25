# Spec

## 전제

- OSIV(`OpenInViewConfig`)는 `HandlerInterceptor` 단계에서 EntityManager를 묶고, 요청에서 처음 얻은 커넥션을 요청 끝까지 유지(`DELAYED_ACQUISITION_AND_HOLD`). 필터(JWT 인증 등)는 인터셉터 앞에서 돌아 이 유지 대상이 아님
- 따라서 "외부 호출 중 커넥션 0"은 두 조건이 모두 맞아야 성립
  1. 외부 호출 시점에 트랜잭션이 열려 있지 않음
  2. 그 요청이 OSIV 아래에서 외부 호출 전에 DB를 쓰지 않았거나, 그 경로가 OSIV에서 제외됨
- 로그인, 소셜 연결은 검증이 요청의 첫 DB 작업보다 앞서므로 1번만 고치면 됨. 이미지 경로는 DB 작업이 OCI 호출보다 앞서므로 1, 2번 모두 필요
- 성공 판정은 외부 호출 대역(fake, mock) 안에서 읽은 Hikari `activeConnections`. 구조 추론이 아니라 이 값으로 고정

## 요구사항

### REQ-001 로그인 검증을 트랜잭션 밖으로

- 요구사항
  - `UserAuthService.loginViaSocial` 의 `@Transactional` 제거. 순서: `socialTokenOf` → `verifyAndExtract`(트랜잭션 없음) → 쓰기 트랜잭션 하나에서 사용자 조회, `restoreIfWithdrawn`, `tokenPairIssuer.issue`
  - 트랜잭션은 주입받은 `TransactionTemplate` 으로 실행 (`SocialRegistrationService.inTransaction` 과 같은 형태, 결과 null이면 `TransactionSystemException`)
  - 사용자 없음(404 `UserNotFoundException`), 유예 만료(`UserWithdrawnException`), 유예 중 로그인 시 복구 동작은 변경 전과 같음
- 수용 기준
  - Kakao, Google, Apple 각각 독립 케이스: 그 provider의 검증기가 정확히 1회 호출됐음을 먼저 확인하고, 그 호출 안의 기록값이 `TransactionSynchronizationManager.isActualTransactionActive() == false` 이고 Hikari `activeConnections == 0` (C-r1-01)
  - 응답 200과 토큰 쌍 발급, 기존 `UserAuthServiceTest`, `UserAuthServiceWithdrawalTest` 동작 유지
- 수용 테스트 ID: AT-001 (`SocialAuthConnectionIntegrationTest`, 새 파일)

### REQ-002 소셜 연결 검증을 트랜잭션 밖으로

- 요구사항
  - `SocialAccountService.link` 의 `@Transactional` 제거. 순서: `verifyAndExtract`(트랜잭션 없음) → 쓰기 트랜잭션 하나에서 `currentUserProvider.user` 이후 기존 로직 전부(`findOwnLinkedAccount`, provider 중복 검사, `addSocialAccount`, `flushNewLink`)
  - 트랜잭션은 주입받은 `TransactionTemplate` 으로 실행, REQ-001과 같은 null 처리
  - 동시 연결 경합에서 진 요청의 409(`SocialAccountAlreadyLinkedException`)와 재시도 200 동작은 변경 전과 같음 (`SocialAccountConcurrencyDockerTest` 통과)
  - `getMySocialAccounts`, `unlink` 는 변경하지 않음
- 수용 기준
  - Kakao, Google, Apple 각각 독립 케이스(연결 대상 provider와 다른 provider로 가입한 유저): 그 provider의 검증기가 정확히 1회 호출됐음을 먼저 확인하고, 호출 안의 기록값이 트랜잭션 없음, `activeConnections == 0` (C-r1-01)
  - 새 연결 201, 같은 계정 재연결 200
- 수용 테스트 ID: AT-002 (`SocialAuthConnectionIntegrationTest`)

### REQ-003 Kakao REST 호출 timeout

- 요구사항
  - `KakaoOidcProperties` 에 `connectTimeout: Duration = 3s`, `readTimeout: Duration = 5s` 추가 (기본값 있는 생성자 파라미터, `application.yml` 에는 넣지 않음)
  - `KakaoOidcConfig` 에 `@Bean @Qualifier("kakaoRestClient") RestClient` 추가: 주입된 `RestClient.Builder`(prototype)에 위 timeout의 `SimpleClientHttpRequestFactory` 를 설정해 build (`TourApiConfig`, `AppleOidcConfig` 와 같은 방식)
  - `KakaoVerifier` 생성자는 `RestClient.Builder` 대신 `@Qualifier("kakaoRestClient") RestClient` 를 받음. 테스트는 `MockRestServiceServer.bindTo(builder)` 로 만든 builder를 build해서 넘김
  - timeout 초과는 기존 경로 그대로: `ResourceAccessException` → `ExternalServiceException(SOCIAL_PROVIDER_UNAVAILABLE)` → 503
- 수용 기준
  - 기본값이 connect 3s, read 5s
  - 로컬 HTTP 서버(JDK `com.sun.net.httpserver.HttpServer`)는 요청을 받으면 수신 latch를 내리고, 테스트가 풀어 줄 때까지(상한 5s) 응답을 보내지 않음. read timeout 200ms (C-r1-02)
  - 단위(`KakaoRestClientTimeoutTest`): `KakaoOidcConfig.kakaoRestClient` 로 만든 클라이언트로 access token 검증 → 수신 latch가 내려갔고(연결 실패가 아니라 응답 대기 중 timeout), `ExternalServiceException`, `errorCode == SOCIAL_PROVIDER_UNAVAILABLE`, 1s 안에 실패. 종료 시 보류한 응답을 풀고 서버를 상한 있는 대기로 정리
  - HTTP(`KakaoLoginTimeoutIntegrationTest`): 실제 `KakaoVerifier` 를 쓰는 컨텍스트에서 Kakao URI를 위 서버로, read timeout을 200ms로 주입(`@DynamicPropertySource` 또는 같은 효과의 방식)하고 `POST /api/v1/auth/login`(Kakao, accessToken) → 503, 본문 `errorCode == SOCIAL_PROVIDER_UNAVAILABLE`, 수신 latch 내려감 (C-r1-02)
- 수용 테스트 ID: AT-003 (`KakaoRestClientTimeoutTest`, `KakaoLoginTimeoutIntegrationTest`, 새 파일)

### REQ-004 이미지 경로 OSIV 제외

- 요구사항
  - `OpenInViewConfig.EXCLUDED_PATHS` 에 `OracleObjectStorageController` 의 핸들러 12개만 잡는 패턴 추가 (`V1` 접두사 포함)
    - `/users/*/img/profile`, `/users/*/img/profile/**`, `/users/img/**`
    - `/posts/*/img`, `/posts/*/img/par/**`, `/posts/*/img/original/**`, `/posts/img/**`
    - `/parties/*/img`, `/parties/*/img/par/**`, `/parties/*/img/original/**`, `/parties/img/**`
    - `PUT /parties/{partyUuid}/img/confirm`(`PartyController`)은 OCI를 부르지 않으므로 잡히면 안 됨. `/parties/*/img/**` 같은 넓은 패턴 금지
  - KDoc 갱신: 제외 기준 = "DB 작업 뒤 외부 API를 부르는 경로", 제외된 경로는 트랜잭션 밖 지연 로딩이 실패하므로 DB 작업을 트랜잭션 안에서 끝내야 한다는 점, 고정 테스트 이름
- 수용 기준
  - `RequestMappingHandlerMapping` 의 모든 핸들러에 대해: OSIV 인터셉터가 빠진 핸들러 = `OracleObjectStorageController` 의 핸들러 전부 + `PlaceController` 의 `/places/search`. 그 밖은 전부 OSIV 유지
  - 경로 변수(`{...}`)는 임의 UUID로 치환해 판정, HTTP 메서드는 매핑의 메서드 사용
- 수용 테스트 ID: AT-004 (`OpenInViewExclusionTest`, 새 파일. 기존 `PlaceSearchTourApiIntegrationTest` 의 OSIV 단언은 그대로 둠)

### REQ-005 파티 이미지 다운로드 해석을 별도 빈으로

- 요구사항
  - `PartyService.resolvePartyOriginalImageDownload`, `resolvePartyThumbnailImageDownloads`, `resolveThumbnail` 을 새 빈 `PartyImageDownloadService`(`party` 패키지)로 이동. `OracleObjectStorageController` 는 새 빈을 호출
    - 근거: `PartyService` 를 직접 생성하는 테스트가 10개라 생성자에 트랜잭션 템플릿을 추가하면 변경이 넓어짐. 이미지 해석은 `PartyService` 의 다른 로직과 상태를 공유하지 않음
  - 새 빈: 클래스와 메서드에 `@Transactional` 없음. 읽기 전용 `TransactionTemplate`(`isReadOnly = true`, `PlatformTransactionManager` 로 생성) 안에서 DB를 읽어 "어느 객체의 이미지를 쓸지"(파티 자체, 연결 모집글, 없음)만 값으로 계산하고, 트랜잭션이 끝난 뒤 OCI PAR 발급
    - 원본: `requireParty` → `imageState` 에 따라 파티 uuid, `postRepository.findByParty_Uuid` 의 모집글 uuid, 또는 null. 파티 없음은 `PartyNotFoundException`(404) 그대로
    - 썸네일: `partyRepository.findAllByUuidIn` 을 먼저, 그다음 `postRepository.findAllByParty_UuidIn` (같은 영속성 컨텍스트라 `post.party.uuid` 접근이 추가 쿼리 없이 해결됨). 입력 순서와 길이를 유지한 `List<ParResponse?>`, 없는 파티와 `NONE` 은 null
  - 쿼리 수는 변경 전과 같음: 원본 최대 2개, 썸네일 2개 (반복문 안 쿼리 없음)
  - `resolvePartyThumbnailImageDownloads` 위의 틀린 주석("트랜잭션이 없으니 커넥션을 잡지 않는다") 삭제, 새 빈 KDoc에 OSIV 제외와 함께 써야 성립한다는 점 명시
  - 기존 테스트 이전: `PartyImageAndPlanTest` 의 원본 다운로드 단언, `PartyQueryOptimizationTest` 의 썸네일 트랜잭션 경계와 쿼리 수 단언을 새 빈 대상으로 옮김 (단언 내용은 유지)
- 수용 기준
  - CUSTOM → 파티 PAR, INHERITED → 모집글 PAR, NONE → null(204), 없는 파티 → 404, 썸네일 혼합 목록의 순서와 null 위치 유지
  - INHERITED인데 연결 모집글이 없는 파티(모집글 삭제는 파티를 지우지 않음): 현재 동작 유지 = 원본 null(204), 썸네일 그 자리 null, 두 경우 모두 OCI 호출 0회 (C-r1-03)
  - OCI 호출 중 `activeConnections == 0` (REQ-006)
- 수용 테스트 ID: AT-005 (`PartyImageDownloadServiceTest`, 새 파일, 옮긴 단언 포함)

### REQ-006 이미지 경로 외부 호출 중 커넥션 0

- 요구사항: REQ-004, REQ-005 적용 후 아래 경로가 OCI 호출 중 커넥션을 잡지 않음. 그 밖의 서비스 코드는 이미 트랜잭션 안에서 DB 작업을 끝내므로(`assertCurrentUserIsAuthor`, `assertCurrentUserIsHost`, `clearPartyImageState`, `clearProfileImage` 는 각자 `@Transactional`) 변경 없음
- 수용 기준: `ObjectStorageClient` 를 `@MockBean` 으로 두고 `createPreauthenticatedRequest`, `deleteObject` 응답 안에서 `activeConnections` 와 트랜잭션 활성 여부를 기록. 아래 경로 전부 `activeConnections == 0`, 트랜잭션 없음, 응답 상태 변경 전과 같음
  - `POST /posts/{uuid}/img/par/upload` (작성자), `POST /parties/{uuid}/img/par/upload` (호스트)
  - `POST /parties/{uuid}/img/original/par/download` (CUSTOM, INHERITED 각각)
  - `POST /parties/img/thumbnail/par/download` (CUSTOM과 INHERITED 섞은 2건 이상)
  - `DELETE /posts/{uuid}/img`, `DELETE /parties/{uuid}/img`, `DELETE /users/me/img/profile`
  - 다운로드 PAR 캐시(`downloadParCache`) 적중을 피하려고 매 케이스 새 UUID 사용
  - 기록은 호출마다 하나씩 쌓는 목록(마지막 값만 남기지 않음). 각 케이스는 먼저 기대한 OCI 작업 종류와 대상 객체 이름 목록, 호출 횟수가 정확히 맞는지 확인한 뒤, 모든 기록에 대해 커넥션 0과 트랜잭션 없음을 단언. 빈 기록 목록으로 통과할 수 없음 (C-r1-04)
    - 예: 업로드는 원본과 썸네일 2회, 썸네일 일괄은 선택된 파티, 모집글 썸네일 객체마다 1회, 삭제는 원본과 썸네일 `deleteObject` 2회
- 수용 테스트 ID: AT-006 (`ImageParConnectionIntegrationTest`, 새 파일)

### REQ-007 규칙 문서

- 요구사항
  - `.claude/rules/auth.md`: 소셜 검증은 트랜잭션 밖(로그인, 연결, 가입 모두), Kakao REST 3s/5s timeout
  - `.claude/rules/party.md`: 파티 이미지 다운로드 해석은 `PartyImageDownloadService`, OSIV 제외 경로라 DB 작업은 트랜잭션 템플릿 안에서 끝냄
  - `OpenInViewConfig` KDoc이 제외 목록의 정본. 규칙 한 줄을 CLAUDE.md가 아닌 KDoc에 둠: "DB 작업 뒤 외부 API를 부르는 경로"만 제외하고 커넥션 0 테스트를 붙임. 외부 호출이 첫 DB 작업보다 앞서는 경로는 제외하지 않음(커넥션을 잡지 않으면서 이후 지연 로딩만 깨짐) (C-r1-07)
- 수용 기준: 문서 변경, 테스트 없음

### REQ-008 완료 기록

- 요구사항: `result.md` 후속 항목에 intent에서 제외한 두 경로(`DELETE /users/me`, `DELETE /users/me/social-accounts/APPLE`)가 Apple 연동 해제 호출 동안 OSIV로 커넥션을 잡는다는 점과 제외 근거(사용자 결정 2026-11-26)를 기록 (C-r1-06)
- 수용 기준: `result.md` 에 두 경로와 남은 위험이 적혀 있음

## 확정 인터페이스

### API 시그니처·DTO

- HTTP API 변경 없음 (경로, 요청, 응답, 상태 코드 동일)
- `UserAuthService(socialVerifierRegistry, userRepository, jwtProvider, refreshTokenRepository, tokenPairIssuer, transactionTemplate: TransactionTemplate, rotationOverlapSeconds)`
- `SocialAccountService(socialVerifierRegistry, userRepository, currentUserProvider, transactionTemplate: TransactionTemplate)`
- `KakaoVerifier(props, kakaoJwtDecoder, meterRegistry, @Qualifier("kakaoRestClient") restClient: RestClient)`
- `KakaoOidcProperties(..., connectTimeout: Duration = Duration.ofSeconds(3), readTimeout: Duration = Duration.ofSeconds(5))`
- `PartyImageDownloadService(partyRepository, postRepository, partyImgService, postImgService, transactionManager: PlatformTransactionManager)`
  - `fun resolveOriginal(partyUuid: UUID): ParResponse?`
  - `fun resolveThumbnails(partyUuids: List<UUID>): List<ParResponse?>`
- 생성자를 바꾼 클래스를 직접 생성하는 기존 테스트(`UserAuthServiceTest`, `UserAuthServiceWithdrawalTest`, `SocialAccountServiceTest`, `KakaoVerifierTest`, dockerTest의 `SocialAccountConcurrencyDockerTest`, `RefreshTokenRotationConcurrencyDockerTest`)는 생성자 인자만 맞춤. 단위 테스트는 `SocialRegistrationServiceTest` 처럼 `TransactionTemplate(transactionManager)` 사용

### DB 스키마

- 변경 없음

## 테스트 공통

- 새 통합 테스트는 `PlaceSearchTourApiIntegrationTest` 형식: `@SpringBootTest(RANDOM_PORT)`, H2 전용 URL, `@ActiveProfiles("test")`, Hikari 풀은 `dataSource.unwrap(HikariDataSource::class.java)`
- AT-001, AT-002: 세 검증기(`KakaoVerifier`, `GoogleVerifier`, `AppleVerifier`)를 `@MockBean` 으로 두고, 응답 안에서 트랜잭션 여부와 `activeConnections` 기록. 검증기가 mock이라 토큰 종류와 nonce는 검사되지 않음, 요청은 `accessToken` 만 채움
- 한 케이스의 기록값은 매 케이스 전에 초기화

## 마일스톤

1. REQ-003 (Kakao timeout) + AT-003
2. REQ-001, REQ-002 + AT-001, AT-002, 기존 테스트 생성자 맞춤
3. REQ-005 + AT-005, 기존 단언 이전
4. REQ-004, REQ-006 + AT-004, AT-006
5. REQ-007 문서, `agent-pipeline gate`(`./gradlew test build`)와 별도로 `./gradlew dockerTest` 전체 실행, 두 실행 결과를 `result.md` 에 기록 (C-r1-05)
6. REQ-008 `result.md`
