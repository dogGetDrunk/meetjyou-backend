# Spec — 여행지 검색 성능 개선

## 로컬 재현 실측 (2026-11-10)

- 환경: `mysql:8.0.41`(운영과 같음), `innodb_buffer_pool_size=128M`, 컨테이너 메모리 512MB, V44·V45 스키마
- 데이터: 합성 1,023,599행(운영 적재 건수), 운영처럼 5,000행씩 INSERT…SELECT·커밋. place 데이터 178MB·인덱스 223MB(운영 198/250MB)
  - '카페' 일치 22,824건(운영 25,650), '스타벅스' 1,983건, '막창' 1,949건
  - 생성·측정 스크립트: 태스크 폴더 `scripts/` (`gen.py` 생성, `load.sql` 5,000행 단위 적재, `bench.py` 측정)
- 지표: 서버 측 지연(`SHOW PROFILES`) + `Innodb_buffer_pool_read_requests`(논리 페이지 읽기)·`Innodb_buffer_pool_reads`(물리 읽기). 운영은 물리 읽기 1회 ≈ 1.7ms로 추정(현재 쿼리 9초 ÷ 물리 5,300회)

| 쿼리 | 논리 읽기 (반복) | 물리 읽기 (빈 버퍼 풀) | 운영 환산 |
|---|---|---|---|
| 현재 '카페' 전국 | 144,164 (반복해도 물리 5,286) | 5,477 | 약 9.3초 (실측 8.9초) |
| 현재 '카페' 서울 | 144,712 | — | 실측 8.6초 |
| 현재 '이디야커피' 전국 | 38,902 | 3,145 | 약 5.3초 |
| 정렬 제거만 '카페' 전국 | 355 | 34 | 0.1초 미만 |
| 정렬 제거만 '카페' 울릉군(2,547행) | 56,061 | 2,125 | 약 3.6초 ← 구멍 |
| 정렬 제거만 '스타벅스' 전국(구문) | 14,463 | 2,703 | 약 4.6초 ← 구멍 |
| **토큰 AND + LIKE** '카페' 전국 | 340 | 17 | — |
| **토큰 AND + LIKE** '스타벅스' / '이디야커피' | 904 / 1,204 | 66 / 62 | 0.1초 내외 |
| **커버링 인덱스 LIKE** '카페' 울릉군 | 157 | 60 | — |
| **커버링 인덱스 LIKE** '카페' / 희귀어 부산(4만 행) | 483 / 317 | 109 / 86 | 0.2초 내외 |
| 커버링 인덱스 LIKE 희귀어 경기(36만 행) | 1,706 | 738 | 약 1.3초 ← 큰 지역은 이 경로 금지 |
| 토큰 AND 희귀어 경기 / '카페' 서울 | 2,140 / 1,440 | — / 565 | 1초 이내 |
| 정확 일치(전체 지역 코드 IN, 커버링 인덱스) | 858 | 286 | 0.5초(빈 버퍼 풀 최악) |

- 원인 확정
  1. **비용 = 일치 행 전부의 행 읽기.** FTS 탐색 자체는 '카페' 2.3만 건에 337페이지로 쌈. `ORDER BY (정확일치), id`가 일치 행 전체를 읽고 정렬하게 만들고, 2.3만 행이 테이블 거의 전 페이지에 흩어져 128MB 버퍼 풀을 매번 넘침(반복해도 물리 읽기 5천 회)
  2. **구문 검색(`"스타벅스"`)은 정렬이 없어도 후보 행을 전부 읽어 위치 확인** → 여러 글자 검색어도 후보 수에 비례
  3. **지역 필터는 FTS가 먼저 전국 일치를 만든 뒤 걸러서** 작은 지역 + 흔한 단어가 최악
- 함정 확인: ngram 파서는 **불용어를 포함한 토큰을 인덱스에서 뺌**(기본 영어 불용어, `a`·`i` 포함) → 토큰 AND에 `+"pi"`를 넣으면 'PizzaHut'이 안 나옴. 현재 구문 검색은 이런 토큰을 건너뜀. 토큰이 전부 불용어 포함이면 현재도 0건('"asia"' → 0)

## 방식 결정 (결정 2 적용)

- **채택: 방식 A — 쿼리·인덱스 변경, 3경로**
  - 정확 일치: 커버링 인덱스 `(region_code, active, search_name)` 등호 조회
  - 지역 없음 또는 큰 지역: FTS 토큰 AND(구문 아님) + `LIKE` 확인, 정렬 없음 → `LIMIT`에서 멈춤
  - 작은 지역: 커버링 인덱스에서 `LIKE` → 그 지역 인덱스만 훑음
  - 근거: 모든 측정 경우에서 논리 읽기 수백~2천, 빈 버퍼 풀 최악 물리 읽기 약 570회(운영 환산 1초 이내). 앱 메모리 0, 인덱스 1개 추가(38MB)·1개 삭제(30MB)
- 기각: 방식 B 앱 메모리 인덱스 — 실측 평균 이름 11.3자 × 102만 행 → 압축 배열 약 37MB·문자열 객체면 약 90MB 힙, 기동·적재 후 102만 행 재로딩(EC2→OCI), 영업 정보 갱신 반영 로직 추가. A가 목표를 달성하므로 비용만 늘림
- 기각: 정렬 제거만(A1) — 작은 지역 + 흔한 단어(울릉군 '카페' 3.6초)와 여러 글자 구문(빈 버퍼 풀 '스타벅스' 4.6초)이 그대로
- 기각: 지역 필터는 항상 커버링 LIKE — 큰 지역 희귀어가 지역 전체를 훑음(경기 1,706페이지, 빈 버퍼 풀 1.3초)
- 기각: 지역 경로를 단계(시·도/시·군·구)로 고르기 — 세종처럼 작은 시·도가 FTS 경로로 가면 '카페'에서 수천 행 읽기. 실제 건수로 고름

## 요구사항

### REQ-001 정확 일치 조회

- 요구사항: 공백 제거 검색어와 `search_name`이 같은 활성 장소를 `/*+ NO_MRR(place) */ ... FORCE INDEX (idx_place_region_search) WHERE region_code IN (:codes) AND active = 1 AND search_name = :exact LIMIT :size`로 조회.
  - `FORCE INDEX`: IN 값이 200개(`eq_range_index_dive_limit`)를 넘으면 인덱스 통계로 추정하고, 통계가 없거나 낡으면 전체 스캔을 고름(구현 중 dockerTest에서 6만 행 전체 스캔 관측) `codes` = 지역 필터의 `selfAndDescendants`, 필터가 없으면 `RegionCatalog`의 검색 가능 지역 코드 전체
  - 커버링 인덱스의 선두 컬럼이 `region_code`라 지역 없는 등호 조회도 코드 목록으로 인덱스를 탐(스킵 스캔은 옵티마이저가 고르지 않음, 실측 인덱스 전체 스캔 3만 페이지)
- 수용 기준: 정확 일치 장소가 결과 맨 앞, 비활성 제외, 지역 필터 범위 밖 제외
- 수용 테스트 ID: AT-002

### REQ-002 FTS 경로 (지역 없음 또는 큰 지역)

- 요구사항: `MATCH(search_name) AGAINST (:terms IN BOOLEAN MODE) AND search_name LIKE :pattern AND search_name <> :exact AND active = 1 [AND region_code IN (:codes)] LIMIT :remaining`, `ORDER BY` 없음
  - `terms`: 검색어(공백·큰따옴표 제거)를 MySQL처럼 단어로 나눔(문자·숫자·`_`가 아닌 문자 = 구분자) → 2글자 이상 단어의 2글자 토큰을 중복 없이 각각 `+"xy"`로
    - 불용어(MySQL 기본 InnoDB 불용어 목록 36개)를 포함한 토큰 제외(대소문자 무시)
    - 1글자 단어는 항목 없음: MySQL이 토큰으로 인덱싱하지 않음('느낌!표'의 '표'), 접두 항목 `+표*`도 못 찾음
    - 이 규칙이면 남은 토큰은 검색어를 부분 문자열로 가진 모든 이름에 인덱싱돼 있음 → **FTS 후보 ⊇ LIKE 일치** → 두 경로 결과가 같음(구현 중 dockerTest가 `%`·`!` 검색어에서 경로 불일치를 잡아 추가)
  - 쓸 토큰이 하나도 없으면(전부 불용어 포함, 또는 큰따옴표 제거 후 1글자) **정확 일치를 포함한 장소 검색 전체가 빈 결과** — 경로와 무관하게 같고 현재 구문 검색과 같은 동작(C-r1-01)
  - `pattern`: `%검색어%`, `\`·`%`·`_`는 이스케이프
  - `remaining` = `size` − 정확 일치 건수, 0 이하이면 조회하지 않음
- 수용 기준: 일치 10배 데이터에서 검색 1회 `ROWS_EXAMINED` 합계 ≤ 1배 데이터 × 1.2 + 20, FTS 경로 절대 상한 200(현재 '카페' 22,845). 여러 글자 검색어도 같음. 후보 자격은 현재 구문 검색과 같음(C-r1-02 정의)
- 수용 테스트 ID: AT-001, AT-003

### REQ-003 지역 경로 선택과 커버링 인덱스 경로

- 요구사항: 지역 필터 코드 집합의 활성 장소 수 합계가 `REGION_SCAN_MAX_PLACES`(**30,000**) 이하면 커버링 경로, 초과면 REQ-002 FTS 경로
  - 커버링 경로: `/*+ NO_MRR(place) */ ... FORCE INDEX (idx_place_region_search) WHERE region_code IN (:codes) AND active = 1 AND search_name LIKE :pattern AND search_name <> :exact LIMIT :remaining`, 정렬 없음
  - `NO_MRR`: 지역 코드가 여러 개(인덱스 범위 여러 개)면 MySQL이 MRR을 골라 조건에 맞는 항목을 **전부 모은 뒤** 행을 읽음 → `LIMIT`에서 못 멈춤(인천 '카페' 623건 전부 vs 힌트 19건, 13.9ms → 1.3ms)
  - 기준값 근거(구현 중 측정으로 10만 → 3만): 희귀어는 커버링 경로가 지역 인덱스를 다 훑음(1,000항목당 약 0.3ms) → 3만이면 +10ms 이내(C-r1-05 비악화 기준). 3만 초과 지역의 흔한 단어는 FTS로 약 `size / 지역 비율` 행(3% 지역 ≈ 680행), 부산(4.1만) '카페' p95 20.7ms
  - 합성 데이터 기준 서울·경기·전남광주·경북·경남·강원·충남·부산·전북·충북이 FTS 경로, 인천(2.8만) 이하 시·도와 모든 시·군·구는 커버링 경로
- 수용 기준: 작은 지역 + 흔한 단어, 큰 지역 + 희귀어 모두 REQ-002와 같은 `ROWS_EXAMINED` 판정. 커버링 경로는 추가로 `ROWS_EXAMINED` ≤ 지역 활성 건수(비용이 일치 건수가 아니라 지역 크기에 묶임, 일치가 늘면 더 일찍 멈춤). 같은 검색어·지역이면 두 경로의 후보 자격이 같음
- 수용 테스트 ID: AT-001, AT-004

### REQ-004 지역별 활성 장소 수 캐시

- 요구사항: `PlaceRegionStats`(@Component)가 `SELECT region_code, COUNT(*) FROM place WHERE active = 1 GROUP BY region_code`를 첫 사용 때 읽어 메모리에 둠. `PlaceImportService`가 적재 성공 직후 `invalidate()` → 다음 검색 때 다시 읽음
  - 경합(C-r1-04): 스냅숏을 세대 객체로 두고 `invalidate()`가 새 세대로 교체, 로더는 시작 때 본 세대에만 `compareAndSet`으로 게시 → invalidate 이전에 시작한 로더는 게시 못 함
  - 건수 조회는 `REQUIRES_NEW` 읽기 전용 트랜잭션 → 적재 커밋 전에 시작된 검색 트랜잭션의 옛 스냅숏으로 새 세대를 채우지 않음
  - 건수가 낡아도 경로 선택(성능)만 달라지고 결과는 같음 → 적재 성공 때만 갱신, 실패 때는 그대로
  - 읽기 실패 시 스냅숏을 두지 않고 예외 전파(다음 호출이 재시도) — `RegionCatalog`와 같은 방식
- 수용 기준: 적재 성공 후 검색이 새 건수로 경로를 고름, 로딩 중 invalidate된 결과는 게시되지 않음, 로드 실패 후 다음 호출이 재시도
- 수용 테스트 ID: AT-005

### REQ-005 인덱스 마이그레이션

- 요구사항: `V47__place_region_search_index.sql` — `ALTER TABLE place ADD INDEX idx_place_region_search (region_code, active, search_name), DROP INDEX idx_place_region_active, ALGORITHM=INPLACE, LOCK=NONE`
  - 새 인덱스가 같은 선두 컬럼을 가져 `idx_place_region_active`(30MB) 역할과 `fk_place_region`의 인덱스 요구를 대신함 → 버퍼 풀 경쟁 감소
  - 온라인 DDL이라 적재·쓰기를 막지 않음. 로컬 102만 행 4초
- 수용 기준: 마이그레이션 후 새 인덱스 존재, 옛 인덱스 없음, FK 유지(존재하지 않는 지역 코드 INSERT 거부)
- 수용 테스트 ID: AT-006

### REQ-006 검색 계약 유지

- 요구사항: 검색어 2~50자(공백 제거 기준)·`size` 1~50 검증, 지역 응답, 비활성 제외, 하위 지역 포함 필터, 응답 DTO 불변
  - 정확 일치 다음 순서: FTS 경로는 FTS 문서 번호 순(≈ 적재 순), 커버링 경로는 인덱스 순(지역 코드 → 이름). 계약 아님(intent)
- 수용 기준: 기존 `PlaceSearchDockerTest`·`TravelDestination*` 테스트 수정 없이 통과
- 수용 테스트 ID: AT-002

## 확정 인터페이스

### API 시그니처·DTO

- `GET /api/v1/places/search?q&region&size` 및 `PlaceSearchResponse` 변경 없음

### 내부

- `PlaceSearchService.search(query, regionCode, size)` 시그니처 유지, 의존성에 `PlaceRegionStats` 추가
- `PlaceSearchRepository`
  - `findExact(exact: String, regionCodes: Set<String>, limit: Int): List<PlaceSearchRow>`
  - `searchFullText(query: PlaceSearchQuery, regionCodes: Set<String>?, limit: Int): List<PlaceSearchRow>`
  - `searchRegionIndex(query: PlaceSearchQuery, regionCodes: Set<String>, limit: Int): List<PlaceSearchRow>`
- `PlaceSearchQuery`(값 객체, 순수 함수): `compact`, `terms: String?`(쓸 토큰 없으면 null), `likePattern` — 토큰화·불용어·이스케이프 규칙을 한곳에
- `PlaceRegionStats`: `activeCount(codes: Set<String>): Long`, `invalidate()`
- `RegionCatalog.searchableCodes(): Set<String>` 추가
- `PlaceImportService` 생성자에 `PlaceRegionStats` 추가(dockerTest `PlaceDb`도 맞춤)

### DB 스키마

- REQ-005의 V47만. 테이블·컬럼 변경 없음. H2 테스트는 Flyway를 쓰지 않아 영향 없음

## 수용 테스트

| ID | 종류 | 내용 |
|---|---|---|
| AT-001 | dockerTest `PlaceSearchCostDockerTest` | 일치 이름 N건 vs 10N건(N=2,000, 비일치 행·지역 분포·active 비율 고정, 일치 행을 id 순으로 고르게 섞음) 데이터에서 같은 검색 1회의 읽은 행 = `ROWS_EXAMINED` 합계(같은 연결의 `events_statements_history`) + `Handler_read_next` 증가분(지역 인덱스에서 조건 통과 후 돌려준 항목, MRR 회귀가 여기에만 보임), 건수 캐시는 미리 로드. 판정: 10N ≤ N × 1.2 + 20, 그리고 모든 경로 **검색 1회 ≤ size × 3(60행)**. 테스트 연결은 `mrr_cost_based=off`(MRR은 운영 규모에서만 비용상 선택됨). 인덱스 조건에서 걸러진 항목은 어느 지표에도 안 잡힘 → 커버링 경로의 지역 스캔은 기준값으로 지역 크기에 묶고 로컬 재현으로 측정. 경우: 전국 2글자, 전국 여러 글자, 작은 지역 + 흔한 단어(커버링), 큰 지역 + 흔한 단어·희귀어(FTS, 기준값을 낮춰 강제), 지역 내 결과가 size보다 적은 경우. 보조: 논리 페이지 증가분 ÷ 추가 일치 < 0.1(3회 중 최솟값). **현재 구현으로 돌려 실패 확인(회귀 검출)** |
| AT-002 | dockerTest 기존 `PlaceSearchDockerTest` | 서비스 생성 한 줄(`db.searchService()`) 외 수정 없이 통과 + 정확 일치가 `size`보다 많을 때 정확 일치만 `size`개 + 후보가 `size`보다 많을 때 결과 수·후보 자격·중복 없음·정확 일치 우선 |
| AT-003 | dockerTest | 후보 전체가 `size` 안인 fixture에서 영문·불용어 이름('PizzaHut', 'Asia Mart', 'GS25')과 특수문자 검색어(`"`, `%`, `_`, `!`)의 새 결과 집합 == 현재 구문 검색 결과 집합. `asia` + 정확 일치 'asia' 장소 → 빈 결과 |
| AT-004 | dockerTest | 후보 전체가 `size` 안인 fixture에서 같은 검색어·지역의 커버링 경로와 FTS 경로(기준값 조정으로 강제) 결과 집합이 같음. `asia` 케이스 포함 |
| AT-005 | 단위 + dockerTest | 단위: 적재 성공 시 `invalidate()` 호출·실패 시 미호출, 로딩 중 invalidate(latch로 순서 고정)된 결과 미게시, 로드 실패 후 재시도. dockerTest: 파일 A → B 적재(이름 변경·신규·누락·번호 바뀐 가게) 후 새 이름 나옴·옛 이름 안 나옴·신규 나옴·누락 안 나옴·재매칭 id 유지, FTS·커버링 경로 각각, 새 건수로 경로 선택 |
| AT-006 | dockerTest `RegionSchemaDockerTest` 확장 | V47 후 인덱스 구성·FK 동작 |
| AT-007 | 단위 `PlaceSearchQueryTest` | 2글자 토큰·중복 제거·불용어 포함 토큰 제외·쓸 토큰 없음 → null·큰따옴표 제거·LIKE 이스케이프 |

## 위험

- **배포 시 마이그레이션 시간**: 운영은 버퍼 풀이 작고 디스크가 느려 로컬 4초보다 길 수 있음(수십 초~수 분 추정). Flyway가 기동 중 실행해 그동안 앱이 안 뜸 → 사람이 한가한 시간에 배포
- **운영 불용어 설정 가정**: 운영 MySQL이 기본값(`innodb_ft_enable_stopword=ON`, 서버 불용어 테이블 없음)이라고 가정. 다르면 AT-003 동치가 운영에서 깨질 수 있음 → 배포 후 사람 확인 항목에 추가
- 합성 데이터의 지역 분포·이름 분포는 운영과 다를 수 있음 → 배포 후 운영 실측으로 확인(intent 사람 확인 항목)

## 마일스톤

1. V47 + `PlaceSearchQuery`(AT-007) + `RegionCatalog.searchableCodes`
2. `PlaceRegionStats` + 적재 연동(AT-005)
3. 저장소 3경로 + 서비스 조립(AT-002·003·004)
4. AT-001 비용 테스트(현재 구현으로 red 확인 → 새 구현 green), AT-006
5. 로컬 재현으로 개선 전·후 재측정 → result (C-r1-05)
   - 검색 1회 전체(정확 일치 + 나머지 문장) 서버 측 지연, 반복 20회 p50·p95 + 빈 버퍼 풀 1회, 논리·물리 읽기
   - 대상: '카페' 전국·서울·울릉군, '스타벅스'·'이디야커피' 전국, 희귀어 전국·부산, 건수 캐시 첫 로딩
   - **비악화 기준: 희귀어·여러 글자 검색어의 개선 후 p95 ≤ 개선 전 p95 + 10ms**
   - 운영 환산(물리 읽기 × 1.7ms)은 참고치, 운영 p95 확정은 배포 후 사람 실측

## 구현 후 로컬 재현 결과 (2026-11-10, 검색 1회 전체 = 정확 일치 + 나머지 문장, 반복 20회, 서버 측)

| 검색 | 개선 전 p50 / p95 | 개선 후 p50 / p95 | 논리 읽기 전 → 후 |
|---|---|---|---|
| '카페' 전국 | 131.2 / 343.5ms | 15.5 / 38.1ms | 129,162 → 1,196 |
| '카페' 서울(FTS) | 133.7 / 152.0ms | 14.8 / 26.9ms | 129,689 → 1,464 |
| '카페' 부산(FTS) | 132.6 / 416.3ms | 16.0 / 20.7ms | 120,951 → 3,019 |
| '카페' 인천(지역 인덱스) | 132.3 / 139.4ms | 1.1 / 1.4ms | 120,890 → 391 |
| '카페' 울릉군(지역 인덱스) | 123.1 / 138.7ms | 0.7 / 1.6ms | 129,073 → 136 |
| '스타벅스' 전국 | 26.3 / 76.2ms | 4.9 / 8.5ms | 20,269 → 1,731 |
| '이디야커피' 전국 | 63.9 / 309.2ms | 12.1 / 17.4ms | 30,703 → 2,015 |
| '막창' 전국 | 11.4 / 87.8ms | 2.6 / 4.7ms | 8,573 → 1,222 |
| 희귀어 전국 | 0.5 / 0.8ms | 1.9 / 3.7ms (+2.9) | 525 → 1,380 |
| 희귀어 부산(FTS) | 0.6 / 1.4ms | 1.0 / 1.3ms | 679 → 962 |
| 희귀어 인천(지역 인덱스, 기준값 근처 최악) | 0.6 / 1.2ms | 8.4 / 9.0ms (+7.8) | 633 → 434 |

- 개선 전 반복 측정에서도 '카페'류는 매번 물리 읽기 5,500~7,000회(버퍼 풀 thrashing), 개선 후 반복 시 물리 읽기 0
- 비악화 기준(희귀어·여러 글자 p95 ≤ 개선 전 + 10ms) 전부 충족
- 건수 캐시 첫 로딩(`GROUP BY region_code`): p50 154ms, 논리 30,508 — 기동 후·적재 후 첫 지역 필터 검색 1회에만
