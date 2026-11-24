# 수정 전 관측 (RED, 2026-11-24, 기준 커밋 025f308)

## 실패한 수용 테스트

- `ChatStompFrameGuardIntegrationTest` 9건 중 7건 실패, 2건(AT-005-2 정상 STOMP 허용)과 탐침 통과
  - AT-003-1, AT-003-2: 공격자에게 ERROR 프레임 없음(`Expected value to not be null`) → 브로커 목적지 직접 SEND가 거절되지 않음
  - AT-003-3: 정상 경로(`convertAndSendToUser`) control 프레임조차 도착하지 않아 준비 단계에서 실패(`subscription never became routable`) → 아래 "유저 큐 경로" 참고
  - AT-004-1, AT-005-3 2건: 두 번째 CONNECT/STOMP에 ERROR 없음
  - AT-005-1: 토큰 없는 STOMP 프레임에 ERROR 없음 → 인증 없이 연결이 열림

## #295 전제 확인 (임시 탐침, 커밋 전 삭제)

- 방 A CONNECT → `CONNECTED`
- 같은 소켓으로 방 B CONNECT → 응답 프레임 없음(두 번째 `CONNECTED` 없음), 연결 유지
- 같은 소켓으로 `/sub/chat/room/{B}` SUBSCRIBE → 허용되고 서버 정상 경로로 보낸 B 메시지 수신
- 결론: Spring은 같은 세션의 두 번째 CONNECT를 막지 않고 `handleConnect` 까지 전달하며, 세션의 방 바인딩이 B로 바뀜 → #295 재현됨

## 유저 큐 경로 (#294 세 번째 시나리오)

- 연결 후 `SimpUserRegistry` 사용자 수 0, 해당 유저 없음
  - 원인 추정: `ChatStompInterceptor` 가 `StompHeaderAccessor.wrap()` 사본에 `user` 를 설정해서 Spring이 세션 사용자를 레지스트리에 등록하는 콜백이 불리지 않음
  - 기존 기능 영향 없음: 코드의 유저 큐 전송은 전부 `@SendToUser(broadcast = false)` 로 세션 id를 직접 쓰고, 이름 기반 전송(`convertAndSendToUser`)은 없음
- 서버 정상 경로 `convertAndSendToUser(uuid, "/queue/errors")` → 도착 안 함
- 클라이언트 SEND `/user/{uuid}/queue/errors` → 도착 안 함
- 결론: 현재 설정에서 다른 유저 오류 큐 주입은 재현되지 않음(이름으로 세션을 못 찾아 버려짐). 다만 레지스트리 등록이 나중에 고쳐지면 바로 열리는 경로라 SEND 가드로 함께 막음

## #293 중간 상태와 MySQL 재현

- REQ-002만 적용(옛 115자 키 유지) → H2 채팅 경로에서 outbox 행 0개(`expected:<4> but was:<0>`), `Value too long for column "DEDUP_KEY CHARACTER VARYING(100)"` 5건. 기존 #220 테스트(`ChatPushRecipientIntegrationTest`)도 같은 이유로 실패 → 길이 가드가 채팅 경로에서 동작함
- REQ-001 적용 후 같은 테스트 통과
- dockerTest `ChatPushOutboxDockerTest`(MySQL 8.0.41, 서버 기본 `sql_mode`)에서 옛 키로 임시 실행 → `MysqlDataTruncation: Data too long for column 'dedup_key'`, 행 0개. 새 키로 행 4개 통과
- 의미: 운영과 같은 이미지, 같은 기본 설정이면 7/19 이후 오프라인 수신자 채팅 푸시는 저장되지 않았음. 운영 DB URL에 세션 `sql_mode` 덮어쓰기가 없다면 유실 확정
