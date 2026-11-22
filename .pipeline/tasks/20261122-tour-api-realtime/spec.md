# Spec

## 전제 (사람 결정 2026-11-22)

- 관광공사 실제 응답 확인은 하지 않고 공공데이터포털 Swagger(`15101578`) 기준으로 구현. Swagger에 없거나 불명확한 것은 방어적으로 처리하고, 운영 키 등록 후 확인 항목으로 `result.md` 에 남김
  - `searchKeyword2` 에 관광타입(`contentTypeId`) 파라미터 없음 → 받은 뒤 `contenttypeid` 로 거름
  - 지역 코드 연결 = `lDongRegnCd`(2자리) + `lDongSignguCd`(3자리) → 우리 5자리 법정동 코드로 가정, 맞지 않으면 시도로 내림

## 요구사항

### REQ-001 관광공사 키워드 검색 클라이언트

- 요구사항: `GET {baseUrl}/searchKeyword2` 를 `serviceKey`, `MobileOS=ETC`, `MobileApp`, `_type=json`, `keyword`, `numOfRows`, `pageNo=1`, 선택 `lDongRegnCd` 로 호출하고 항목 목록을 돌려줌
  - `serviceKey` 는 포털의 Decoding 키(`+`, `/`, `=` 포함)를 받아 **한 번만** 퍼센트 인코딩(`+` → `%2B`). 이중 인코딩과 미인코딩 모두 인증 실패가 됨
  - 성공 = HTTP 2xx + JSON 파싱 성공 + `response.header.resultCode == "0000"`
  - 결과 0건 응답의 `items` 가 빈 문자열(`""`)이거나 `item` 이 배열이 아닌 단일 객체여도 처리
  - 그 밖의 모든 경우(비 2xx, XML 오류 본문, 파싱 실패, `resultCode` 불일치, 연결과 읽기 타임아웃) = `TourApiException`
  - 예외 메시지와 로그에 서비스키나 키가 든 URI를 절대 넣지 않음(`RestClientException` 메시지는 요청 URI를 포함하므로 그 메시지를 쓰지 않음)
- 수용 기준: 위 파라미터로 한 번 요청, 키 인코딩 1회, 항목 파싱, 실패 유형별 `TourApiException`, 예외 메시지에 키 없음
- 수용 테스트 ID: AT-001 (`TourApiClientTest`, `MockRestServiceServer`)

### REQ-002 관광지 결과 가공

- 요구사항: `TourPlaceSearch` 가 클라이언트 결과를 응답 항목으로 바꿈
  - `contenttypeid` 가 12(관광지), 14(문화시설), 28(레포츠)인 항목만 남김. 분류명은 이 세 값을 한글 이름으로 매핑
  - 지역: `lDongRegnCd + lDongSignguCd` 가 카탈로그에 있고 `searchable` 이면 그 코드, 아니면 `lDongRegnCd` 가 searchable 시도면 시도, 둘 다 아니면 항목 버림
  - 좌표 `mapx`(경도), `mapy`(위도)가 숫자가 아니면 항목 버림
  - 지역 필터가 있으면 API에는 시도(`regionCode` 앞 2자리)만 넘기고, 결과는 `regionCatalog.isWithin(항목 지역, 필터)` 로 다시 거름(일반구와 시군구 코드 체계 차이를 API에 맡기지 않음)
  - 요청 건수 = 검색의 `size`. 타입과 지역으로 거른 뒤 `size` 보다 적을 수 있음(한 번만 호출, 추가 페이지 없음)
  - 이미지(`firstimage`)는 응답에 넣지 않음(78주차 회의 결정: DTO 이미지 제외)
- 수용 기준: 타입 필터, 지역 매핑 3단계(정확, 시도로 내림, 버림), 일반구 포함 지역 필터, 잘못된 좌표 버림
- 수용 테스트 ID: AT-002 (`TourPlaceSearchTest`)

### REQ-003 장애와 비활성 처리

- 요구사항
  - `TourApiException` 또는 예상하지 못한 `RuntimeException` → 관광지 결과 비움, 상태 `FAILED`, `log.warn` 1줄(원인 분류와 걸린 시간 ms만, 키와 URI 없음). Discord 알림은 보내지 않음(한도 초과가 연속 알림 폭주가 됨). 걸린 시간은 C-r1-02 재검토 근거(느린 응답 관측 시 총 시간 상한 추가)
  - 서비스키가 비어 있으면 호출하지 않고 상태 `DISABLED`
  - 성공 시 상태 `OK`(0건이어도 `OK`)
- 수용 기준: 실패 유형마다 200 응답 + 지역과 가게 결과 그대로 + `tourStatus=FAILED`, 키 없음 → 호출 0회 + `DISABLED`
- 수용 기준 추가(C-r1-01): `PLACE_TOUR_SERVICE_KEY` 이름으로 값을 주면 `TourApiProperties.serviceKey` 에 들어가고, 없으면 빈 문자열
- 수용 테스트 ID: AT-003 (`TourPlaceSearchTest`, `PlaceSearchTourApiIntegrationTest`, `TourApiPropertiesBindingTest`)

### REQ-004 DB 트랜잭션 밖에서 외부 호출

- 요구사항: 관광공사 호출 동안 트랜잭션이 없고 Hikari 활성 커넥션을 잡고 있지 않음
  - 새 `PlaceSearchFacade`(트랜잭션 없음)가 ① 기존 `PlaceSearchService.search`(`@Transactional(readOnly = true)`, 검증과 DB 검색) ② `TourPlaceSearch.search` 순서로 호출하고 결과를 합침
  - 검증 실패(검색어 길이, `size`, 알 수 없는 지역)는 ①에서 400으로 끝나 관광공사를 호출하지 않음
  - 컨트롤러는 `PlaceSearchFacade` 를 호출
  - **OSIV 예외 경로 (구현 중 발견, 2026-11-22)**: Spring Boot 기본 `open-in-view=true` 에서는 `HibernateJpaVendorAdapter` 가 연결 방식을 `DELAYED_ACQUISITION_AND_HOLD` 로 두어, 읽기 트랜잭션이 끝나도 커넥션을 요청 끝까지 잡음 → 관광공사 대기 중에도 커넥션 1개 점유(AT-004 실패로 확인, `open-in-view=false` 로 재실행 시 통과)
  - 수정: `spring.jpa.open-in-view=false` 로 Boot 자동 등록을 끄고, 같은 `OpenEntityManagerInViewInterceptor` 를 직접 등록하되 `/api/v1/places/search` 만 제외. 다른 모든 경로의 OSIV 동작은 그대로
  - 기각: 전역 OSIV 끄기(다른 API의 지연 로딩 영향 범위가 이 태스크 밖), 관광공사를 DB 검색보다 먼저 호출(검증과 지역 카탈로그 첫 로딩이 DB를 쓰면 다시 커넥션을 잡게 되어 순서에 기대는 취약한 구조)
- 수용 기준: 테스트 대역 안에서 `TransactionSynchronizationManager.isActualTransactionActive() == false` 이고 `HikariPoolMXBean.activeConnections == 0`(OSIV 기본값 켜짐 상태 그대로에서 확인), 검증 실패 시 대역 호출 0회
- 수용 테스트 ID: AT-004 (`PlaceSearchTourApiIntegrationTest`)

### REQ-005 응답 확장 (추가만)

- 요구사항: `PlaceSearchResponse` 에 `tourPlaces`, `tourStatus` 추가. 기존 `regions`, `places` 의 이름, 형식, 순서 불변
- 수용 기준: 키가 있을 때 `GET /places/search?q=경복궁` 응답 JSON에 `tourPlaces[0].contentId/name/categoryName/address/lat/lng/region` 과 `tourStatus=OK`
- 수용 테스트 ID: AT-005 (`PlaceSearchTourApiIntegrationTest`), 기존 `PlaceSearchServicePathTest`, `TravelDestinationApiIntegrationTest` 통과 유지

## 확정 인터페이스

### API 시그니처, DTO

```kotlin
// GET /places/search?q=&region=&size=  (변경 없음)
data class PlaceSearchResponse(
    val regions: List<RegionResponse>,
    val places: List<PlaceSearchItem>,
    val tourPlaces: List<TourPlaceItem> = emptyList(),
    val tourStatus: TourSearchStatus = TourSearchStatus.DISABLED,
)

data class TourPlaceItem(
    val contentId: String,
    val name: String,          // title
    val categoryName: String,  // 관광지 | 문화시설 | 레포츠
    val address: String,       // addr1 (빈 문자열 허용)
    val lat: Double,           // mapy
    val lng: Double,           // mapx
    val region: RegionResponse,
)

enum class TourSearchStatus { OK, FAILED, DISABLED }
```

### 설정 (`place.tour`, `@ConfigurationProperties`)

| 키 | 기본값 | 비고 |
|---|---|---|
| `base-url` | `https://apis.data.go.kr/B551011/KorService2` | |
| `service-key` | `""` | `application.yml` 에 `service-key: ${PLACE_TOUR_SERVICE_KEY:}` 로 명시 매핑(relaxed binding의 표준 이름은 `PLACE_TOUR_SERVICEKEY` 라 `PLACE_TOUR_SERVICE_KEY` 가 자동으로 묶이지 않음, C-r1-01). 운영 등록은 사람, 비면 비활성 |
| `mobile-app` | `meetjyou` | 필수 파라미터 |
| `connect-timeout` | 1초 | |
| `read-timeout` | 2초 | 읽기 사이 대기 한도일 뿐 응답 전체 시간 상한이 아님. 연결 실패와 응답 멈춤은 잡지만, 조금씩 끝없이 흘려보내는 응답은 3초를 넘길 수 있음(C-r1-02, 사람 결정으로 감수). 총 시간 상한은 두지 않음 |

### 파일

- 신규 `place/tour/`: `TourApiProperties`, `TourApiConfig`(RestClient 빌드, 타임아웃, URI 인코딩), `TourPlaceSource`(인터페이스, 테스트 대역 교체 지점), `TourApiClient`, `TourApiException`, `TourPlace`(원본 항목), `TourPlaceSearch`, `TourContentType`
- 신규 `place/PlaceSearchFacade.kt`, `place/dto/TourPlaceItem.kt`
- 변경 `PlaceController`(facade 호출), `PlaceSearchResponse`(필드 추가)
- 변경 `src/main/resources/application.yml`: `place.tour.service-key: ${PLACE_TOUR_SERVICE_KEY:}`, `spring.jpa.open-in-view: false`
- 신규 `config/OpenInViewConfig.kt`: OSIV 인터셉터 직접 등록, 검색 경로만 제외
- 변경 `.claude/rules/place.md`: 관광공사 실시간 검색 규칙(저장 금지, 트랜잭션 밖 호출, 키 로그 금지) 추가

### DB 스키마

- 변경 없음 (migration 없음)

## 마일스톤

1. 클라이언트와 설정 (AT-001)
2. 가공과 장애 처리 (AT-002, AT-003 단위)
3. facade, 응답 확장, 통합 테스트 (AT-003 통합, AT-004, AT-005)
4. 규칙 문서, `./gradlew test build`
