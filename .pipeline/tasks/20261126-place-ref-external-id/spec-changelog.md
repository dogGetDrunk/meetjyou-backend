# Spec 변경 로그

| 일시 | 요구사항 ID | 변경 | 이유 | 메인 설계자 |
|---|---|---|---|---|
| 2026-11-26 | REQ-001 | V48 하나 → V48(post), V49(party), V50(plan) 테이블별 분리, AT-001에 출처 전환 UPDATE 추가 | C-r1-06, C-r1-04 | claude-opus-5-5 |
| 2026-11-26 | REQ-004 | JSON 토큰 계약, 출처 전환 시나리오, 관광공사 호출 0회 단언 추가 | C-r1-03, C-r1-04, C-r1-07 | claude-opus-5-5 |
| 2026-11-26 | REQ-005 | 응답 9개별 단언 연결 | C-r1-02 | claude-opus-5-5 |
| 2026-11-26 | REQ-006 | 표시 필드를 모집글 `location`, 파티와 계획서 `destination` 으로 정정 | C-r1-08 | claude-opus-5-5 |
| 2026-11-26 | 위험, 마일스톤 | 멱등 해시 변경 감수 기록, `./gradlew dockerTest` 실행 마일스톤 추가 | C-r1-01, C-r1-05 | claude-opus-5-5 |
| 2026-11-26 | REQ-005, REQ-004 | AT-004를 별도 `GetPlanResponseSnapshotCompatTest` 대신 `TourContentRefApiIntegrationTest` 안에서 앱의 실제 `ObjectMapper` 빈으로 확인, AT-003 파일명도 같은 클래스로 확정 | 스냅샷 역직렬화 실패는 `runCatching` 으로 조용히 null이 되므로 손으로 만든 매퍼가 아닌 앱 빈으로 확인해야 의미가 있음 | claude-opus-5-5 |
| 2026-11-26 | 위험 | 멱등 해시 감수 범위에 `CreatePlanRequest` 추가 | V-r1-01(사람 승인) | claude-opus-5-5 |
