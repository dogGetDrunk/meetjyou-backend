# Spec 비평 결정

| 라운드 (최대 2) | 항목 | 수용/기각 | 근거 | 메인 설계자 |
|---|---|---|---|---|
| 1 | C1-1 (blocking) 파티 락 후 Post stale snapshot 읽기 → Post·Party 불일치 | 수용 | `findUuidByPostUuid`가 RR snapshot 생성, 이후 일반 조회는 락 대기 중 커밋 미반영 + `@DynamicUpdate` dirty check 누락. `PartyRowLock`과 같은 `PostRowLock` 도입(REQ-006). 기각: 락 없는 `refresh`(같은 snapshot 재조회, party.md 금지 패턴) | claude-opus-5-5 |
| 1 | C1-2 (blocking) 새 순서(party→post)로 `deletePlan ↔ updatePost` 교착 재발 | 수용 | `deletePlan` detach 순서 party → post 정렬(REQ-007). 기각: 409 수용 목록 등재 — #201로 제거된 교착을 되살림, 정렬 비용은 2줄 + 기존 테스트 2곳 갱신 | claude-opus-5-5 |
| 2 | C2-1 (nit) party.md `deletePlan` 괄호 설명·Pinned by 포인터 잔존 | 수용 | 규칙 문서 내부 모순 방지, REQ-005에 명시 | claude-opus-5-5 |
| 2 | C2-2 (nit) 기존 테스트의 `findByUuid` stub·순서 검증 불일치 | 수용 | 구현 티켓 범위에 기존 테스트 갱신 명시, REQ-005에 추가 | claude-opus-5-5 |
| - | 프로세스: 엔진에 test-writer 단계 없음, 워커는 origin/main 분기 → 수용 테스트 코드 반입 경로 | 사람 결정 (2026-11-02, "태스크 폴더 경유") | Terra가 별도 세션에서 `tests/code/<src 경로>`에 전체 파일 작성 → 구현자는 지정 경로로 그대로 복사, 수정 금지 → 리뷰어가 바이트 일치 확인. 기각: Terra 생략(§5.5 세션 분리 위반), 엔진 개선 선행(범위 확대·태스크 보류), main 선반영(빨간 테스트로 CI 실패) | claude-opus-5-5 |
| - | 수용 테스트 코드 잔여 공백 (AT-001-3·002-2·002-3 plan/compPreference 보존, AT-004-1 일정 보존, AT-007-2 marker 삭제, AT-006 일반 경로 변형 축약) | 수용 (보조 단언 공백 허용) | 9개 보호 파일 모두 작성, 각 AT 핵심 Then(최종 값·예외·락 대기) 단언 확보. 4차 Terra 실행 비용 대비 이득 작음. 공백은 리뷰어 nit 확인 항목으로 이관, 구현자 수정 금지 유지. 컴파일 미검증 → 컴파일 오류는 구현자가 escalation | claude-opus-5-5 |
| - | escalation (T-01 구현자): 보호 테스트 PostPartySyncIntegrationTest 결함 — detekt MaxLineLength 16건, spyk(Spring Data JDK proxy) IllegalAccessException | 수용, 메인 설계가 tests/code 수정 | 개행·헬퍼 추출로 120자 이하, spyk → mockk<CompPreferenceRepository>(relaxed = true). AT-001-3의 compPreference C0 보존 단언은 원래 없었고 Post/Party 롤백 단언 유지 → 검증 범위 손실 없음. 기각: baseline 추가(CLAUDE.md 금지), --add-opens(공유 proxy 계측으로 타 테스트 파손 보고) | claude-opus-5-5 |
