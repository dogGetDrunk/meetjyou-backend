# Intent

> 승인 기록은 `approval.json`(`agent-pipeline approve-intent`), 승인 후 이 파일 수정 금지.

## 해결할 문제

- 모집글, 파티, 여행계획서의 대표 여행지는 `region_code` + `place_id`(소상공인 `place` FK)만 저장 가능
- 관광공사 여행지는 ADR-0004에 따라 DB에 저장하지 않고 실시간 검색만 함(`GET /places/search` 의 `tourPlaces`, PR #290)
  - 사용자가 검색 결과에서 관광지(예: 경복궁)를 골라도 글에 연결할 방법이 없음
- ADR-0004 결정 "장소 참조를 `place_id` FK에서 출처 + 외부 id 구조로 변경"(노션 244 TODO)이 미구현

## 의도

- 모집글, 파티, 여행계획서가 관광공사 장소를 `contentid` 로 참조할 수 있게 함
- 공사 API 추가 호출과 장소 이름 저장 없이 해결(이름 저장이 캐싱 금지에 해당하는지 공사 문의 전)

## 결정 사항 (2026-11-26 사람 선택)

- **저장 구조**: `post`, `party`, `plan` 에 nullable `tour_content_id` 컬럼 추가, 기존 `place_id` FK는 소상공인 전용으로 유지
  - `place_id` 와 `tour_content_id` 는 동시에 값을 가질 수 없음(DB CHECK 제약)
  - 기각: `place_source` + `place_ref` 로 통일(ADR 문구 그대로). 출처 확장성은 있으나 소상공인 FK 무결성이 사라지고, `@ManyToOne` 대신 목록 응답 배치 로딩을 새로 짜야 해 이미 동작하는 소상공인 경로를 다시 손봄. 세 번째 출처 계획 없음(YAGNI)
- **응답 표시**: 조회 응답에는 `tourContentId` 만 내려줌, 화면 이름은 과도기 동안 필수인 기존 `location`/`destination` 문자열 사용
  - 기각: 조회마다 `detailCommon2` 재조회(게시글 조회가 하루 1,000회 한도 소모, 공사 장애가 게시글 조회로 번짐)
  - 공사가 이름 저장을 허용하면 이름 컬럼은 후속 작업
- **쓰기 검증**: `tourContentId` 는 형식(숫자, 길이)만 검증, 위반 시 400. 공사 API 호출 없음, 지역은 요청 `regionCode` 를 그대로 씀(없으면 미지정 `00`)
  - 기각: 저장 시 공사 API로 존재와 시군구 확인(쓰기 경로가 외부 장애와 한도에 묶임, OSIV 제외 구조 추가 필요)
  - 감수: 존재하지 않는 `contentid` 나 지역 불일치가 저장될 수 있음. 지역 단위 집계와 `location` 표시에는 영향 작음

## 포함 범위

- Flyway 마이그레이션: `post`, `party`, `plan` 에 `tour_content_id` 컬럼과 CHECK 제약 추가
- 엔티티(`Post`, `Party`, `Plan`)와 `TravelDestination` 에 관광공사 참조 추가
- `TravelDestinationResolver`: `tourContentId` 형식 검증, `placeId` 와 동시 지정 시 400, 기존 값 재전송 허용 규칙 유지
- 요청 DTO: `CreatePostRequest`, `UpdatePostRequest`, `CreatePlanRequest`, `UpdatePlanRequest` 에 `tourContentId: String?` 추가(선택 항목, 기존 앱 요청 그대로 동작)
- 파티는 기존처럼 모집글에서 여행지를 받음(`createParty`, `syncFromPost`) → 관광공사 참조도 함께 복사
- 응답 DTO: 모집글, 파티, 여행계획서 조회와 생성, 수정 응답에 `tourContentId` 추가
  - `party.planSnapshot` JSON 역직렬화 호환 유지(새 필드 기본값 null)
- ADR-0004 개정: Decision의 "출처 + 외부 id 구조로 변경"을 위 저장 구조로 정정, 개정 근거와 날짜 기록(이 intent 승인으로 함께 승인)
- `.claude/rules/place.md` 의 Destinations 항목 갱신
- 단위 테스트와 통합 테스트, CHECK 제약은 MySQL dockerTest로 확인

## 제외 범위

- 관광공사 장소 이름, 주소, 좌표 저장(공사 캐싱 문의 결과 대기)
- 공사에 캐싱 여부 문의 메일 발송(사람 작업)
- 관광공사 검색 호출 한도와 시군구 필터(노션 246, 병렬 태스크 `20261126-tour-quota-region-filter`)
- `location` 등 DTO 길이 검증과 완료 파티 409(노션 248, 이 태스크 머지 후 진행)
- 추천 집계 구현, 프론트 검색 결과 형식 합의
- 기존 `place_id` 데이터 이관(운영 사용 0건, ADR-0004 11/22 조회)

## 성공 기준

- 모집글 생성과 수정에서 `tourContentId` 를 보내면 저장되고, 모집글 조회 응답에 같은 값이 나옴
- 그 모집글로 만든 파티와 모집글 수정 후 동기화된 파티에 같은 `tourContentId` 가 반영됨
- 여행계획서 생성, 수정, 조회도 같은 동작
- `placeId` 와 `tourContentId` 를 함께 보내면 400, 숫자가 아니거나 길이를 넘는 `tourContentId` 도 400
- `tourContentId` 없이 보내는 기존 요청(현재 앱)은 응답 코드와 저장 결과가 이전과 같음
- MySQL에서 두 컬럼을 함께 채우는 INSERT가 CHECK 제약으로 거부됨(dockerTest)
- 기존 `planSnapshot` JSON(새 필드 없음)이 그대로 역직렬화됨
- `agent-pipeline gate` 통과(`./gradlew build` 포함 detekt, Konsist, 전체 테스트)

## 작업 크기

M — 엔티티 3개, 마이그레이션 1개, 요청과 응답 DTO, 리졸버, ADR 개정을 함께 바꾸는 기능 하나

## 출처

- 노션 태스크: [관광공사 여행지 실시간 연동 (참조를 출처+외부 id로 변경)](https://app.notion.com/p/41a56d9a97d981c8b088cf54e550eab9) (작업 ID 244)
- GitHub 이슈: 없음
- 관련 결정: `docs/adr/0004-tour-api-realtime-integration.md`
