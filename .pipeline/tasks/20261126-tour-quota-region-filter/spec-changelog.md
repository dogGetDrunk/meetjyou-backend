# Spec 변경 로그

| 일시 | 요구사항 ID | 변경 | 이유 | 메인 설계자 |
|---|---|---|---|---|
| 2026-11-26 | REQ-002 | 중지 상태와 시각 계산을 `TourPlaceSearch` 에서 새 클래스 `TourQuotaPause`(`place/tour`)로 이동, 동작과 공개 인터페이스는 그대로 | detekt `TooManyFunctions`(클래스당 11개) 초과. 억제 대신 응집된 책임으로 분리 | Claude Opus 5.5 |
