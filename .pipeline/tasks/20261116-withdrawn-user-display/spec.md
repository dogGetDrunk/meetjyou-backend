# Spec

## 요구사항

### REQ-001 표시 규칙 한 곳

- 요구사항: 탈퇴 회원 표시 규칙을 `UserDisplay.of(user)` 하나로 정의
  - `user.status == PURGED` → `nickname = "(탈퇴한 사용자)"`, `hasProfileImage = false`, `isWithdrawn = true`
  - 그 외(`NORMAL`, `DELETED`) → `nickname = user.nickname`, `hasProfileImage = user.hasProfileImage`, `isWithdrawn = false`
  - 문구 상수 `UserDisplay.WITHDRAWN_NICKNAME` 이 유일한 정의, `PostAuthorSummary.WITHDRAWN_NICKNAME` 삭제
- 수용 기준
  - 세 상태 각각 위 값
  - 프로덕션 코드에서 `WITHDRAWN_NICKNAME` 정의와 `UserStatus.PURGED` 기반 표시 분기는 `UserDisplay` 에만 존재
- 수용 테스트 ID: AT-001 (`UserDisplayTest`, 단위)

### REQ-002 게시글 작성자 기준 변경

- 요구사항: `PostAuthorSummary.of` 가 `UserDisplay.of` 를 사용, 기존 `!isActive()`(DELETED 포함) 기준을 PURGED 만으로 변경
- 수용 기준
  - `DELETED` 작성자: 원래 닉네임, 실제 `hasProfileImage`, `isWithdrawn=false`
  - `PURGED` 작성자: `(탈퇴한 사용자)`, `false`, `true`
  - 게시글 목록, 작성자별 목록, 상세 세 응답 모두 같음
- 수용 테스트 ID: AT-002 (`PostAuthorSummaryTest` 단위 수정, `PostAuthorSummaryIntegrationTest` HTTP 수정)

### REQ-003 채팅 메시지 발신자

- 요구사항: `ChatMessageResponse.of` 가 `UserDisplay.of(sender)` 로 `senderNickname` 을 채우고 `isWithdrawn` 필드 추가
- 수용 기준
  - 메시지 이력 조회 `GET /api/v1/chat/rooms/{roomUuid}/messages` 에서 PURGED 발신자 메시지는 `senderNickname="(탈퇴한 사용자)"`, `isWithdrawn=true`, DELETED와 NORMAL 발신자는 원래 닉네임과 `isWithdrawn=false`
  - WebSocket 브로드캐스트(`ChatMessageBroadcastEvent.message`)도 같은 `ChatMessageResponse.of` 로 만들어지며, 발신자(항상 NORMAL, 비활성 회원은 STOMP 연결 거부)의 원래 닉네임과 `isWithdrawn=false` 를 담음
- 수용 테스트 ID: AT-003 (`ChatMessageResponseTest` 단위, `WithdrawnUserDisplayIntegrationTest` HTTP), AT-003b (`ChatServiceTest` 브로드캐스트 이벤트 페이로드)

### REQ-004 파티 멤버 목록

- 요구사항: `PartyMemberResponse.of` 가 `UserDisplay.of` 사용, `isWithdrawn` 추가
- 수용 기준: `GET /api/v1/parties/{partyUuid}/members` 에서 PURGED 멤버는 `(탈퇴한 사용자)`, `hasProfileImage=false`, `isWithdrawn=true`, DELETED와 NORMAL 멤버(호스트 포함)는 원래 값과 `isWithdrawn=false`
- 수용 테스트 ID: AT-004 (`GetPartyMembersTest` 단위 추가, `WithdrawnUserDisplayIntegrationTest` HTTP)

### REQ-005 가입 신청 목록

- 요구사항: `PendingJoinRequest` 에 `isWithdrawn` 추가, `PartyService.getPendingJoinRequests` 가 `UserDisplay.of` 사용
- 수용 기준: `GET /api/v1/parties/{partyUuid}/join-requests` 에서 PURGED 신청자는 가려지고 `isWithdrawn=true`, DELETED와 NORMAL 신청자는 원래 값과 `isWithdrawn=false`
- 수용 테스트 ID: AT-005 (`GetPendingJoinRequestsTest` 단위 추가, `WithdrawnUserDisplayIntegrationTest` HTTP)

### REQ-006 알림센터 받은 신청

- 요구사항: `ReceivedApplicationItem` 에 `isWithdrawn` 추가, `NotificationCenterService.getReceivedApplications` 가 `UserDisplay.of` 사용
- 수용 기준: `GET /api/v1/notification-center/received-applications` 에서 PURGED 신청자는 가려지고 `isWithdrawn=true`, DELETED와 NORMAL 신청자는 원래 값과 `isWithdrawn=false`
- 수용 테스트 ID: AT-006 (`NotificationCenterServiceTest` 단위 추가, `WithdrawnUserDisplayIntegrationTest` HTTP)

### REQ-007 추가 쿼리 없음, 닉네임 등록 차단 유지

- 요구사항: 표시 규칙은 이미 로딩된 `User` 의 `status`, `nickname`, `hasProfileImage` 만 읽음. 회원 닉네임으로 새 문구를 가입, 수정할 수 없음
- 수용 기준
  - 기존 쿼리 수 테스트(`ChatReadServiceQueryOptimizationTest`, `PartyQueryOptimizationTest`) 기대값 변경 없이 통과
  - `UserRequestValidationTest` 의 표시 문구 등록 거부 케이스가 `UserDisplay.WITHDRAWN_NICKNAME` 으로 통과
- 수용 테스트 ID: AT-007 (기존 테스트 유지, 참조만 변경)

## 확정 인터페이스

### API 시그니처·DTO

- 신규 `com.dogGetDrunk.meetjyou.user.dto.UserDisplay`

```kotlin
data class UserDisplay(val nickname: String, val hasProfileImage: Boolean, val isWithdrawn: Boolean) {
    companion object {
        const val WITHDRAWN_NICKNAME = "(탈퇴한 사용자)"
        fun of(user: User): UserDisplay
    }
}
```

- 응답 필드 추가(기존 필드 이름과 타입 유지, 새 필드는 `hasProfileImage` 다음, 채팅은 `senderNickname` 다음)
  - `ChatMessageResponse.isWithdrawn: Boolean` (`senderNickname` 의 가림 여부)
  - `PartyMemberResponse.isWithdrawn: Boolean`
  - `PendingJoinRequest.isWithdrawn: Boolean`
  - `ReceivedApplicationItem.isWithdrawn: Boolean`
  - JSON 이름은 게시글 `author.isWithdrawn` 과 같은 `isWithdrawn`
- `PostAuthorSummary` 필드 변경 없음, 값 기준만 변경
- `ChatSwaggerDocController` 의 예시 응답 생성자에 `isWithdrawn = false` 추가
- 엔드포인트, 요청, 상태 코드 변경 없음

### DB 스키마

- 변경 없음

## 변경 파일

- 신규: `user/dto/UserDisplay.kt`
- 수정: `post/dto/PostAuthorSummary.kt`, `chat/message/ChatMessageResponse.kt`, `chat/ChatSwaggerDocController.kt`, `party/dto/GetPartyMembersResponse.kt`, `party/dto/GetPendingJoinRequestsResponse.kt`, `party/PartyService.kt`(getPendingJoinRequests), `notificationcenter/dto/ReceivedApplicationsSectionResponse.kt`, `notificationcenter/NotificationCenterService.kt`(getReceivedApplications)
- 규칙 문서: `.claude/rules/user.md` 에 "표시 가림은 PURGED 만, 공개 프로필 404는 DELETED 부터(의도된 차이)" 1줄
- 테스트: 신규 `user/UserDisplayTest.kt`, `common/WithdrawnUserDisplayIntegrationTest.kt`(또는 `user/`), 수정 `post/PostAuthorSummaryTest.kt`, `post/PostAuthorSummaryIntegrationTest.kt`, `user/UserRequestValidationTest.kt`, `party/GetPartyMembersTest.kt`, `party/GetPendingJoinRequestsTest.kt`, `notificationcenter/NotificationCenterServiceTest.kt`, `chat/message/ChatMessageResponseTest.kt`(신규)

## 테스트 설계

- AT-001 단위: `UserFixtures.user()` 로 NORMAL, `withdraw()` 로 DELETED, `withdraw()+anonymize()` 로 PURGED, 각각 `hasProfileImage=true` 로 시작해 세 필드 검증
- AT-002~006 HTTP 통합(`@SpringBootTest(RANDOM_PORT)`, H2, `PostAuthorSummaryIntegrationTest` 와 같은 MockBean 구성)
  - 활동 중인 호스트 1명이 파티, 채팅방을 만들고 메시지 1개를 보냄, 멤버 2명(DELETED 예정, PURGED 예정)이 가입하고 메시지 1개씩 보냄, 신청자 3명(NORMAL, DELETED 예정, PURGED 예정)이 대기 신청
  - 세 상태 모두 HTTP로 검증: 멤버 목록과 채팅은 호스트가 NORMAL 대상, 신청 목록과 알림센터는 NORMAL 신청자가 대상
  - 그 뒤 상태 전환(`withdraw`, `withdraw+anonymize`) 후 호스트 토큰으로 4개 엔드포인트 조회, 각 회원의 `nickname`(채팅은 `senderNickname`), `hasProfileImage`, `isWithdrawn` 검증
  - 데이터 준비는 리포지토리 직접 저장(`ChatTestDataHelper` 패턴) 허용, 조회는 반드시 HTTP
- AT-003b 단위: `ChatServiceTest` 에서 `sendMessage` 가 발행한 `ChatMessageBroadcastEvent` 를 잡아 `message.senderNickname`, `message.isWithdrawn=false` 검증
- 단위 테스트 추가 케이스: 각 서비스 테스트에 PURGED 회원 1건, DELETED 회원 1건

## 마일스톤

1. `UserDisplay` + AT-001
2. 게시글 기준 변경 + AT-002, AT-007 참조 변경
3. 4개 응답 적용 + 단위 테스트
4. HTTP 통합 테스트 + 규칙 문서
5. `agent-pipeline gate`(= `./gradlew test build`)
