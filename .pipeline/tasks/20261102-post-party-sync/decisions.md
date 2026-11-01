# Spec 비평 결정

| 라운드 (최대 2) | 항목 | 수용/기각 | 근거 | 메인 설계자 |
|---|---|---|---|---|
| 1 | C1-1 (blocking) 파티 락 후 Post stale snapshot 읽기 → Post·Party 불일치 | 수용 | `findUuidByPostUuid`가 RR snapshot 생성, 이후 일반 조회는 락 대기 중 커밋 미반영 + `@DynamicUpdate` dirty check 누락. `PartyRowLock`과 같은 `PostRowLock` 도입(REQ-006). 기각: 락 없는 `refresh`(같은 snapshot 재조회, party.md 금지 패턴) | claude-opus-5-5 |
| 1 | C1-2 (blocking) 새 순서(party→post)로 `deletePlan ↔ updatePost` 교착 재발 | 수용 | `deletePlan` detach 순서 party → post 정렬(REQ-007). 기각: 409 수용 목록 등재 — #201로 제거된 교착을 되살림, 정렬 비용은 2줄 + 기존 테스트 2곳 갱신 | claude-opus-5-5 |
| 2 | C2-1 (nit) party.md `deletePlan` 괄호 설명·Pinned by 포인터 잔존 | 수용 | 규칙 문서 내부 모순 방지, REQ-005에 명시 | claude-opus-5-5 |
| 2 | C2-2 (nit) 기존 테스트의 `findByUuid` stub·순서 검증 불일치 | 수용 | 구현 티켓 범위에 기존 테스트 갱신 명시, REQ-005에 추가 | claude-opus-5-5 |
