# Spec 변경 로그

| 일시 | 요구사항 ID | 변경 | 이유 | 메인 설계자 |
|---|---|---|---|---|

- 2026-11-22 C-r1-01 수용: `place.tour.service-key` 를 `application.yml` 에서 `${PLACE_TOUR_SERVICE_KEY:}` 로 명시 매핑, 변경 파일과 AT-003 바인딩 테스트 추가
- 2026-11-22 C-r1-02 기각-감수: "최악 지연 DB + 3초 미만" 문장 삭제, 읽기 타임아웃의 실제 한계를 적음. FAILED 로그에 걸린 시간 추가
- 2026-11-22 구현 중 발견: OSIV 기본값이 트랜잭션 후에도 커넥션을 잡음(AT-004 실패). REQ-004에 OSIV 예외 경로 추가, `OpenInViewConfig` 와 `open-in-view: false` 를 변경 파일에 추가
