# Intent

## 해결할 문제

11/23 전체 코드 리뷰에서 나온 채팅 결함 3건(노션 작업 245)

- **#293 채팅 푸시 유실 가능**: 채팅 푸시 `dedupKey` = `chat:{roomUuid}:{receiverUuid}:{messageUuid}` = 115자, `notification_outbox.dedup_key` 는 `VARCHAR(100)`. MySQL strict 모드면 outbox `save` 가 "Data too long" 으로 실패해서 오프라인 수신자 푸시가 유실됨(메시지 저장은 성공). 2026-07-19(`345af5a`)부터 해당 가능
  - 테스트가 못 잡은 이유: H2 `create-drop` 은 엔티티 기준 255자, dockerTest(MySQL)에는 채팅 푸시 outbox 저장 경로 없음
- **#294 브로커 목적지 직접 SEND**: `ChatStompInterceptor.preSend` 가 CONNECT, SUBSCRIBE만 검사. 클라이언트가 SEND destination을 `/sub/...`, `/user/...` 로 주면 simple broker가 `@MessageMapping` 과 멤버십 검사 없이 구독자에게 그대로 전달 → 다른 방에 가짜 메시지, 가짜 `MEMBER_BANNED` 이벤트, 다른 유저에게 가짜 오류 프레임
- **#295 CONNECT 재전송으로 방 바인딩 덮어쓰기**: `handleConnect` 가 세션에 이미 `ROOM_UUID` 가 있어도 덮어씀. A 방 연결 후 같은 소켓으로 B CONNECT를 보내면 A에서 강퇴돼도 세션이 안 닫히고 A 구독이 살아 메시지를 계속 받음(#221 접근 수명 규칙 우회). 이슈 기준 확신도 65%, 프레임워크가 중복 CONNECT를 막지 않는다는 전제는 미확인

## 의도

- 채팅 접근 규칙(#221 "연결 하나 = 방 하나, CONNECT에서 한 번 인가")이 SEND와 재CONNECT 경로에서도 지켜지게 막고, 채팅 푸시가 저장 단계에서 실패하지 않게 함
- 같은 종류의 길이 초과가 다시 테스트를 통과하지 못하도록 테스트 환경 컬럼 길이를 운영과 맞춤

## 포함 범위

- #293
  - 채팅 푸시 키를 `chat:{messageUuid}:{receiverUuid}` (79자)로 축소. 메시지당 수신자별 유일성은 유지, 마이그레이션 불필요
  - `NotificationOutbox.dedupKey` 에 `@Column(length = 100)` 추가 → H2 스키마도 100자가 되어 기존 H2 테스트가 길이 초과를 잡음
  - dockerTest(MySQL)에 채팅 메시지 전송 → 오프라인 수신자 outbox 행 저장 경로 추가
- #294
  - `preSend` 에서 `StompCommand.SEND` 의 destination이 `/pub/` 로 시작하지 않으면 거절(기존 거절 방식대로 ERROR 프레임 + 연결 종료)
  - 회귀 테스트: `/sub/chat/room/{다른 방}`, `/sub/chat/room/{uuid}/events`, `/user/{uuid}/queue/errors` 로의 직접 SEND가 구독자에게 전달되지 않음
- #295
  - 세션에 `ROOM_UUID` 가 이미 있으면 CONNECT 거절
  - 회귀 테스트: 같은 WebSocket 세션에서 CONNECT 두 번 → 두 번째 거절, 첫 방 바인딩 유지. 이 테스트로 "프레임워크가 중복 CONNECT를 막지 않는다" 전제도 함께 확인
- `.claude/rules/chat.md` 접근 수명 항목에 SEND destination 제한과 재CONNECT 거절 규칙 추가
- 운영 확인(읽기 전용, 구현 전): 운영 앱 로그의 "Data too long" 발생 여부와 운영 MySQL strict 모드 여부로 #293 실제 유실 여부 판단, 결과는 `result.md` 에 기록

## 제외 범위

- 이미 유실된 푸시 재발송: 채팅 푸시는 지난 메시지를 다시 보낼 가치가 낮음
- Spring Security messaging(`@EnableWebSocketSecurity`) 도입: 목적지 권한을 선언형으로 관리하는 더 일반적인 방법이지만 의존성과 STOMP CSRF 처리까지 새로 들어옴. 지금 필요한 건 SEND prefix 한 줄 검사라 기각
- 다른 알림 키 길이 정리: `join_request:` 키가 현재 정확히 100자(epoch millis 13자리 기준)라 한계에 걸려 있지만 초과는 아님. 위 `@Column(length = 100)` 으로 앞으로의 초과는 테스트에서 잡힘
- 앱(프론트) 수정: 정상 앱은 `/pub/` 로만 SEND하고 CONNECT를 한 번만 보내므로 영향 없음

## 성공 기준

- 채팅 메시지 전송 시 오프라인 수신자 outbox 행이 MySQL(dockerTest)에서 저장되고 `dedup_key` 가 100자 이하
- 키를 115자로 되돌리면 H2 테스트가 실패함(길이 가드가 실제로 동작)
- `/pub/` 외 목적지로의 SEND는 거절되고 다른 방, 다른 유저 구독자에게 아무 프레임도 도착하지 않음
- 정상 `/pub/chat/...` SEND는 기존대로 동작(기존 `ChatIntegrationTest` 통과)
- 같은 세션의 두 번째 CONNECT는 거절되고, 첫 방 강퇴 시 그 세션이 닫힘
- `./gradlew test`, `./gradlew build`(detekt, Konsist 포함), `./gradlew dockerTest` 통과

## 작업 크기

M — 서로 독립적인 결함 3건이지만 모두 채팅 접근과 푸시 한 영역이고, DB 구조 변경 없이 서버 수정과 테스트만으로 끝남

## 요청 원문과 다른 점

- 요청 원문은 노션 작업 번호 `245` 뿐. 범위는 노션 작업 245와 연결 이슈 #293~#295 본문을 그대로 따름
- 없음(노션 TODO 1번 운영 로그 확인도 이 태스크에서 읽기 전용으로 수행)

## 결정 사항

- 운영 로그와 `sql_mode` 확인은 이 태스크 안에서 읽기 전용으로 수행(사람 결정, 2026-11-24 "운영 로그는 여기서 바로 확인")

## 출처

- 노션 태스크: [채팅 보안과 채팅 푸시 결함 수정 (11/23 리뷰)](https://app.notion.com/p/42156d9a97d981d8a6f9cfd4a85a9b37) (작업 ID 245)
- GitHub 이슈: dogGetDrunk/meetjyou-backend#293, #294, #295
