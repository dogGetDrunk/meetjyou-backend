# Spec

## 요구사항

### REQ-001 한도 초과 구분

- 요구사항: 게이트웨이 XML `returnReasonCode=22` 와 JSON `header.resultCode=22` 를 `TourApiException.Reason.QUOTA_EXCEEDED` 로 던짐. 다른 코드는 지금처럼 `RESULT_CODE`
- 수용 기준: XML 22 → `QUOTA_EXCEEDED`, JSON `22` → `QUOTA_EXCEEDED`, JSON `0003`, XML 30 → `RESULT_CODE`. 메시지에 서비스키 없음
- 수용 테스트 ID: AT-001 (`TourApiClientTest` "실패")

### REQ-002 한도 초과 뒤 호출 중지

- 요구사항: `TourPlaceSearch` 가 `QUOTA_EXCEEDED` 를 받으면 `resumeAt = min(다음 KST 자정, 지금 + 1시간)` 까지 source를 호출하지 않고 `FAILED` 를 돌려줌
  - 중지 시작 때 warn 1줄(`reason=QUOTA_EXCEEDED`, `resumeAt`, `elapsedMs`), 중지 중 검색은 로그 없음
  - `resumeAt` 이 지나면 다음 검색에서 다시 호출, 또 22면 새 `resumeAt` 으로 다시 중지
  - 다른 실패(`UNREACHABLE`, `HTTP_STATUS`, `BAD_BODY`, `RESULT_CODE`, 예상 못 한 예외)는 중지 없음
  - 상태 = `AtomicReference<Instant?>`, 시각 = 주입한 `Clock`(기본 `Clock.systemUTC()`)
- 수용 기준:
  - 22 후 같은 시각 검색 → source 호출 0회, `FAILED`
  - 14:00 KST에 22 → 14:59:59 호출 0회, 15:00 호출 1회
  - 23:30 KST에 22 → 23:59:59 호출 0회, 다음 날 00:00 호출 1회
  - `UNREACHABLE` 뒤 다음 검색 → 호출 1회
  - `DISABLED` 는 지금과 같음
- 수용 테스트 ID: AT-002 (`TourPlaceSearchTest` "한도 초과")

### REQ-003 일반 시군구 필터에 시군구 코드 전달

- 요구사항: 지역 필터가 SIGUNGU이고, 부모가 시도(코드 2자리)이고, 하위 지역이 없으면 `lDongSignguCd` = 지역 코드 뒤 3자리를 함께 전달. 그 밖(시도, 일반구, 일반구를 둔 시, 필터 없음)은 지금과 같음
  - 결과 재필터(`isWithin`)는 유지
- 수용 기준:
  - `11110` 종로구 → source에 (`11`, `110`), URI에 `lDongRegnCd=11&...lDongSignguCd=110`
  - `41110` 수원시(일반구 있음) → (`41`, null), `41111` 장안구(부모가 시) → (`41`, null), `11` → (`11`, null), null → (null, null)
  - 시군구 코드가 없으면 URI에 `lDongSignguCd` 없음
- 수용 테스트 ID: AT-003 (`TourPlaceSearchTest` "지역 필터", `TourApiClientTest` "검색 요청")

## 확정 인터페이스

### API 시그니처·DTO

- `TourPlaceSource.searchKeyword(keyword: String, sidoCode: String?, signguCode: String?, size: Int): List<TourPlace>`
- `TourApiException.Reason.QUOTA_EXCEEDED` 추가
- `TourPlaceSearch(source, regionCatalog, clock: Clock = Clock.systemUTC())`
- `RegionCatalog.hasChildren(code: String): Boolean` 추가
- HTTP 응답 형식 변경 없음 (`tourStatus` 값 그대로)

### DB 스키마

- 변경 없음

## 변경 파일

- `place/tour/TourApiException.kt`, `TourApiClient.kt`, `TourPlace.kt`, `TourPlaceSearch.kt`
- `place/RegionCatalog.kt`
- 테스트: `TourApiClientTest.kt`, `TourPlaceSearchTest.kt`, `PlaceSearchTourApiIntegrationTest.kt`(fake 시그니처만)
- `.claude/rules/place.md` 관광공사 절

## 마일스톤

- M1: REQ-001~003 구현과 테스트, `agent-pipeline gate` 통과
