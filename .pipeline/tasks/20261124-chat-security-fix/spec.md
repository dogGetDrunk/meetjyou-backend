# Spec

## 요구사항

### REQ-000 운영 확인 (읽기 전용, 구현 전, 완료)

- 요구사항: 운영 앱 로그와 운영 MySQL `sql_mode` 를 읽기 전용으로 확인해 #293 실제 유실 여부를 판단
- 수용 기준: `result.md` 에 다음을 기록
  - 확인한 로그 범위(시작과 끝 시각), 채팅 outbox 저장에서 나온 `Data too long` 유무
  - 앱 연결의 실효 `sql_mode` 와 그 근거, 확인하지 못한 부분(예: 시크릿인 DB URL의 세션 변수)
  - 결론이 확정인지 미확정인지, 미확정이면 확정에 필요한 조회
- 결과: `logs/ops-check.md` (2026-11-24 수행, 결론 미확정: 로그 범위 11/22~11/24에 채팅 사용 0건, 서버 기본 strict, DB URL 미확인)
- 수용 테스트 ID: 없음(운영 확인)

### REQ-001 채팅 푸시 dedup 키를 100자 안으로 (#293)

- 요구사항
  - `ChatService.handleChatMessage` 의 채팅 푸시 `dedupKey` 를 `chat:{messageUuid}:{receiverUuid}` 로 변경(5+36+1+36 = 78자, intent의 "79자"는 계산 착오)
- 수용 기준
  - 오프라인 수신자에게 outbox 행이 저장되고 `dedup_key` = `chat:{messageUuid}:{receiverUuid}`, 길이 100자 이하
  - 메시지 1개, 수신자 2명 → 키가 다른 행 2개. 같은 수신자에게 메시지 2개 → 키가 다른 행 2개(중복 억제가 메시지와 수신자 단위로 유지)
- 관측 방법(전 테스트 공통): 메시지 전송은 `TransactionTemplate` 으로 실제 커밋 → `AFTER_COMMIT` + `REQUIRES_NEW` 핸들러가 커밋 직후 같은 스레드에서 동기 실행 → 그 뒤 별도 트랜잭션(저장소 조회)으로 outbox 행을 읽음. 비동기 대기 없음. 이벤트나 핸들러 호출을 대신 검증하지 않음
- 수용 테스트 ID
  - AT-001-1(H2, `ChatPushRecipientIntegrationTest` 확장): 수신자 2명에게 메시지 1개, 이어서 같은 수신자들에게 메시지 1개 더 → 행 4개, 정확한 키 4개, 모두 100자 이하
  - AT-001-2(dockerTest, MySQL 8.0.41): 같은 시나리오를 실제 MySQL에서 실행해 행 저장 확인

### REQ-002 테스트 스키마의 dedup_key 길이를 운영과 일치 (#293 재발 방지)

- 요구사항
  - `NotificationOutbox.dedupKey` 에 `@Column(length = 100)` (Flyway `V1` 의 `VARCHAR(100)` 과 같은 값, 상수로 둠)
- 수용 기준
  - 101자 키로 outbox를 직접 저장하면 H2가 길이 초과로 거절
  - REQ-002만 적용하고 키는 옛 115자 형식인 상태에서 채팅 전송 경로를 돌리면 outbox 행이 0개이고, 핸들러 쪽에서 H2 길이 초과 예외가 발생(로그 또는 예외 원인으로 확인) → 같은 경로가 REQ-001 적용 후 행을 저장. 이 중간 상태 결과를 `result.md` 에 기록
- 수용 테스트 ID: AT-002-1(H2, 101자 키 저장 시 예외, 100자 키 저장 성공)

### REQ-003 SEND는 애플리케이션 목적지(`/pub/`)만 허용 (#294)

- 요구사항
  - `ChatStompInterceptor.preSend` 에 `StompCommand.SEND` 분기 추가: destination이 없거나 `/pub/` 로 시작하지 않으면 `InvalidInputException(value = destination, ...)` → 기존 `ChatStompErrorHandler` 가 ERROR 프레임(`message` = `INVALID_INPUT_VALUE`) 전송, 연결 종료
  - 접두사는 `WebSocketConfig` 의 application destination prefix와 같은 상수를 공유(두 곳이 따로 바뀌지 않게)
- 수용 기준
  - 대상 구독이 준비됐음을 먼저 증명: 피해자 연결이 대상 목적지를 구독한 뒤 서버의 정상 경로(`SimpMessagingTemplate.convertAndSend`, 유저 큐는 `convertAndSendToUser`)로 보낸 control 프레임이 도착
  - 그 뒤 공격자 연결이 같은 목적지로 다른 표식의 프레임을 SEND → 공격자는 ERROR 프레임(`INVALID_INPUT_VALUE`)을 받고 transport가 닫힘, 피해자는 정해진 시간(1초) 동안 공격 표식 프레임을 받지 않음
  - `/pub/chat/message` SEND는 기존대로 브로드캐스트됨(기존 `ChatIntegrationTest` 통과)
- 수용 테스트 ID: AT-003-1(통합, 다른 방 메시지 목적지), AT-003-2(통합, 이벤트 목적지), AT-003-3(통합, 다른 유저 오류 큐 `/user/{uuid}/queue/errors`), AT-003-4(단위, destination 없음과 `/pubx` 같은 접두사 유사값 거절, `/pub/...` 통과)

### REQ-004 한 연결에서 CONNECT는 한 번만 (#295)

- 요구사항
  - `handleConnect` 시작 시 세션 속성에 `ROOM_UUID` 가 이미 있으면 JWT, DB 검사 전에 `InvalidInputException` 으로 거절 → ERROR 프레임 + 연결 종료. 세션 속성은 건드리지 않음
- 수정 전 전제 확인(RED): raw WebSocket으로 방 A CONNECT → 같은 소켓으로 방 B(멤버인 방) CONNECT → 같은 소켓으로 `/sub/chat/room/{B}` SUBSCRIBE
  - SUBSCRIBE가 허용되면(= `validateConnectedRoom` 이 B를 연결된 방으로 봄) 두 번째 CONNECT가 `handleConnect` 까지 와서 바인딩을 B로 바꾼 것이 증명됨 → #295 재현
  - 두 번째 CONNECT에 Spring이 ERROR를 주거나 연결을 닫으면 앱 코드 전에 막히는 것 → #295 재현 불가로 `result.md` 에 기록, 가드는 방어용으로 유지(비용 한 줄), 이 결과를 사람에게 보고
  - 결과가 어느 쪽인지 불분명하면 구현을 멈추고 사람에게 보고
- 수용 기준(수정 후)
  - 같은 소켓의 두 번째 CONNECT → ERROR 프레임(`INVALID_INPUT_VALUE`)과 transport 종료. 거절로 연결 자체가 닫히므로 이후 A, B 어느 방의 프레임도 받을 수 없음
  - 거절 시 세션 속성의 방은 A 그대로(서버 쪽 바인딩 보존은 단위 테스트에서 세션 속성 맵을 직접 확인)
  - 첫 CONNECT만 한 연결은 기존대로 A 강퇴 시 close code 1008로 닫힘(기존 `ChatAccessRevocationIntegrationTest`). 두 번째 CONNECT를 시도한 연결은 이미 닫혀서 강퇴 종료 검사 대상이 아님 → "강퇴 후에도 계속 수신"이 불가능함을 transport 종료로 보장
- 수용 테스트 ID: AT-004-1(통합, raw WebSocket, 수정 전 RED에서는 위 전제 확인 관측, 수정 후 ERROR와 종료), AT-004-2(단위, 세션 속성에 A가 있으면 저장소와 JWT 호출 없이 거절, 거절 후 세션 속성의 방이 A)

### REQ-005 STOMP 명령(CONNECT 별칭)도 CONNECT와 같은 검사 (코드 판독 중 발견)

- 배경: STOMP 1.2의 `STOMP` 프레임은 `CONNECT` 와 같은 연결 요청이지만 `StompCommand.STOMP` 로 들어와 현재 `when` 의 `else -> message` 로 통과. 토큰 없이 연결이 열리고, REQ-003 전이라면 이 연결로 `/sub/...` SEND도 가능
- 요구사항
  - `StompCommand.STOMP` 도 `handleConnect` 로 보냄(REQ-004의 중복 검사 포함)
- 수용 기준
  - 토큰 없는 `STOMP` 프레임 → ERROR 프레임(`UNAUTHENTICATED`)과 transport 종료
  - 유효한 토큰, 멤버인 방의 `STOMP` 프레임 → `CONNECTED` 수신, 연결 유지
  - `CONNECT` 후 같은 소켓에서 `STOMP`, `STOMP` 후 `CONNECT` → 두 번째가 ERROR(`INVALID_INPUT_VALUE`)와 transport 종료
- 수용 테스트 ID: AT-005-1(통합 raw, 토큰 없는 STOMP 거절), AT-005-2(통합 raw, 정상 STOMP 허용), AT-005-3(통합 raw, 명령 형식을 섞은 중복 거절 2가지)

### REQ-006 모듈 규칙 문서 갱신

- 요구사항: `.claude/rules/chat.md` 의 Access lifetime 항목에 "SEND는 `/pub/` 만, CONNECT(및 STOMP)는 연결당 한 번" 추가
- 수용 기준: 문서에 두 규칙과 이유(브로커 목적지 직접 SEND는 서비스 검사를 건너뜀, 재CONNECT는 방 바인딩을 바꿔 강퇴 시 세션 종료를 피함)가 있음
- 수용 테스트 ID: 없음(문서)

## 확정 인터페이스

### API 시그니처, DTO

- 외부 API, STOMP 목적지, 메시지 형식 변경 없음
- 새 `ErrorCode` 없음: 거절은 모두 기존 `INVALID_INPUT_VALUE`(400), `UNAUTHENTICATED` 재사용. 정상 앱은 이 경로에 닿지 않으므로 앱 분기 추가 불필요
- `ChatStompInterceptor.preSend` 분기: `CONNECT`, `STOMP` → `handleConnect` / `SUBSCRIBE` → `handleSubscribe` / `SEND` → `handleSend` / 그 외 통과

### DB 스키마

- 변경 없음(운영 컬럼은 이미 `VARCHAR(100)`, 엔티티 매핑만 맞춤). 마이그레이션 없음

## 변경 파일

- `src/main/java/com/dogGetDrunk/meetjyou/chat/ChatService.kt` (REQ-001)
- `src/main/java/com/dogGetDrunk/meetjyou/notification/outbox/NotificationOutbox.kt` (REQ-002)
- `src/main/java/com/dogGetDrunk/meetjyou/chat/connection/ChatStompInterceptor.kt` (REQ-003, 004, 005)
- `src/main/java/com/dogGetDrunk/meetjyou/config/WebSocketConfig.kt` (REQ-003 접두사 상수 공유)
- `src/test/java/com/dogGetDrunk/meetjyou/chat/ChatPushRecipientIntegrationTest.kt` (AT-001-1)
- `src/test/java/com/dogGetDrunk/meetjyou/notification/outbox/` 새 H2 테스트 (AT-002-1)
- `src/test/java/com/dogGetDrunk/meetjyou/chat/ChatStompFrameGuardIntegrationTest.kt` 새 파일 (AT-003-1~3, AT-004-1, AT-005-1~3)
- `src/test/java/com/dogGetDrunk/meetjyou/chat/connection/` 단위 테스트 (AT-003-4, AT-004-2)
- `src/dockerTest/java/com/dogGetDrunk/meetjyou/chat/ChatPushOutboxDockerTest.kt` 새 파일 (AT-001-2, `SharedMySql.newDatabase()` + `@SpringBootTest`, 시드는 저장소로 직접: dockerTest 클래스패스에 `ChatTestDataHelper` 없음)
- `.claude/rules/chat.md` (REQ-006)

## 마일스톤

0. REQ-000 운영 확인(완료, `logs/ops-check.md`)
1. RED: 수정 전 코드로 AT-001-1(키 형식), AT-003-1~3, AT-004-1(전제 확인 관측), AT-005-1, AT-005-3 실행해 실패와 관측 결과 기록
2. REQ-002만 적용 → AT-002-1 통과, 옛 키로 AT-001-1의 행 0개와 H2 길이 초과 확인(중간 상태 기록)
3. REQ-001 적용 → AT-001-1 통과
4. REQ-003, REQ-004, REQ-005 수정 → 통합과 단위 테스트 통과
5. AT-001-2 dockerTest 추가와 통과
6. REQ-006 문서, `./gradlew test build`, `./gradlew dockerTest`
