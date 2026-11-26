# Spec

## 전제

- intent 결정(2026-11-26): `tour_content_id` 컬럼 추가 + `place_id` FK 유지, 응답은 contentId 만, 쓰기는 형식만 검증
- 운영 MySQL `8.0.41`(`docs/ops/db-backup-plan.md`) → CHECK 제약 강제됨(8.0.16+)
- 엔티티 3개는 대표 여행지를 `TravelDestination` 으로만 읽고 씀(`travelDestination()`, `applyTravelDestination()`), 파티는 `createParty`, `syncFromPost` 에서 모집글의 `TravelDestination` 을 그대로 받음 → `TravelDestination` 에 필드를 더하면 파티 복사가 따라옴
- 관광공사 `contentid` 는 숫자 문자열(`TourApiClient` 가 `contentid` 를 문자열로 읽음, 예: `126508`)

## 요구사항

### REQ-001 스키마

- 요구사항: 테이블별 마이그레이션 3개, 파일마다 `ALTER TABLE` 한 문장(C-r1-06)
  - `V48__add_post_tour_content_id.sql`, `V49__add_party_tour_content_id.sql`, `V50__add_plan_tour_content_id.sql`
  - 각 테이블에 `tour_content_id VARCHAR(20) NULL` 추가
  - 테이블별 CHECK 제약 `chk_<table>_single_place`: `place_id IS NULL OR tour_content_id IS NULL`
  - 기존 행은 `NULL` (이관 없음)
  - 부분 적용 대비: MySQL 8.0 InnoDB 원자적 DDL이라 실패한 파일은 변경을 남기지 않음 → 실패 시 `flyway repair` 후 재배포하면 그 파일부터 다시 적용(result.md에 절차 기록)
- 수용 기준
  - 세 테이블에 컬럼이 `VARCHAR(20)`, nullable 로 존재
  - `place_id` 와 `tour_content_id` 를 함께 채운 INSERT가 세 테이블 모두 거부됨(MySQL 오류 코드 `3819`)
  - 둘 중 하나만 채운 INSERT는 성공
  - 한 UPDATE 문으로 `place_id` 를 비우고 `tour_content_id` 를 채우는 출처 전환은 성공, 반대 방향도 성공(C-r1-04)
  - 기존 FK `fk_<table>_place` 는 그대로 동작
- 수용 테스트 ID: AT-001 (`TourContentRefSchemaDockerTest`, MySQL)
  - CHECK 위반은 Spring 오류 코드 표에 없는 `3819`(SQLState `HY000`)라 `DataIntegrityViolationException` 이 아닐 수 있음 → 예외 종류가 아니라 원인 `SQLException.errorCode == 3819` 로 단언

### REQ-002 엔티티와 `TravelDestination`

- 요구사항
  - `TravelDestination(regionCode, place, tourContentId: String? = null)`, `UNSPECIFIED` 는 셋 다 기본값
  - `Post`, `Party`, `Plan` 에 `@Column(name = "tour_content_id", length = TOUR_CONTENT_ID_MAX_LENGTH) var tourContentId: String? = null`
  - `travelDestination()` 은 `tourContentId` 포함, `applyTravelDestination()` 은 `tourContentId` 도 대입
  - 길이 상수 `TOUR_CONTENT_ID_MAX_LENGTH = 20` 은 `TravelDestination` companion 에 둠(마이그레이션 길이와 같음)
- 수용 기준: AT-003의 파티 반영 시나리오로 확인(엔티티 단독 테스트 없음)

### REQ-003 `TravelDestinationResolver`

- 요구사항: 시그니처 `resolve(regionCode: String?, placeId: Long?, tourContentId: String? = null, current: TravelDestination? = null)`
  - 재전송 허용: `regionCode`, `placeId`, `tourContentId` 세 값이 모두 `current` 와 같으면 검사 없이 `current` 반환(기존 규칙에 `tourContentId` 비교만 추가)
  - `placeId` 와 `tourContentId` 가 둘 다 non-null → `InvalidInputException`(400), 장소 조회 전에 검사
  - `tourContentId` 가 `^[0-9]{1,20}$` 에 맞지 않음(빈 문자열, 공백, 문자 포함, 21자 이상) → `InvalidInputException`(400)
  - `tourContentId` + 지역 → `TravelDestination(검증된 지역, null, tourContentId)`
  - `tourContentId` 만 → `TravelDestination("00", null, tourContentId)` (지역 미지정)
  - 공사 API 호출과 지역 일치 검사는 없음(intent 감수 항목)
  - 기존 `placeId` 와 `regionCode` 처리 동작은 바뀌지 않음
- 수용 기준: 위 규칙 각각 + 기존 케이스 회귀 없음
- 수용 테스트 ID: AT-002 (`TravelDestinationResolverTest` 확장, 단위)
  - 동등 분할과 경계값: `"1"`(최소), 20자리(최대), 21자리, `""`, `" "`, `"12a"`, `"-1"`, `placeId`+`tourContentId`, 재전송(같은 값, 지역만 바꿈, contentId만 바꿈)

### REQ-004 요청과 쓰기 경로

- 요구사항
  - `CreatePostRequest`, `UpdatePostRequest`, `CreatePlanRequest`, `UpdatePlanRequest` 에 `val tourContentId: String? = null` (`placeId` 옆)
  - `PostService` 생성과 수정, `PlanService` 생성과 수정이 `tourContentId` 를 리졸버에 전달(수정은 `current` 포함)
  - 수정 요청은 지금처럼 대표 여행지 전체를 교체: `tourContentId` 를 빼고 보내면 기존 값이 지워짐(`placeId` 와 같은 규칙)
  - 파티: 모집글 생성 시 `createParty`, 모집글 수정 시 `syncFromPost` 로 `tourContentId` 포함 복사(코드 변경은 REQ-002로 충분, 경로 확인만)
  - JSON 토큰 계약(C-r1-03): `"126508"`(따옴표 문자열)과 `126508`(따옴표 없는 정수, Jackson 기본 스칼라 강제 변환으로 문자열이 됨)은 허용하고 같은 형식 검증을 거침. 명시적 `null` 은 생략과 같음
  - 쓰기와 조회 어디서도 관광공사 API를 호출하지 않음(C-r1-07)
- 수용 기준
  - 모집글 생성(`tourContentId` + `regionCode`) → 모집글 조회와 파티 조회 응답 모두 같은 `tourContentId`, `place` 는 null
  - 모집글 수정으로 `tourContentId` 변경 → 모집글과 파티 모두 새 값, 빼고 보내면 둘 다 null
  - 출처 전환(C-r1-04): 소상공인 장소 모집글을 `tourContentId` 로 수정 → 모집글과 파티 모두 `place` null + 새 `tourContentId`, 반대 방향도 같음. 계획서도 두 방향 확인
  - 여행계획서 생성, 수정, 조회도 같은 저장과 응답
  - API로 `placeId` + `tourContentId` 동시 전송, 형식 위반 `tourContentId` → 400
  - raw body: 따옴표 없는 정수 → 문자열로 저장, 명시적 `null` → null 저장(C-r1-03)
  - `enabled = true` 인 카운팅 `TourPlaceSource` fake 주입, 위 쓰기와 조회 흐름 전체에서 호출 0회(C-r1-07)
  - `tourContentId` 없는 기존 요청은 응답 코드와 저장 결과가 이전과 같음(기존 `TravelDestinationApiIntegrationTest` 케이스 통과)
- 수용 테스트 ID: AT-003 (`TravelDestinationApiIntegrationTest` 확장, H2 통합)

### REQ-005 응답

- 요구사항: 아래 9개 응답에 `tourContentId: String?` 추가(엔티티 값 그대로)
  - `CreatePostResponse`, `UpdatePostResponse`, `GetPostResponse`
  - `GetPartyResponse`, `GetMyPartyResponse`, `UpdatePartyResponse`
  - `CreatePlanResponse`, `UpdatePlanResponse`, `GetPlanResponse`
  - `GetPlanResponse.tourContentId` 는 기본값 `null` (`party.planSnapshot` 에 저장된 예전 JSON 역직렬화 호환, `region`, `place` 와 같은 방식)
- 수용 기준(C-r1-02: 응답마다 AT-003 단언 하나 이상)
  - `CreatePostResponse`, `CreatePlanResponse`: 생성 응답 본문에 보낸 `tourContentId`
  - `UpdatePostResponse`, `UpdatePlanResponse`: 수정 응답 본문에 새 값
  - `GetPostResponse`, `GetPlanResponse`, `GetPartyResponse`: 조회 응답에 저장값
  - `UpdatePartyResponse`: 파티 수정(제목 등 여행지 외 수정) 응답에 모집글에서 받은 값 유지
  - `GetMyPartyResponse`: 내 파티 목록 항목에 같은 값
  - null 표현: 소상공인 장소나 지역만 쓴 응답은 `"tourContentId": null` (필드 존재, 값 null)
  - 새 필드가 없는 예전 `planSnapshot` JSON이 예외 없이 역직렬화되고 `tourContentId == null`
- 수용 테스트 ID: AT-004 (`GetPlanResponseSnapshotCompatTest`, 단위, 앱과 같은 `ObjectMapper` 설정)

### REQ-006 문서

- 요구사항
  - ADR-0004: Decision의 "장소 참조를 `place_id` FK에서 출처 + 외부 id 구조로 변경"을 "`place_id` FK는 소상공인 전용으로 두고 `tour_content_id` 컬럼 추가, 둘 중 하나만(CHECK)"으로 정정. 개정 이력에 날짜(2026-11-26), 근거(통일 구조 기각 이유), 승인(이 태스크 intent 승인, damiannlee) 기록. Status는 Accepted 유지
  - `.claude/rules/place.md` "Destinations on post / party / plan": 관광공사 참조 규칙(contentId만 저장, 형식만 검증, `place_id` 와 배타, 화면 이름은 모집글 `location`, 파티와 계획서 `destination`) 추가, `paths` 에 `V48__*`~`V50__*` 포함
- 수용 기준: 문서 diff 리뷰, 자원별 표시 필드 이름이 맞는지 확인(C-r1-08)(테스트 없음)

## 확정 인터페이스

### API 시그니처와 DTO

- 요청(모집글 생성과 수정, 여행계획서 생성과 수정): 기존 필드 + `"tourContentId": "126508"` (선택, 숫자 1~20자)
- 응답(REQ-005의 9개): 기존 필드 + `"tourContentId": "126508" | null`
- 오류: 400 `INVALID_INPUT_VALUE` (`InvalidInputException`, 기존 리졸버 오류와 같은 형식)
- 엔드포인트, 상태 코드, 기존 필드 변경 없음

### DB 스키마

```sql
-- V48__add_post_tour_content_id.sql (V49 party, V50 plan 같은 형태)
ALTER TABLE post
    ADD COLUMN tour_content_id VARCHAR(20) NULL,
    ADD CONSTRAINT chk_post_single_place CHECK (place_id IS NULL OR tour_content_id IS NULL);
```

## 변경 파일

- `src/main/resources/db/migration/V48__add_post_tour_content_id.sql`, `V49__add_party_tour_content_id.sql`, `V50__add_plan_tour_content_id.sql` (신규)
- `place/TravelDestinationResolver.kt` (`TravelDestination` 포함)
- `post/Post.kt`, `party/Party.kt`, `plan/Plan.kt`
- `post/dto/CreatePostRequest.kt`, `UpdatePostRequest.kt`, `plan/dto/CreatePlanRequest.kt`, `UpdatePlanRequest.kt`
- `post/PostService.kt`, `plan/PlanService.kt`
- 응답 DTO 9개(REQ-005)
- `docs/adr/0004-tour-api-realtime-integration.md`, `.claude/rules/place.md`
- 테스트: `TravelDestinationResolverTest`, `TravelDestinationApiIntegrationTest`, 신규 `GetPlanResponseSnapshotCompatTest`, 신규 `TourContentRefSchemaDockerTest`

## 위험

- MySQL은 참조 동작(`ON DELETE`, `ON UPDATE`)이 있는 FK 컬럼에 CHECK를 금지(오류 `3823`). `fk_*_place` 는 참조 동작이 없어 해당하지 않는다고 판단 → AT-001이 실제 MySQL에서 마이그레이션 적용으로 확인
- H2(`create-drop`, 엔티티 기준)에는 CHECK가 없음 → 동시 지정 차단은 리졸버(AT-002, AT-003)와 MySQL(AT-001)로 나눠 확인
- 248(`location` 길이, 완료 파티 409)이 같은 DTO와 다음 마이그레이션 번호를 씀 → 이 태스크 머지 후 진행(intent 제외 범위), 248은 V51부터
- 감수(C-r1-01, damiannlee 승인): `CreatePostRequest` 필드 추가로 멱등 키 해시가 바뀜 → 배포 직전 키로 배포 직후 같은 요청을 재시도하면 원래 글 대신 409. 중복 글과 데이터 손상은 없음. result.md 후속 항목에 기록

## 마일스톤

1. V48~V50 + 엔티티 + `TravelDestination` + 리졸버, AT-001, AT-002
2. 요청과 응답 DTO, 서비스 전달, AT-003, AT-004
3. ADR-0004 개정, `place.md` 갱신
4. `./gradlew dockerTest` 로컬 실행으로 AT-001을 실제 MySQL에서 확인하고 결과(통과 수, 실패 수)를 result.md에 기록(C-r1-05, `gate` 는 dockerTest를 돌리지 않음)
5. `agent-pipeline gate`
