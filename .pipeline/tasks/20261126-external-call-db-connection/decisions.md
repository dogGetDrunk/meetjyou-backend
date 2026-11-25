# Spec 비평 결정

> 결정 값: `수용`, `기각-오판`(비평 전제가 틀림), `기각-범위`(intent 제외 범위), `기각-감수`(알려진 위험이나 결함을 남김, 고치는 대신 문서화 포함).
> `기각-범위`, `기각-감수`는 사람에게 묻고 `사람 승인` 칸에 승인자와 발언 원문을 적은 뒤에만 기록. 분류 없는 `기각`과 빈 승인 칸은 엔진이 거부.

| 라운드 (최대 2) | 항목 | 수용/기각 | 근거 | 메인 설계자 | 사람 승인 |
|---|---|---|---|---|---|
| 1 | C-r1-01 | 수용 | AT-002에 provider별 케이스가 없어 한 provider로 통과 가능. AT-001, AT-002 모두 세 provider 독립 케이스와 검증기 1회 호출 확인을 먼저 요구하도록 수정 | Claude Opus 5.5 | - |
| 1 | C-r1-02 | 수용 | intent 성공 기준이 503 응답 자체를 요구하는데 spec은 예외만 검사. 수신 latch로 응답 보류 중 timeout임을 증명하고, 실제 KakaoVerifier로 `POST /api/v1/auth/login` 503과 `errorCode` 를 확인하는 HTTP 테스트 추가 | Claude Opus 5.5 | - |
| 1 | C-r1-03 | 수용 | 모집글 삭제는 파티를 지우지 않아 INHERITED인데 모집글 없는 파티가 실제로 존재. 현재 동작(원본 204, 썸네일 null, OCI 0회)을 수용 기준과 AT-005 케이스로 고정 | Claude Opus 5.5 | - |
| 1 | C-r1-04 | 수용 | 마지막 값만 남기는 기록이면 빈 기록이나 앞선 호출의 점유를 놓침. 호출별 기록 목록 + 기대 작업과 대상 객체와 횟수 확인 후 전 기록 단언으로 수정 | Claude Opus 5.5 | - |
| 1 | C-r1-05 | 수용 | 프로젝트 gate는 `./gradlew test build` 라 dockerTest가 빠짐. intent 성공 기준대로 마지막 마일스톤에 `./gradlew dockerTest` 전체 실행과 결과 기록 추가 | Claude Opus 5.5 | - |
| 1 | C-r1-06 | 수용 | intent가 Apple 해제 경로 제외를 후속 기록으로 요구. REQ-008로 `result.md` 기록 요구사항 추가 | Claude Opus 5.5 | - |
| 1 | C-r1-07 | 수용 | 외부 호출이 첫 DB 작업보다 앞서는 경로는 커넥션을 잡지 않으므로 제외 대상이 아님. KDoc 규칙을 "DB 작업 뒤 외부 호출" 경로로 한정 | Claude Opus 5.5 | - |

## 구현 후 검증 결정

> `logs/impl-verify-rN.md`의 `V-rN-NN` 항목. 값과 승인 규칙은 위와 같음. 구현을 결정에 맞게 고치면 `수용`, 이탈을 남기거나 결정을 코드에 맞춰 바꾸면 `기각-감수`(사람 승인).

| 라운드 (최대 2) | 항목 | 수용/기각 | 근거 | 메인 설계자 | 사람 승인 |
|---|---|---|---|---|---|
| 1 | V-r1-01 | 수용 | fake가 `SocialVerifierRegistry.get` 을 덮어써 운영 레지스트리의 provider 선택을 건너뜀. 세 검증기 빈을 각자 자기 provider를 기록하는 하위 클래스(`@Primary`)로 바꿔 운영 레지스트리가 고르게 함. 레지스트리에서 Google을 Kakao로 바꾸면 2건 실패함을 RED로 확인 | Claude Opus 5.5 | - |
| 1 | V-r1-02 | 수용 | 로그인 케이스가 응답 본문을 확인하지 않음. `accessToken`, `refreshToken` 비어 있지 않음 단언 추가 | Claude Opus 5.5 | - |
| 1 | V-r1-03 | 수용 | 서버 보류 상한과 기본 read timeout이 모두 5s라 주입한 200ms가 적용되지 않아도 503이 나올 수 있음. HTTP 테스트에 경과 시간 2s 미만 단언 추가 | Claude Opus 5.5 | - |
| 1 | V-r1-04 | 수용 | `result.md` 미작성 상태에서 검증이 돌았음. 최종 gate(`./gradlew test build`)와 `./gradlew dockerTest` 결과를 `result.md` 에 기록 | Claude Opus 5.5 | - |
| 1 | V-r1-05 | 수용 | REQ-008 후속 기록 누락. Apple 해제 두 경로와 남은 커넥션 점유, 제외 근거를 `result.md` 후속 항목에 기록 | Claude Opus 5.5 | - |
