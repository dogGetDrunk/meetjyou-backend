### V-r1-01 Unvalidated resultCode reaches exception messages and logs

- **Kind:** an acceptance criterion not met.
- **Grounds:** `src/main/java/com/dogGetDrunk/meetjyou/place/tour/TourApiClient.kt:27–29` copies the upstream `resultCode` verbatim into `TourApiException.message`. `src/main/java/com/dogGetDrunk/meetjyou/place/tour/TourPlaceSearch.kt:40–51` then logs that message. A malformed response whose `resultCode` contains a request URI therefore exposes its service key through both paths.
- **Decision broken:** REQ-001 / AT-001, `spec.md:18`: “예외 메시지와 로그에 서비스키나 키가 든 URI를 절대 넣지 않음”; REQ-003, `spec.md:37`: “원인 분류와 걸린 시간 ms만, 키와 URI 없음”.
- **Impact:** The promised key-free exception and logging boundary is not enforced for malformed JSON error codes.

### V-r1-02 NaN coordinates survive the invalid-coordinate filter

- **Kind:** an acceptance criterion not met.
- **Grounds:** `src/main/java/com/dogGetDrunk/meetjyou/place/tour/TourPlaceSearch.kt:66–69` accepts any non-null `toDoubleOrNull()` result except zero. Kotlin parses `"NaN"` as `Double.NaN`, which passes both checks. An otherwise valid item with `mapx="NaN"` therefore remains in `tourPlaces` with status `OK`.
- **Decision broken:** REQ-002 / AT-002, `spec.md:27`: “좌표 `mapx`(경도), `mapy`(위도)가 숫자가 아니면 항목 버림”; acceptance criterion at `spec.md:31`: “잘못된 좌표 버림”.
- **Impact:** Invalid locations reach clients instead of being discarded; their coordinates cannot be used as numeric map positions.