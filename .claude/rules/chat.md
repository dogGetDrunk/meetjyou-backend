---
description: Chat module rules — loaded only when working in chat/ package
paths:
  - "**/chat/**"
---

# Chat Module

STOMP over WebSocket (`/ws-chat`, pub `/pub/**`, sub `/sub/**`). Key classes:
- `ChatRoom` / `ChatMessage` / `ChatParticipant` — core domain (`room/`, `message/`, `participant/`)
- `ChatService` — message handling; `ChatReadService` — read/unread state
- `connection/` — `ChatStompInterceptor`, `ChatSessionTracker` (presence), `WebSocketEventListener`, `ChatWebSocketSessionRegistry` (open sessions, for server-side close)
- `event/ChatRoomEventBroadcaster` — room events to subscribers

**Notifications:** publish `NotificationEvent` (see `.claude/rules/notification.md`). Do NOT call push senders directly.

**WebSocket sends:** never call `SimpMessagingTemplate` from business code. Publish `ChatMessageBroadcastEvent` / go through `ChatRoomEventBroadcaster`; `ChatBroadcastListener` sends after commit (`fallbackExecution = true` covers callers without a transaction). Sending mid-transaction exposes uncommitted or rolled-back rows to clients (#151).

**Access lifetime (#221):** a connection is authorized once on CONNECT, so access loss must close it — nothing filters outbound frames
- One connection = one room (`roomUuid` CONNECT header, stored as a session attribute). SUBSCRIBE is an allowlist: `/sub/chat/room/{connected room}` (+ `/events`) and `/user/queue/errors` only. The simple broker treats a subscription as an Ant pattern, so a looser rule lets a wildcard receive every room
- Kick / leave → `ChatParticipantService.removeParticipant` publishes `ChatAccessRevokedEvent`; withdrawal → `UserSessionRevoker.revokeAll` publishes `UserSessionsRevokedEvent`. `ChatSessionRevocationListener` closes the matching sessions after commit (1008, reason = errorCode); the reconnect is rejected on CONNECT
- A new way to lose room access must publish one of these events
- SEND rechecks membership and `User.isActive()` per message — covers the gap between commit and close
- Accepted race: a CONNECT validated just before the removal commits can set its attributes after the close scan and stay open until it reconnects (#221 PR)

**Errors to the client:** throw domain exceptions (`BusinessException`), never `IllegalArgumentException` — the app branches on `errorCode`
- CONNECT / SUBSCRIBE rejected in `ChatStompInterceptor` → `ChatStompErrorHandler` sends an ERROR frame (`message` header = errorCode, body = `ErrorResponse` JSON) and the connection closes. JWT exceptions pass through unchanged: `EXPIRED_TOKEN` means refresh and reconnect
- A failed SEND (`@MessageMapping`) keeps the connection: `ChatController`'s `@MessageExceptionHandler` replies to the sender only on `/user/queue/errors` (needs `/queue` on the simple broker)

**Testing:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebSocketStompClient` — see `ChatIntegrationTest`.
