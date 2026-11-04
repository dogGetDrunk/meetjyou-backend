# Spec 비평 결정

| 라운드 (최대 2) | 항목 | 수용/기각 | 근거 | 메인 설계자 |
|---|---|---|---|---|
| r1 | C-r1-01 | 수용 | `.claude/rules/terms.md` 유효 약관 정의 = `ACTIVE && effectiveAt <= now`. 재발송 허용 조건에 `effectiveAt <= now` 추가, 판정은 약관 row 잠금 후 발송 트랜잭션 안. 원 발송(발행 시)의 동작은 불변 | Claude (main designer) |
| r1 | C-r1-02 | 기각 | `DiscordAlertService.deliver`가 webhook 전송을 `CompletableFuture.runAsync` 안 try/catch로 처리, webhook 미설정 시 즉시 return → 알림 실패가 호출부로 전파되지 않음. 별도 격리는 불필요한 방어 코드. 대신 비평 검토 중 발견: dedup 키 `status:exceptionClass:source` 때문에 uuid 없는 source면 다른 공지 실패 알림이 누락됨 → source에 uuid 포함을 spec에 추가 | Claude (main designer) |
| r1 | C-r1-03 | 수용 | 핸들러 직접 호출·테스트 트랜잭션은 AFTER_COMMIT 예외 격리와 전량 롤백을 입증하지 못함. 실제 API 경유, 첫 insert flush 후 실패 주입, 새 트랜잭션에서 결과 확인을 AT-004에 명시, catch 범위에 프록시 커밋 시점 예외 포함 | Claude (main designer) |
| r1 | C-r1-04 | 수용 | DTO 생성까지 읽기 경계 안에서 완료 후 조회수 증가. 상위 트랜잭션 호출은 미지원으로 계약 한정 — 호출부는 `PostController` 1곳뿐, `PostService`에 클래스 수준 `@Transactional` 없음. AT-002-3을 실제 롤백 통합 테스트로 변경 | Claude (main designer) |
| r1 | C-r1-05 | 수용 | intent 성공 기준이 쿼리 횟수 고정이라 Repository mock 호출 수로는 부족(지연 로딩 SQL 미검출). 대상 테이블 SELECT만 세는 통합 검증 추가, 전체 API SQL 수 고정은 하지 않음(취약 단언 방지) | Claude (main designer) |
| r1 | C-r1-06 | 수용 | 공지·약관은 별도 잠금 구현이라 한쪽 누락 가능 → 2 경로 × 2 시나리오, `data_lock_waits` 폴링, 404·403 API별 매핑. 단 "선행 발송 실패 후 대기 중 재발송 복구" 시나리오는 제외 — 0건 상태 재발송(AT-005-1·2)과 실패 롤백(AT-004)의 조합으로 충분, 추가 dockerTest 비용 대비 새 위험 없음 | Claude (main designer) |
| r1 | C-r1-07 | 기각 | 목록 방은 요청자 JOINED 멤버십의 party에서 도출(`findAllWithPartyByUserUuidAndMemberStatus(JOINED)` → `findAllWithPartyByPartyUuidIn`), `uk_user_party(user_id, party_id)`(V8)로 (요청자, party)당 row 1개 → 요청자 JOINED 조인이 방 집합 변경·중복 집계를 만들지 않음. 근거를 spec REQ-001에 기록, 제안된 테스트 데이터 다양화는 AT-001-2에 반영 | Claude (main designer) |
| r1 | C-r1-08 | 수용 | 프로젝트 gate는 `./gradlew test build`만, PR CI(`ci.yml`)는 `./gradlew build dockerTest`. 티켓 완료 증거에 dockerTest 명시, 로컬 Docker 부재 시 같은 HEAD의 CI 통과를 증거로 사용, 공통 엔진 gate는 변경하지 않음 | Claude (main designer) |

# 구현자 지시서 점검 결정

| 라운드 (최대 2) | 항목 | 수용/기각 | 근거 | 메인 설계자 |
|---|---|---|---|---|
| r1 | B-r1-01 | 수용 | spec·보호 테스트(`BroadcastPreferenceBatchTest`·`BroadcastAlreadySentTest`·`TermsReconsentBroadcastApiIntegrationTest`)는 `broadcastOnPublish(UUID)`·`resend(UUID)` 두 메서드 계약, T-03 produces와 T-04 consumes·produces·구현 범위·실행 순서만 옛 `broadcast(UUID[, validateApplicability])` 표기 잔존. 티켓을 두 메서드 계약으로 통일, flag 인자·호환 메서드 추가 금지 명시. spec·보호 테스트 불변 | Claude (main designer) |
| r1 | B-r1-02 | 수용 | `BroadcastConcurrencyDockerTest.race` 본문 43줄, CLAUDE.md 30줄 상한 위반(dockerTest는 detekt 대상 밖이라 CI 미검출). `Scenario` 묶음 + `pausedFirstRequest`·`resolveSource`·`assertResponses`·`assertOneCompleteSet` 헬퍼로 분리, 4개 시나리오·lock-wait 관측·finally 해제·단언 불변, origin/main에서 `compileDockerTestKotlin` 통과 | Claude (main designer) |
