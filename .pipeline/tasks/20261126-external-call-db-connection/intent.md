# Intent

> 메인 설계 에이전트가 사람과 대화로 작성.
> 승인 기록은 `approval.json`(`agent-pipeline approve-intent`), 승인 후 이 파일 수정 금지.

## 해결할 문제

외부 서버(Kakao, OCI Object Storage)를 호출하는 동안 Hikari 커넥션을 붙잡는 요청 경로가 있음. 외부 서버가 느려지면 이 요청들이 풀(50개, `connection-timeout` 5s)을 고갈시켜 DB를 쓰는 모든 API가 실패함. 외부 장애가 서비스 전체 장애로 번지는 구조.

- 로그인, 소셜 연결 (#298)
  - `UserAuthService.loginViaSocial`, `SocialAccountService.link` 가 `@Transactional` 안에서 `verifyAndExtract` 호출 → 트랜잭션 시작 시점에 커넥션 확보, 외부 검증 동안 점유
  - Kakao access token 검증 `RestClient`(`KakaoVerifier`)에 timeout 미지정 → Kakao가 응답하지 않으면 무기한 대기
  - 회원가입(`SocialRegistrationService.register`)은 이미 트랜잭션 밖에서 검증 중
- 이미지 PAR 발급과 삭제 (#299)
  - 프로젝트는 OSIV를 켜 둔 상태(`OpenInViewConfig`, 제외는 `/places/search` 하나). OSIV는 요청에서 처음 얻은 커넥션을 요청 끝까지 유지(`DELAYED_ACQUISITION_AND_HOLD`)
  - 그래서 DB 조회 뒤 OCI를 호출하는 `OracleObjectStorageController` 경로는 트랜잭션을 짧게 나눠도 OCI 호출 내내 커넥션을 잡음
    - 파티 원본 다운로드: `@Transactional(readOnly = true)` 안에서 OCI 호출
    - 파티 썸네일 일괄 다운로드(최대 50건): "트랜잭션이 없으니 커넥션을 안 잡는다"는 주석과 달리 OSIV로 점유
    - 모집글, 파티 업로드 PAR와 이미지 삭제: 작성자, 호스트 확인(DB) 뒤 OCI 호출

## 의도

외부 호출이 진행되는 동안 그 요청이 DB 커넥션을 하나도 잡고 있지 않게 만들고, 이 성질을 테스트로 고정함. 응답 형식과 상태 코드는 바꾸지 않음.

## 포함 범위

- 로그인, 소셜 연결: 소셜 토큰 검증을 트랜잭션 밖으로 분리 (회원가입과 같은 구조: 검증 → 짧은 트랜잭션)
- Kakao `RestClient` 에 명시적 timeout (connect 3s, read 5s, `AppleOidcConfig` 와 같은 값)
  - timeout 초과는 기존 `ResourceAccessException` → `ExternalServiceException`(503 `SOCIAL_PROVIDER_UNAVAILABLE`) 경로를 그대로 탐
- `OracleObjectStorageController` 의 OCI 호출 경로 전부를 OSIV 제외 목록에 추가 (ADR-0004의 `/places/search` 선례와 같은 방식)
  - 대상: 업로드 PAR 3개, 다운로드 PAR 6개, 이미지 삭제 3개
  - 제외된 경로의 DB 작업은 짧은 트랜잭션 안에서 끝내고 필요한 값만 꺼낸 뒤 OCI 호출 (OSIV가 없어지면 트랜잭션 밖 지연 로딩이 실패하므로 함께 정리)
- 외부 호출 시점에 활성 커넥션 0을 단언하는 통합 테스트 (`PlaceSearchTourApiIntegrationTest` 선례)
  - 로그인, 소셜 연결 각 1개 이상, 이미지 경로는 DB 작업이 앞서는 경로 전부
- `OpenInViewConfig` KDoc, 틀린 주석(`resolvePartyThumbnailImageDownloads`), `.claude/rules/` 의 해당 모듈 규칙 갱신

## 제외 범위

- OSIV 전역 비활성화: 모든 컨트롤러의 지연 로딩 동작이 바뀌어 영향 범위가 이 태스크보다 훨씬 큼
- Google 검증기 timeout 조정: 검증이 트랜잭션 밖으로 나가면 커넥션을 잡지 않음. SDK 기본값(20s)은 그대로 둠
- Kakao JWKS 조회 timeout: 24시간 캐시와 30초 rate limit이 이미 있어(`KakaoJwkSource`) 실제 호출 빈도가 매우 낮음
- 파티장 탈퇴, 이미지 업로드 흐름 자체의 기능 변경
- 회원 탈퇴, Apple 연결 해제 경로(`DELETE /users/me`, `DELETE /users/me/social-accounts/APPLE`): 아래 결정에 따라 제외, `result.md` 후속 항목으로 기록

## 사람 결정

1. **회원 탈퇴, Apple 연결 해제 경로 → 제외 (2026-11-26 사용자 승인, 제안 채택)**
   - 같은 결함: `AccountExitService` 는 DB 커밋 뒤 Apple 해제를 호출하지만, OSIV 때문에 Apple 호출(3s/5s timeout) 동안 커넥션을 잡음
   - 근거: 탈퇴는 하루 몇 건 수준이고 timeout이 이미 있어 풀 고갈 위험이 거의 없음. 넣으면 탈퇴 흐름(유예, 복구, 알림) 회귀 테스트까지 범위가 커짐
   - 기각: 포함(두 경로도 OSIV 제외 + 활성 커넥션 0 테스트)

## 성공 기준

- Kakao, Google, Apple 로그인과 소셜 연결: 검증기 호출 중 Hikari `activeConnections == 0` 을 테스트로 단언
- 이미지 경로: OCI 클라이언트 호출 중 `activeConnections == 0` 을 DB 작업이 앞서는 경로마다 테스트로 단언
- Kakao access token 검증이 read timeout을 넘기면 503 `SOCIAL_PROVIDER_UNAVAILABLE` (테스트로 확인)
- 대상 엔드포인트의 응답 본문과 상태 코드가 변경 전과 같음 (기존 테스트 통과)
- `OpenInViewConfig` 가 의도한 경로만 제외하고 나머지는 OSIV 유지 (테스트로 고정)
- `./gradlew build` 전체 통과(detekt, `ConventionTest` 포함)와 `./gradlew dockerTest` 통과

## 작업 크기

M — 기능 하나(외부 호출 중 커넥션 점유 제거)지만 인증과 이미지 두 모듈, OSIV 설정, 통합 테스트에 걸쳐 있음

## 출처

- 노션 태스크: [외부 서버 호출 중 DB 연결 점유 해소 (카카오 로그인, 이미지 주소 발급)](https://app.notion.com/p/42156d9a97d98189bbffdfbed1a26e1d) (작업 ID 247)
- GitHub 이슈: #298, #299
