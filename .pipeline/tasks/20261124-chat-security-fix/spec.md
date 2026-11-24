# Spec

## 요구사항

### REQ-001 채팅 푸시 dedup 키를 100자 안으로 (#293)

- 요구사항
  - `ChatService.handleChatMessage` 의 채팅 푸시 `dedupKey` 를 `chat:{messageUuid}:{receiverUuid}` 로 변경(5+36+1+36 = 78자, intent의 "79자"는 계산 착오)
  - 키 형식을 `ChatService` companion의 private 함수 하나로 둠(테스트에서 길이 검증용으로 호출하지 않고, 결과 행으로 검증)
- 수용 기준
  - 오프라인 수신자에게 outbox 행이 저장되고 `dedup_key` = `chat:{messageUuid}:{receiverUuid}`, 길이 100자 이하
  - 같은 메시지의 수신자별 키는 서로 다름, 다른 메시지는 같은 수신자라도 키가 다름(중복 억제가 메시지 단위로 유지)
- 수용 테스트 ID: AT-001-1(H2, 기존 `ChatPushRecipientIntegrationTest` 확장: 행의 키 형식과 길이), AT-001-2(dockerTest, MySQL 8.0.41에서 채팅 메시지 전송 → outbox 행 저장)

### REQ-002 테스트 스키마의 dedup_key 길이를 운영과 일치 (#293 재발 방지)

- 요구사항
  - `NotificationOutbox.dedupKey` 에 `@Column(length = 100)` (Flyway `V1` 의 `VARCHAR(100)` 과 같은 값, 상수로 둠)
- 수용 기준
  - H2 `create-drop` 스키마에서 `notification_outbox.dedup_key` 길이가 100
  - 100자를 넘는 키를 저장하면 H2 테스트가 실패함(REQ-001 수정 전 코드로 AT-001-1이 실패하는 것으로 확인, 구현 순서상 RED 단계 기록)
- 수용 테스트 ID: AT-002-1(H2에서 101자 키 outbox 저장 시 예외)

### REQ-003 SEND는 애플리케이션 목적지(`/pub/`)만 허용 (#294)

- 요구사항
  - `ChatStompInterceptor.preSend` 에 `StompCommand.SEND` 분기 추가: destination이 없거나 `/pub/` 로 시작하지 않으면 `InvalidInputException(value = destination, ...)` → 기존 `ChatStompErrorHandler` 가 ERROR 프레임(`message` = `INVALID_INPUT_VALUE`) 전송 후 연결 종료
  - 접두사는 `WebSocketConfig` 의 application destination prefix와 같은 상수를 공유(두 곳이 따로 바뀌지 않게)
- 수용 기준
  - `/sub/chat/room/{다른 방}`, `/sub/chat/room/{방}/events`, `/user/{uuid}/queue/errors` 로의 SEND → 보낸 쪽은 ERROR 프레임(`INVALID_INPUT_VALUE`), 해당 목적지 구독자는 아무 프레임도 받지 않음
  - `/pub/chat/message` SEND는 기존대로 브로드캐스트됨(기존 `ChatIntegrationTest` 통과)
- 수용 테스트 ID: AT-003-1(통합, 다른 방 메시지 목적지), AT-003-2(통합, 이벤트 목적지), AT-003-3(통합, 다른 유저 오류 큐), AT-003-4(단위, destination 없음과 `/pubx` 같은 접두사 유사값 거절, `/pub/...` 통과)

### REQ-004 한 연결에서 CONNECT는 한 번만 (#295)

- 요구사항
  - `handleConnect` 시작 시 세션 속성에 `ROOM_UUID` 가 이미 있으면 JWT, DB 검사 전에 `InvalidInputException` 으로 거절 → ERROR 프레임 + 연결 종료
- 수용 기준
  - 같은 WebSocket에서 방 A CONNECT 성공 후 방 B(멤버인 방) CONNECT → ERROR 프레임(`INVALID_INPUT_VALUE`), 연결 종료, 세션 속성의 방은 A 그대로
  - 위 상황 대신 첫 CONNECT만 한 연결은 기존대로 A 강퇴 시 close code 1008로 닫힘(기존 테스트)
  - 테스트는 수정 전 코드에서 먼저 실행해 "Spring이 같은 세션의 두 번째 CONNECT를 막지 않는다"는 전제를 확인하고 결과를 `result.md` 에 기록
- 수용 테스트 ID: AT-004-1(통합, raw WebSocket으로 CONNECT 두 번), AT-004-2(단위, 세션 속성에 방이 있으면 저장소 호출 없이 거절)

### REQ-005 STOMP 명령(CONNECT 별칭)도 CONNECT와 같은 검사 (코드 판독 중 발견)

- 배경: STOMP 1.2의 `STOMP` 프레임은 `CONNECT` 와 같은 연결 요청이지만 `StompCommand.STOMP` 로 들어와 현재 `when` 의 `else -> message` 로 통과. 토큰 없이 연결이 열리고, REQ-003 전이라면 이 연결로 `/sub/...` SEND도 가능
- 요구사항
  - `StompCommand.STOMP` 도 `handleConnect` 로 보냄(REQ-004의 중복 검사 포함)
- 수용 기준
  - 토큰 없는 `STOMP` 프레임 → ERROR 프레임(`UNAUTHENTICATED`)과 연결 종료
  - 유효한 토큰, 멤버인 방의 `STOMP` 프레임 → 연결 허용(CONNECT와 동일)
- 수용 테스트 ID: AT-005-1(단위, 토큰 없음 거절), AT-005-2(단위, 정상 허용)

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
- `src/test/java/com/dogGetDrunk/meetjyou/notification/...` 새 H2 테스트 (AT-002-1)
- `src/test/java/com/dogGetDrunk/meetjyou/chat/ChatAccessRevocationIntegrationTest.kt` 또는 새 통합 테스트 (AT-003-1~3, AT-004-1)
- `src/test/java/com/dogGetDrunk/meetjyou/chat/connection/` 단위 테스트 (AT-003-4, AT-004-2, AT-005-1, AT-005-2)
- `src/dockerTest/java/com/dogGetDrunk/meetjyou/chat/ChatPushOutboxDockerTest.kt` 새 파일 (AT-001-2, `SharedMySql.newDatabase()` + `@SpringBootTest`, 시드는 저장소로 직접: dockerTest 클래스패스에 `ChatTestDataHelper` 없음)
- `.claude/rules/chat.md` (REQ-006)

## 마일스톤

1. RED: AT-002-1, AT-001-1(키 검증 추가), AT-003-1~3, AT-004-1, AT-005-1을 수정 전 코드로 실행해 실패 확인(AT-004-1은 전제 확인 겸)
2. REQ-002 → REQ-001 수정, AT-001-1, AT-002-1 통과
3. REQ-003, REQ-004, REQ-005 수정, 통합과 단위 테스트 통과
4. AT-001-2 dockerTest 추가와 통과
5. REQ-006 문서, `./gradlew test build`, `./gradlew dockerTest`
