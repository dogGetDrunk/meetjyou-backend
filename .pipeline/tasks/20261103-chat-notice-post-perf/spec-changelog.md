# Spec 변경 로그

| 일시 | 요구사항 ID | 변경 | 이유 | 메인 설계자 |
|---|---|---|---|---|
| 2026-11-03 | REQ-001 | 조인 동등성 근거·실제 SQL 1회 통합 검증·테스트 데이터 다양화 | C-r1-05, C-r1-07 | Claude |
| 2026-11-03 | REQ-002 | DTO 생성까지 읽기 경계 안, 호출 경로 계약 한정, AT-002-3 실제 롤백 테스트 | C-r1-04 | Claude |
| 2026-11-03 | REQ-003 | 설정 SELECT 실제 SQL 1회 통합 검증, 공지·약관 각각 | C-r1-05 | Claude |
| 2026-11-03 | REQ-004 | 실제 API 경유·부분 flush 후 실패 주입, catch 범위, 알림 source에 uuid 포함 | C-r1-02, C-r1-03 | Claude |
| 2026-11-03 | REQ-005 | 약관 `effectiveAt <= now` 조건, API별 404·403, 동시성 2×2 + `data_lock_waits` | C-r1-01, C-r1-06 | Claude |
| 2026-11-03 | 검증 | dockerTest 완료 증거·CI 대체 규칙 | C-r1-08 | Claude |
| 2026-11-04 | REQ-003~005 | 발송 빈·Repository·예외·핸들러·재발송 서비스 시그니처, 테스트 지원 계약(StatementInspector 실패 주입·SQL 계수, 고정 Clock) 확정 | test-writer 5건이 "구현 재량" 인터페이스로 보호 테스트 작성 불가 escalation | Claude |
| 2026-11-04 | REQ-004 | 통합 테스트 beforeCommit 실패 주입 변형 삭제 → 커밋 시점 예외 격리는 핸들러 단위 테스트(AT-004-3)로 | 구현 내부 훅 없이 주입 불가, 핸들러 try/catch가 프록시 호출 전체를 감싸는 구조라 단위로 충분 | Claude |
| 2026-11-04 | REQ-001 | AT-001-2 측정 대상을 unread 집계 SELECT(`chat_message` + `count(`)로 한정 | 최신 메시지 조회도 `chat_message`를 읽어 "SELECT 전체 1회" 달성 불가, 해당 조회는 범위 밖 | Claude |
| 2026-11-04 | 전체 | 티켓·AT 경로 `src/*/kotlin` → `src/*/java` | 이 레포 Kotlin 소스 위치는 `src/main/java` | Claude |
| 2026-11-04 | REQ-002·004·005 | AT-002·004-1·004-2·005-2 케이스에 test-writer 단계 완료 기준 명시(대상 클래스 부재·소스셋 밖 컴파일 불가는 실패 사유 아님), AT-002-2 "비로그인" → "유저 row 없음", AT-004-1 tx 종료 실패 문구 제거 | 같은 조건에서 워커별 성공/실패 판정이 갈려 check-tests 차단 반복 | Claude |
| 2026-11-04 | 전체 | 보호 테스트 10개 파일 detekt 위반 62건 정리(줄바꿈·헬퍼 추출·`check()`/`error()`·파라미터 묶음), 단언 불변 | 구현 GREEN인데 보호 테스트 lint로 CI 실패 → T-01·T-02 재시도 max까지 반복 | Claude |
| 2026-11-04 | REQ-004 | AT-004-2 `@DirtiesContext` 제거(잘못된 import + 공유 H2 스키마 삭제로 무관 party 테스트 8건 실패) | T-03 워커 escalation, 별도 property로 이미 독립 context | Claude |
| 2026-11-04 | REQ-002 | AT-002-3 실패 주입을 `callRealMethod()` → spy 기본 answer 위임으로 교체, 단언 불변 | Spring Data 인터페이스 spy라 `callRealMethod` 불가(MockitoException) → 주입 미발동, `runCatching` 제거 시 RED 확인 | Claude |
