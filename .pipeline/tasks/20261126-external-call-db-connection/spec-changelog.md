# Spec 변경 로그

| 일시 | 요구사항 ID | 변경 | 이유 | 메인 설계자 |
|---|---|---|---|---|
| 2026-11-26 | REQ-001, REQ-002 | provider별 독립 케이스와 검증기 1회 호출 확인 요구 | C-r1-01 | Claude Opus 5.5 |
| 2026-11-26 | REQ-003 | 수신 latch와 응답 보류, HTTP 503 통합 테스트(`KakaoLoginTimeoutIntegrationTest`) 추가 | C-r1-02 | Claude Opus 5.5 |
| 2026-11-26 | REQ-005 | INHERITED이고 모집글 없는 파티의 원본과 썸네일 결과, OCI 0회 고정 | C-r1-03 | Claude Opus 5.5 |
| 2026-11-26 | REQ-006 | 호출별 기록 목록, 기대 작업과 대상과 횟수 확인 후 전 기록 단언 | C-r1-04 | Claude Opus 5.5 |
| 2026-11-26 | 마일스톤 | `./gradlew dockerTest` 전체 실행과 결과 기록 추가 | C-r1-05 | Claude Opus 5.5 |
| 2026-11-26 | REQ-008 | Apple 해제 경로 후속 기록 요구사항 신설 | C-r1-06 | Claude Opus 5.5 |
| 2026-11-26 | REQ-007 | OSIV 제외 규칙을 DB 작업 뒤 외부 호출 경로로 한정 | C-r1-07 | Claude Opus 5.5 |
| 2026-11-26 | REQ-005 | 별도 빈 근거 정정, `PartyService` 생성자에서 `PartyImgService`, `PostImgService` 제거와 테스트 17곳 인자 수정 명시 | 구현 중 확인: 옮기고 나면 두 의존이 쓰이지 않아 detekt 미사용 경고 대상, 생성자 변경은 피할 수 없음 | Claude Opus 5.5 |
| 2026-11-26 | 테스트 공통 | AT-001/002, AT-004, AT-006 세 클래스가 같은 컨텍스트 설정(`support/ExternalCallTestConfig`, H2 URL `external_call_connection`, `@MockBean` 4종)을 공유. 검증기 fake는 mockk가 아닌 `SocialVerifierRegistry` 하위 클래스 | 클래스마다 컨텍스트를 만들면 테스트 JVM OOM. mockk stub은 다른 단위 테스트의 `clearAllMocks()` 에 지워짐 | Claude Opus 5.5 |
| 2026-11-26 | 테스트 공통 | `build.gradle.kts` 테스트 태스크 `maxHeapSize = "1g"` | Gradle 기본 512m에서 컨텍스트 캐시 증가로 OOM. 기각: 컨텍스트 캐시 축소(공유 H2 스키마 삭제 연쇄 위험), 수용 테스트 축소(C-r1-02 수용분) | Claude Opus 5.5 |
| 2026-11-26 | REQ-001 | `UserAuthService` 생성자 7개로 detekt `LongParameterList` 신규 → 근거 주석과 `@Suppress`. 기존 baseline 항목 2개(`PartyService`, `OracleObjectStorageController`)는 바뀐 시그니처로 ID만 교체, 항목 수 329 유지 | baseline 추가 금지 규칙. 기각: 소셜 로그인 별도 서비스 분리(인증 모듈 테스트와 규칙까지 범위 확대) | Claude Opus 5.5 |
| 2026-11-26 | 테스트 공통 | AT-001/002 fake를 레지스트리 하위 클래스에서 provider별 검증기 하위 클래스(`@Primary` 빈 3개)로 변경, 로그인 토큰 쌍 단언, AT-003 HTTP 경과 시간 2s 미만 단언 | V-r1-01, V-r1-02, V-r1-03 | Claude Opus 5.5 |
