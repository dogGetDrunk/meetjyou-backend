# Spec 변경 로그

| 일시 | 요구사항 ID | 변경 | 이유 | 메인 설계자 |
|---|---|---|---|---|
| 2026-11-26 | REQ-001 | V48 하나 → V48(post), V49(party), V50(plan) 테이블별 분리, AT-001에 출처 전환 UPDATE 추가 | C-r1-06, C-r1-04 | claude-opus-5-5 |
| 2026-11-26 | REQ-004 | JSON 토큰 계약, 출처 전환 시나리오, 관광공사 호출 0회 단언 추가 | C-r1-03, C-r1-04, C-r1-07 | claude-opus-5-5 |
| 2026-11-26 | REQ-005 | 응답 9개별 단언 연결 | C-r1-02 | claude-opus-5-5 |
| 2026-11-26 | REQ-006 | 표시 필드를 모집글 `location`, 파티와 계획서 `destination` 으로 정정 | C-r1-08 | claude-opus-5-5 |
| 2026-11-26 | 위험, 마일스톤 | 멱등 해시 변경 감수 기록, `./gradlew dockerTest` 실행 마일스톤 추가 | C-r1-01, C-r1-05 | claude-opus-5-5 |
