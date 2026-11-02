# Spec 변경 로그

| 일시 | 요구사항 ID | 변경 | 이유 | 메인 설계자 |
|---|---|---|---|---|
| 2026-11-02 | REQ-001~005 | 초안 작성 | intent 승인(PR #242) | claude-opus-5-5 |
| 2026-11-02 | REQ-005~007, 인터페이스 | Post locking read, deletePlan detach 순서, 규칙 문서 범위 확장 | 비평 1라운드 C1-1·C1-2 수용 | claude-opus-5-5 |
| 2026-11-02 | REQ-005 | 규칙 문단 정리·기존 테스트 갱신 명시 | 비평 2라운드 C2-1·C2-2 수용 | claude-opus-5-5 |
| 2026-11-02 | REQ-007, REQ-005 | plan 전환 편집 ↔ deletePlan을 accepted deadlock(409)으로 전환, plan 유지 편집 무교착 기준 추가 | T-01 구현자 escalation: party-first 정렬만으로 plan_id 보조 인덱스 순환 대기 미해소(MySQL 실측) | claude-opus-5-5 |
| 2026-11-02 | REQ-007 | 근거 문단을 개정 수용 기준(accepted deadlock)과 일치 | T-01 리뷰 nit F2 | claude-opus-5-5 |
