# 결과

> 태스크 PR 전에 태스크 세션이 작성. 포트폴리오와 회고 재료.

## 결과 요약

- 외부 서버(Kakao, Google, Apple 검증, OCI Object Storage) 호출 중 요청이 Hikari 커넥션을 잡지 않게 수정 (#298, #299, 노션 247)
- 결함 재현: 수정 전 구조로 되돌리면 DB 선행 이미지 경로 7개 모두 OCI 호출 중 `activeConnections == 1`, 로그인 3개 provider 모두 검증 중 트랜잭션 활성 → 수정 후 전부 0
- Kakao REST 무기한 대기 제거: connect 3s, read 5s, 초과 시 503 `SOCIAL_PROVIDER_UNAVAILABLE`
- HTTP API 형식과 상태 코드 변경 없음

## 변경 내용

- 로그인, 소셜 연결 (`UserAuthService.loginViaSocial`, `SocialAccountService.link`)
  - `@Transactional` 제거, 소셜 토큰 검증 → `TransactionTemplate` 한 번으로 계정 작업 (회원가입과 같은 구조)
- Kakao timeout
  - `KakaoOidcProperties.connectTimeout`(3s), `readTimeout`(5s), `KakaoOidcConfig.kakaoRestClient` 빈, `KakaoVerifier` 는 이 클라이언트를 주입받음
- 이미지 경로 OSIV 제외
  - `OpenInViewConfig` 에 `OracleObjectStorageController` 핸들러 12개만 잡는 패턴 추가, `PUT /parties/{uuid}/img/confirm` 은 OSIV 유지
  - KDoc에 제외 기준 명시: DB 작업 뒤 외부 호출하는 경로만 제외, 외부 호출이 먼저인 경로는 제외하지 않음
- 파티 이미지 다운로드 해석
  - 새 빈 `PartyImageDownloadService`: 읽기 전용 `TransactionTemplate` 안에서 이미지 출처만 값으로 계산 → 트랜잭션 종료 후 PAR 발급
  - `PartyService` 에서 OCI 의존(`PartyImgService`, `PostImgService`) 제거, 틀린 "트랜잭션 없으니 커넥션 안 잡음" 주석 삭제
- 테스트
  - 새 수용 테스트: `KakaoRestClientTimeoutTest`, `KakaoLoginTimeoutIntegrationTest`(AT-003), `SocialAuthConnectionIntegrationTest`(AT-001, AT-002), `PartyImageDownloadServiceTest`(AT-005), `OpenInViewExclusionTest`(AT-004), `ImageParConnectionIntegrationTest`(AT-006)
  - AT-001/002, AT-004, AT-006은 같은 컨텍스트 설정(`support/ExternalCallTestConfig`) 공유
  - 생성자 변경에 맞춰 기존 테스트 인자 수정: `PartyService` 17곳, `UserAuthService` 와 `SocialAccountService` 5곳, `KakaoVerifierTest`
  - `build.gradle.kts` 테스트 태스크 `maxHeapSize = "1g"`
- 문서: `.claude/rules/auth.md`(검증은 트랜잭션 밖, Kakao timeout), `.claude/rules/party.md`(이미지 경로 OSIV 제외와 `PartyImageDownloadService`)
- detekt: 기존 baseline 항목 2개(`PartyService`, `OracleObjectStorageController`)는 바뀐 시그니처로 ID만 교체(항목 수 329 유지), `UserAuthService` 생성자 7개는 근거 주석과 `@Suppress("LongParameterList")`

## 비평 결정

- 1라운드, gpt-6.2-sol(다른 벤더), 7건 전부 수용
  - C-r1-01 provider별 독립 케이스, C-r1-02 응답 보류 중 timeout 증명과 HTTP 503 테스트, C-r1-03 모집글 없는 INHERITED 파티 동작 고정
  - C-r1-04 OCI 호출별 기록과 기대 호출 확인, C-r1-05 `dockerTest` 전체 실행, C-r1-06 Apple 해제 경로 후속 기록, C-r1-07 OSIV 제외 기준을 DB 선행 경로로 한정

## 구현 후 검증

- 1라운드, 검증 커밋 `153f8e4`, gpt-6.2-sol, 5건 전부 수용, 실행 실패와 재시도 없음
  - V-r1-01 fake가 레지스트리 선택을 건너뜀 → provider별 검증기 하위 클래스로 교체, 레지스트리 오배정 시 실패를 RED로 확인
  - V-r1-02 로그인 토큰 쌍 단언 추가
  - V-r1-03 HTTP timeout 테스트에 경과 시간 2s 미만 단언 (기본 5s와 구분)
  - V-r1-04, V-r1-05 이 문서의 gate 근거와 후속 작업 기록

## gate 근거

- `agent-pipeline gate` (`./gradlew test build`, detekt와 `ConventionTest` 포함): exit 0, `BUILD SUCCESSFUL in 1m 31s`, test 888건 실패 0, 건너뜀 0 (커밋 `50e76e1`)
- `./gradlew dockerTest` (Testcontainers MySQL): `BUILD SUCCESSFUL in 3m 17s`, 176건 실패 0, 건너뜀 0 (커밋 `b3c7633` 기준, 이후 변경은 `src/test` 와 태스크 문서뿐이라 dockerTest 대상 코드 동일)
- `origin/main`(#306 관광공사 한도 태스크) 병합 후 `agent-pipeline gate` 재실행: exit 0, `BUILD SUCCESSFUL in 1m 38s`, test 898건 실패 0, 건너뜀 0
- RED 확인 3건: 로그인 `@Transactional` 복원 시 3건 실패, 이미지 경로 OSIV 제외 제거 시 7건 실패(커넥션 1), 넓은 패턴(`/parties/*/img/**`) 시 `PUT .../img/confirm` 에서 실패

## 핵심 결정과 기각한 대안

- 이미지 경로: OSIV 경로 제외 + DB 작업을 트랜잭션 안에서 끝내기
  - 기각: OSIV 전역 끄기 (모든 컨트롤러의 지연 로딩 동작이 바뀌어 영향 범위가 태스크보다 큼)
  - 근거: ADR-0004의 `/places/search` 제외와 같은 방식, 결함 범위가 `OracleObjectStorageController` 한 곳
- 제외 패턴은 넓은 `/parties/*/img/**` 대신 핸들러별 좁은 패턴
  - OCI를 부르지 않는 `PUT .../img/confirm` 까지 OSIV에서 빠지면 지연 로딩 위험만 생김. 전 핸들러 대상 테스트로 고정
- 파티 이미지 해석을 별도 빈으로 분리
  - 기각: `PartyService` 에 `TransactionTemplate` 추가 (이미지 해석은 다른 로직과 상태를 공유하지 않고, 분리하면 `PartyService` 가 OCI 의존을 완전히 버림)
- Kakao timeout은 Apple, 관광공사와 같은 3s/5s
  - 기각: Google 검증기 timeout 조정 (검증이 트랜잭션 밖으로 나가 커넥션과 무관, SDK 기본 20s 유지), Kakao JWKS timeout (24시간 캐시와 30s rate limit으로 호출 빈도 낮음)
- 테스트 JVM 힙 1g
  - 원인: 새 컨텍스트 추가로 Gradle 기본 512m에서 OOM(27회). 컨텍스트 공유로 4개 → 2개로 줄인 뒤에도 남음
  - 기각: 컨텍스트 캐시 크기 축소 (기본 H2 DB를 공유하는 컨텍스트가 닫히며 스키마를 지우는 연쇄 실패 위험), 수용 테스트 축소
- `UserAuthService` `LongParameterList` 는 `@Suppress`
  - 기각: baseline 추가(프로젝트 규칙 위반), 소셜 로그인 별도 서비스 분리(인증 모듈 테스트와 규칙까지 범위 확대). 분리는 후속 후보
- 공유 컨텍스트의 fake는 mockk가 아닌 하위 클래스
  - 다른 단위 테스트의 `clearAllMocks()` 가 캐시된 컨텍스트 안 mockk stub을 지워 전체 실행에서만 실패했음

## 남은 후속 작업

- **회원 탈퇴, Apple 연결 해제 경로의 커넥션 점유** (REQ-008, intent에서 제외)
  - `DELETE /users/me`, `DELETE /users/me/social-accounts/APPLE`: `AccountExitService` 가 DB 커밋 뒤 Apple 연동 해제를 호출하지만, OSIV 때문에 Apple 호출(3s/5s timeout) 동안 요청이 커넥션 1개를 계속 잡음
  - 제외 근거(사용자 결정 2026-11-26): 탈퇴는 하루 몇 건 수준이고 timeout이 있어 풀 고갈 위험이 거의 없음. 넣으면 탈퇴 흐름 회귀 테스트까지 범위가 커짐
  - 해소 방법: 두 경로를 OSIV에서 제외하고 Apple 호출 중 커넥션 0 테스트 추가 (`OpenInViewConfig` KDoc 규칙대로)
- `UserAuthService` 에서 소셜 로그인 분리 검토 (생성자 7개, `@Suppress` 제거 가능)
- 운영 확인(사람): 배포 후 로그인과 이미지 PAR 응답 지연, Hikari active 지표에 이상 없는지
