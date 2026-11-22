# Result

## 바뀐 것

- `GET /places/search` 응답에 `tourPlaces`(관광공사 관광지, 문화시설, 레포츠)와 `tourStatus`(`OK`, `FAILED`, `DISABLED`) 추가. 기존 `regions`, `places` 불변
- 관광공사 `searchKeyword2` 실시간 호출(`place/tour/`), 응답은 저장하지 않음(ADR-0004)
  - 관광타입 12, 14, 28만 남김, 지역은 `lDongRegnCd + lDongSignguCd` → 우리 법정동 코드, 모르는 시군구는 시도로, 모르는 시도는 버림
  - 지역 필터는 API에 시도만 넘기고 결과를 `isWithin` 으로 다시 거름(일반구 포함)
  - 좌표가 숫자가 아니거나 0, NaN, Infinity면 버림
- 장애 처리: 타임아웃, 비 2xx, 게이트웨이 XML 오류(한도 초과 22 포함), `resultCode` 불일치, 예상 못한 예외 → 관광지만 비우고 200 + `FAILED`, warn 로그 1줄(사유, 걸린 ms)
- 서비스키 없음 → 호출 안 함, `DISABLED`. 운영에 키를 등록하기 전까지 검색 결과는 지금과 같음
- 서비스키 보호: 키는 URI 변수로 한 번만 인코딩, Spring 클라이언트 예외 메시지(요청 URI 포함)를 쓰지 않음, `TourApiProperties.toString` 마스킹, 코드 모양이 아닌 `resultCode` 는 옮기지 않음
- `PlaceSearchFacade`(트랜잭션 없음): DB 검색(검증 포함, 자체 읽기 트랜잭션) → 관광공사 호출 순서
- **OSIV 예외 경로**(구현 중 발견): Boot 기본 open-in-view에서는 읽기 트랜잭션이 끝나도 커넥션을 요청 끝까지 잡아, 관광공사 대기 중 커넥션 1개를 점유함(AT-004 실패로 발견, OSIV를 끄고 재실행해 원인 확정)
  - `spring.jpa.open-in-view: false` + `OpenInViewConfig` 로 같은 인터셉터를 직접 등록하고 `/api/v1/places/search` 만 제외. 다른 경로는 기존과 동일(테스트로 고정)
  - 기각: 전역 OSIV 끄기(다른 API 지연 로딩 영향이 범위 밖), 관광공사를 DB보다 먼저 호출(순서에 기대는 취약 구조)
- 설정: `application.yml` `place.tour.service-key: ${PLACE_TOUR_SERVICE_KEY:}`
- 모듈 규칙 `.claude/rules/place.md` 에 관광공사 실시간 검색 규칙 추가

## 비평과 검증 결정

| 항목 | 결정 | 요약 |
|---|---|---|
| C-r1-01 | 수용 | relaxed binding은 `PLACE_TOUR_SERVICEKEY` 만 묶음 → `application.yml` 명시 매핑과 바인딩 테스트 |
| C-r1-02 | 기각-감수(이민석 승인) | 읽기 타임아웃은 총 시간 상한이 아님. 총 시간 마감은 두지 않고 한계를 문서화, 실패 로그에 걸린 ms를 남겨 관측 시 재검토 |
| V-r1-01 | 수용 | 관광공사 `resultCode` 를 그대로 로그에 옮기던 것 → 코드 모양일 때만 |
| V-r1-02 | 수용 | `"NaN"`, `"Infinity"` 좌표 통과 → 유한값 검사 |

## gate 근거

- `agent-pipeline gate` exit 0 (`./gradlew test build`, detekt, Konsist 포함)
- 테스트 848개, 실패 0, 스킵 0
- 새 테스트: `TourApiClientTest`(AT-001), `TourPlaceSearchTest`(AT-002, AT-003), `TourApiPropertiesBindingTest`(AT-003 키 바인딩), `PlaceSearchFacadeTest`(AT-003), `PlaceSearchTourApiIntegrationTest`(AT-003, AT-004 트랜잭션 없음과 활성 커넥션 0, OSIV 예외 경로, AT-005)

## 남은 일 (사람)

- **운영 서버에 `PLACE_TOUR_SERVICE_KEY` 등록**(개발계정 키, 하루 1,000회). 등록 전까지는 `DISABLED`
- **키 등록 후 실제 응답 확인 1회**(Swagger 기준 구현이라 미확인): `searchKeyword2` 가 관광타입 필터를 받는지, `lDongRegnCd + lDongSignguCd` 가 우리 지역 코드와 맞는지. 어긋나면 지역이 시도로 내려가거나 항목이 빠짐
- 프론트: `tourPlaces` 표시, 출처 "출처 : ⓒ한국관광콘텐츠랩" 표기, 입력 debounce와 최소 글자 수(호출 한도 보호), 응답 형식 합의(노션 244 TODO)
- 공사 문의(선택 장소 이름 저장 허용 여부) 답변 후 모집글, 파티, 여행계획서의 관광지 참조 구조 태스크
- 서비스 런칭 후 운영계정 신청과 트래픽 증설 신청

## 후속 관찰

- `FAILED` 경고 로그의 `elapsedMs` 가 읽기 타임아웃(2초)을 크게 넘는 사례가 보이면 총 시간 상한 추가(C-r1-02)
- 한도 초과(`gatewayReasonCode=22`) 빈도
