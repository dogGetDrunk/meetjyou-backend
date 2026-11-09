# Spec — 여행지 검색 1차

> 근거 자료: intent.md(승인본), 2026-09 소상공인 파일 실측(2026-11-09, `상가(상권)정보_20260930`, 275만 행, CSV 16개, 전 필드 따옴표)

## 사실 확인 (2026-11-09 실측)

- 다운로드: 로그인·쿠키 없이 3단계, base-url `https://www.data.go.kr`, POST 본문은 `application/x-www-form-urlencoded`
  1. `POST /tcs/dss/selectFileDataDownload.do` — `publicDataPk=15083033`, `publicDataDetailPk=uddi:86f46f63-829a-46c1-abdd-d8e66846b6da`, `atchFileId=`(빈 값), `fileDetailSn=1`, `publicDataTyCode=PR0051` → JSON `{"status":true,"atchFileId":"FILE_000000007751774","fileDetailSn":1,"dataSetFileDetailInfo":{"dataNm":"소상공인시장진흥공단_상가(상권)정보_20260930",…}}`. `status`가 true가 아니면 실패
  2. `POST /cmm/cmm/check-limit.json` — `atchFileId`, `fileDetailSn` → `{"needCaptcha":false}`. true이거나 HTTP 403이면 실패
  3. `GET /cmm/cmm/fileDownload.do?atchFileId={id}&fileDetailSn={sn}` → `application/octet-stream`, zip 349,070,686 bytes
- 원본: `소상공인시장진흥공단_상가(상권)정보_20260930` zip, SHA-256 `c83b30e7a1d1144864a91d0e1818abf360f565972878d6e3c88d75ceafe2286a`. 분석·시드 생성 스크립트: `.pipeline/tasks/20261109-place-search/scripts/`(`an.py` 집계, `seed.py` → `V45__seed_region.sql`)
- zip 항목 이름은 UTF-8(플래그 없음), CSV 16개(시·도별) + 안내 txt 1개, CSV 본문 UTF-8(BOM 없음), 모든 필드 따옴표, 헤더 39열(`상가업소번호`…`경도`,`위도`), 빈 좌표·주소·상호 0건
- 업종 코드(상권업종소분류코드): 음식 `I2*`, 숙박 `I1*`, 소매 `G2*`
  - 제외: `I20701` 구내식당, `I21102` 무도 유흥 주점, `I10201` 기숙사/고시원
  - 쇼핑 7개 코드(후보 8개, 시계·귀금속은 한 코드): `G20405` 편의점, `G21802` 기념품점, `G21503` 화장품, `G21501` 약국, `G21801` 예술품, `G21301` 서점, `G21701` 시계/귀금속
  - 대상 합계 **1,023,599건**
- 시·도 16개(전남광주 `12`), 시·군·구 코드 256개. 그중 일반구 39개는 이름이 `수원시 장안구` 형태이고 상위 시(13개: 수원 41110, 성남 41130, 안양 41170, 부천 41190, 안산 41270, 고양 41280, 용인 41460, 화성 41590, 청주 43110, 천안 44130, 포항 47110, 창원 48120, 전주 52110)는 파일에 없음 → 상위 시 코드 = 일반구 코드 앞 4자리 + `0`(39개 모두 성립)
- 분기 사이 같은 가게인데 상가업소번호가 바뀌는 경우 약 4.7만 곳(10/13 검토서) → 번호만으로 갱신하면 멀쩡한 가게가 "영업 정보 없음"이 됨

## 요구사항

### REQ-001 지역 목록

- 요구사항: `region` 테이블에 시·도 16 + 시·군·구 256 + 일반구 상위 시 13 + "미지정" 1 = 286행을 Flyway로 적재. 일반구의 부모는 상위 시, 상위 시·일반 시·군·구의 부모는 시·도
- 수용 기준
  - 전남광주(`12`, 예: `12210` 광주 동구), 인천 신설 구(`28125` 제물포구 등), 화성 신설 구(`41591`~`41597`) 존재
  - `41111`(수원시 장안구) → 부모 `41110`(수원시) → 부모 `41`(경기도)
  - "미지정"(`00`)은 `searchable=false`
  - 레벨별 건수(SIDO 17 = 16 + 미지정, SIGUNGU 269), 코드 중복 0, 부모 없는 SIGUNGU 0, SIDO의 부모 없음, SIGUNGU의 부모는 SIDO 또는 (일반구일 때) 부모의 부모가 SIDO인 SIGUNGU
- 수용 테스트 ID: AT-001

### REQ-002 소상공인 적재 (ADMIN)

- 요구사항: ADMIN API로 비동기 적재 시작. 서버가 3단계로 zip을 받아 스트리밍으로 읽고, 대상 업종·등록된 지역만 staging에 넣은 뒤 반영
- 수용 기준
  - 대상 업종만 `place`에 들어가고 제외 업종·미등록 지역 코드 행은 0건(건너뜀 건수로 집계)
  - 같은 파일 재적재 시 `place` 행 수 불변, 값이 같은 행은 UPDATE 0건(`updated=0`, `updated_at` 불변), 바뀐 필드(이름·지점명·주소·좌표·업종·소속 지역)는 갱신
  - **입력 검증(반영 전, 실패 시 `place` 무변경)**: 각 CSV 헤더에 사용 열(`상가업소번호`,`상호명`,`지점명`,`상권업종소분류코드`,`상권업종소분류명`,`시도코드`,`시군구코드`,`도로명주소`,`경도`,`위도`)이 모두 있을 것 / CSV 1개 이상 / staging 1건 이상 / 현재 활성 가게가 있는 **시·도마다** staging 건수 ≥ 그 시·도 활성 건수의 50%(경계 50%는 통과)
  - 다운로드·파싱·검증 실패 시 `place` 무변경
  - **반영 순서** (각 단계 5,000행 단위 트랜잭션, 모집글 생성의 FK 공유 락이 장시간 막히지 않게)
    1. 같은 `source_key` → 값이 바뀐 행만 UPDATE, `active=true`
    2. 재매칭: 남은 기존 행(키가 staging에 없음, 활성 여부 무관)과 남은 staging 행(키가 place에 없음)을 전체 값 `(region_code, search_name, road_address)`로 묶어 **양쪽 각 1건**일 때만 기존 행의 `source_key`·필드 갱신, `active=true`, `place.id` 유지. 접두 인덱스는 탐색용, 동일성은 전체 값 비교
    3. 남은 staging → 신규 INSERT
    4. 1~3이 모두 성공한 뒤에만: staging에 키가 없는 기존 행 → `active=false`(행 유지)
  - 반영 중 실패: 이미 커밋한 1~3단계 청크는 유지(새 파일의 올바른 값), 4단계 미실행 → 정상 가게가 검색에서 빠지지 않음. 상태 FAILED, 가드 해제, staging은 다음 실행 시작 때 비움. 같은 파일 재실행 결과는 한 번에 성공한 실행과 같음(id·행 수·활성 상태)
  - 실행 중 재호출 409, 비ADMIN 403
  - 완료·실패 시 로그 + Discord 운영 알림(`sendOperationalAlert`, source `place-import`)
- 수용 테스트 ID: AT-002 ~ AT-007

### REQ-003 여행지 검색

- 요구사항: `GET /places/search?q=&region=&size=` → 지역 목록 + 장소 목록
- 수용 기준
  - `q`: 앞뒤 공백 제거 후 2~50자, 아니면 400. `size` 1~50(기본 20), 범위 밖 400
  - 지역 결과: `searchable` 지역 중 `name`/`fullName`에 `q`(공백 제거 비교) 포함, 최대 10개. 메모리 카탈로그에서 계산(DB 조회 없음)
  - 장소 결과: `active=true`만, `search_name`(이름+지점명, 공백 제거) ngram FULLTEXT 구문 검색 → "밀본 그레이츠"와 "밀본그레이츠"가 같은 결과
  - 정렬: 검색용 이름이 `q`와 정확히 같은 장소 먼저, 그다음 `id` 오름차순
  - `region` 필터: 2자리 → 그 시·도 전체, 5자리 → 그 시·군·구와 하위 일반구 전체. 없는 코드·`00` → 400
  - 장소마다 소속 지역(시·군·구 코드·이름, 시·도 코드·이름) 포함, 이미지 없음
  - 인증 필요(기본 정책 유지)
- 수용 테스트 ID: AT-008, AT-009

### REQ-004 대표 여행지 참조 (전환 기간)

- 요구사항: 모집글·계획서 생성·수정 요청에 `regionCode: String?`, `placeId: Long?` 추가. 기존 `location` 필드는 그대로 필수. 파티는 모집글에서 받음(생성 `createParty`, 수정 `syncFromPost`)
- 수용 기준
  - 해석 규칙(`TravelDestinationResolver`)
    - 둘 다 없음 → 미지정(`00`), 장소 없음
    - `placeId`만 → 장소의 시·군·구를 지역으로
    - `regionCode`만 → 그 지역(없는 코드·`00` → 400)
    - 둘 다 → 장소 지역이 요청 지역과 같거나, 요청 지역의 하위(일반구)이거나, 요청 지역이 장소의 시·도여야 함. 아니면 400
    - 없는 `placeId` → 400. 비활성 장소도 허용(수정 시 기존 값 재전송 보호)
    - 수정 요청의 `regionCode`+`placeId`가 현재 저장된 값과 같으면 계층 검사 생략 — 적재로 장소 소속 지역이 바뀌어도 재전송이 400이 되지 않음
  - 글의 대표 지역은 작성 시점 선택을 보존: 적재로 장소 소속이 바뀌어도 기존 post·party·plan의 `region_code`는 그대로(일괄 동기화 안 함). 응답의 `place`는 현재 장소 값
  - 수정 요청은 전체 교체 의미: 참조를 빼고 보내면 미지정으로 바뀜(현재 `location` 필수 교체와 동일)
  - 응답: `region`(code, name, fullName, sidoCode, sidoName) 항상 포함, `place`(id, name, branchName, categoryName, roadAddress, lat, lng, active) 선택. 기존 `location`/`destination` 문자열 유지
  - 참조 없이 기존 필드만 보내는 요청 성공(응답 region = 미지정)
  - 목록 응답에서 지역·장소 조회는 batch fetch(`default_batch_fetch_size: 100`)로 페이지당 쿼리 수 고정
- 수용 테스트 ID: AT-010, AT-011

### REQ-005 기존 행 마이그레이션

- 요구사항: `post`·`party`·`plan`에 `region_code VARCHAR(5) NOT NULL DEFAULT '00'`(FK region), `place_id BIGINT NULL`(FK place) 추가 → 기존 행은 미지정
- 수용 기준: 마이그레이션 전 행이 마이그레이션 후 `region_code='00'`, `place_id IS NULL`
- 수용 테스트 ID: AT-012

## 확정 인터페이스

### API

```
GET  /places/search?q={2..50}&region={code?}&size={1..50, default 20}
  200 { regions: [RegionResponse], places: [PlaceSearchItem] }

POST /admin/places/imports            (ADMIN)  202 PlaceImportStatusResponse | 409 PLACE_IMPORT_IN_PROGRESS
GET  /admin/places/imports/latest     (ADMIN)  200 PlaceImportStatusResponse

RegionResponse      { code, name, fullName, level(SIDO|SIGUNGU), sidoCode, sidoName, cityCode?, cityName? }
  - 시·도 `11`:        {11, 서울특별시, 서울특별시, SIDO, 11, 서울특별시, null, null}
  - 일반 시·군·구:     {11680, 강남구, 서울특별시 강남구, SIGUNGU, 11, 서울특별시, null, null}
  - 일반구의 상위 시:  {41110, 수원시, 경기도 수원시, SIGUNGU, 41, 경기도, null, null}
  - 일반구:            {41111, 수원시 장안구, 경기도 수원시 장안구, SIGUNGU, 41, 경기도, 41110, 수원시}
  - 미지정:            {00, 미지정, 미지정, SIDO, 00, 미지정, null, null}
PlaceSearchItem     { id, name, branchName?, categoryName, roadAddress, lat, lng, region: RegionResponse }
PlaceSummaryResponse{ id, name, branchName?, categoryName, roadAddress, lat, lng, active }
PlaceImportStatusResponse { state(IDLE|RUNNING|SUCCEEDED|FAILED), startedAt?, finishedAt?,
                            read, staged, skipped, inserted, updated, rematched, deactivated, failure? }
```

- 요청 DTO 추가 필드: `CreatePostRequest`·`UpdatePostRequest`·`CreatePlanRequest`·`UpdatePlanRequest` ← `regionCode: String? = null` (`@Size(max=5)`), `placeId: Long? = null`
- 응답 DTO 추가 필드: `region: RegionResponse`, `place: PlaceSummaryResponse?` ← Post(Get/Create/Update), Party(Get/GetMy/Create/Update), Plan(Get/Create/Update)
  - `GetPlanResponse`는 `party.planSnapshot` JSON으로 역직렬화되므로 `region: RegionResponse? = null`, `place: PlaceSummaryResponse? = null`(옛 스냅샷 호환)
- 새 ErrorCode: `PLACE_IMPORT_IN_PROGRESS(409)`, `PLACE_NOT_FOUND`는 만들지 않음(요청 검증 실패 = `INVALID_INPUT_VALUE` 400), `PLACE_IMPORT_SOURCE_UNAVAILABLE`은 비동기 실패 상태로만 기록(HTTP 응답 없음)
- `ErrorCodeStatusTest` 표에 새 코드 추가

### DB 스키마 (Flyway)

- `V44__create_region_and_place.sql`
  - `region(code VARCHAR(5) PK, name VARCHAR(30) NOT NULL, full_name VARCHAR(60) NOT NULL, level VARCHAR(10) NOT NULL, parent_code VARCHAR(5) NULL FK region(code), searchable TINYINT(1) NOT NULL)`
  - `place(id BIGINT AUTO_INCREMENT PK, source VARCHAR(20) NOT NULL, source_key VARCHAR(30) NOT NULL, name VARCHAR(200) NOT NULL, branch_name VARCHAR(100) NULL, search_name VARCHAR(300) NOT NULL, category_code VARCHAR(10) NOT NULL, category_name VARCHAR(50) NOT NULL, region_code VARCHAR(5) NOT NULL FK region(code), road_address VARCHAR(200) NOT NULL, lat DOUBLE NOT NULL, lng DOUBLE NOT NULL, active TINYINT(1) NOT NULL, created_at, updated_at)`
    - `UNIQUE uk_place_source_key(source, source_key)`, `INDEX idx_place_rematch(source, region_code, search_name(100), road_address(100))`, `INDEX idx_place_region_active(region_code, active)`, `FULLTEXT ft_place_search_name(search_name) WITH PARSER ngram`
  - `place_import_staging(id BIGINT AUTO_INCREMENT PK, source_key, name, branch_name, search_name, category_code, category_name, region_code, road_address, lat, lng)` + `INDEX(source_key)`, `INDEX(region_code, search_name(100), road_address(100))`
- `V45__seed_region.sql`: 286행 INSERT (2026-09 소상공인 파일에서 생성, 생성 스크립트는 result.md에 기록)
- `V46__add_travel_destination_refs.sql`: `post`·`party`·`plan`에 `region_code`(DEFAULT '00', FK), `place_id`(NULL, FK) + 인덱스
- 엔티티: `Region`(code PK, `@Immutable`), `Place`(`source` enum `PlaceSource { SBIZ }`), `Post`/`Party`/`Plan`에 `@ManyToOne(LAZY) region`, `@ManyToOne(LAZY) place`
- H2 테스트는 엔티티 어노테이션으로 스키마 생성 → FULLTEXT·네이티브 검색 쿼리·적재 SQL은 dockerTest(MySQL)에서만 검증

### 구성 요소 (패키지 `place`)

- `RegionCatalog`: 기동 시 `region` 전체 로드(286행, 마이그레이션으로만 바뀜), 코드 조회·하위 코드 전개·이름 검색
- `PlaceSearchService` + `PlaceSearchRepository`(네이티브 MATCH … AGAINST, 결과 id → `findAllById` 1회)
- `TravelDestinationResolver`: REQ-004 규칙
- `PlaceImportService`: 실행 가드(`AtomicBoolean`), 전용 단일 스레드 실행기, 상태 보관
- `SbizFileClient`(RestClient, 3단계 다운로드, `InputStream` 반환) ↔ `SbizZipReader`(ZipInputStream + commons-csv, 행 필터) ↔ `PlaceImportWriter`(JdbcTemplate batch 1,000행 staging 적재, 5,000행 단위 반영 SQL)
- 설정: `place.import.sbiz.*`(base-url, public-data-pk, public-data-detail-pk, 타임아웃) `@ConfigurationProperties`
- 의존성 추가: `org.apache.commons:commons-csv` (전 필드 따옴표 CSV, 수작업 split 기각)

## 테스트

| ID | 종류 | 내용 |
|---|---|---|
| AT-001 | dockerTest | Flyway 적용 후 region 286행, 레벨별 건수·중복·고아·부모 레벨 검사, 일반구·상위 시·시도 체인, 신설 구, 미지정 searchable=false |
| AT-002 | dockerTest | fixture zip(대상·제외 업종·미등록 지역 혼합) 적재 → 대상만 저장, 건너뜀 건수 |
| AT-003 | dockerTest | 같은 fixture 재적재 → 행 수 불변, `updated=0`·`updated_at` 불변 / 값 바뀐 fixture → 해당 필드만 갱신, 소속 지역 바뀐 장소를 참조한 글의 region_code 불변 |
| AT-004 | dockerTest | 다음 fixture: 빠진 가게 비활성 / 번호만 바뀐 가게 id 유지(1:1, 비활성 기존 후보 포함) / 1:N·N:1·N:M은 재매칭 없이 신규 삽입+기존 비활성 / 같은 키와 같은 값의 새 키 공존 시 같은 키 유지+새 키 삽입 / 주소 앞부분만 같은 다른 주소는 재매칭 안 함 |
| AT-005 | dockerTest | 반영 거부·무변경: 한 시·도 50% 미만(경계 50%는 통과), 시·도 CSV 누락, 필수 헤더 누락, CSV 0개, 활성 0건에서 staging 0건 / 반영 중 실패 주입(1단계 두 번째 청크) → FAILED, 비활성화 0건, 가드 해제, 같은 파일 재실행 결과 = 정상 실행 결과 |
| AT-006 | 통합(H2, MockMvc) | 적재 API 비ADMIN 403, 실행 중 409, 상태 조회, 완료·실패 시 `sendOperationalAlert(source="place-import")` 호출 |
| AT-007 | 단위 | `SbizFileClient` 3단계 요청 파라미터·순서(MockRestServiceServer), `status=false`·`needCaptcha=true`·403·다운로드 오류 → 실패, 이후 단계 미호출 |
| AT-008 | dockerTest | 검색: 지역+장소, 공백 무관, 시·도·시(일반구 포함) 필터, 비활성·미지정 제외, 정확 일치 우선, 일반구 장소의 region 응답 값(cityCode/cityName) |
| AT-009 | 통합(H2) | 검색 파라미터 검증 400(q 길이·size·없는 region·`00`) |
| AT-010 | 단위 | `TravelDestinationResolver` 규칙 표 전부 + 저장된 값 재전송 시 계층 검사 생략 |
| AT-011 | 통합(H2) | 모집글·계획서 생성·수정·조회에 region/place(일반구·시도·미지정 응답 값), 파티 동기화, 참조 없는 기존 요청 → 미지정, 불일치·없는 코드 400, 비활성 장소 참조 글의 `place.active=false` / 서로 다른 지역·장소를 가진 목록 2건 vs 10건의 SQL 문 수 동일(Statistics, clear 후) |
| AT-012 | dockerTest | V45까지 적용 → post/party/plan 행 삽입 → V46 적용 → 세 테이블 모두 region_code='00', place_id IS NULL |

## 마일스톤

1. 스키마·시드(V44~V46), 엔티티, `RegionCatalog` + AT-001·AT-012
2. 적재(클라이언트·리더·writer·서비스·ADMIN API) + AT-002~007
3. 검색 API + AT-008·009
4. 참조 변경(resolver, DTO, 서비스 3개) + AT-010·011
5. `agent-pipeline gate`, `./gradlew dockerTest`, result.md

## 위험·운영 메모

- 운영 첫 적재: 102만 행 × EC2→OCI 왕복(요청당 23~53ms) — batch 1,000행이면 staging ~1,000회 왕복. JDBC URL의 `rewriteBatchedStatements` 여부는 저장소 밖(시크릿)이라 미확인 → 배포 후 소요 시간 실측해 result에 기록
- 전국 2글자 검색(예: "카페" 11.9만 건 일치)은 정렬 비용이 큼 → 운영 p95 실측 후 기준 초과 시 다음 태스크(메모리 인덱스 또는 지역 선택 강제)
- 포털 내부 주소 변경 시 적재 실패(알림으로 감지), 데이터 반영 없음
