---
description: Chat module rules — loaded only when working in chat/ package
paths:
  - "**/chat/**"
---

# Chat Module

STOMP over WebSocket (`/ws-chat`, pub `/pub/**`, sub `/sub/**`). Key classes:
- `ChatRoom` / `ChatMessage` / `ChatParticipant` — core domain (`room/`, `message/`, `participant/`)
- `ChatService` — message handling; `ChatReadService` — read/unread state
- `connection/` — `ChatStompInterceptor`, `ChatSessionTracker` (presence), `WebSocketEventListener`
- `event/ChatRoomEventBroadcaster` — room events to subscribers

**Notifications:** publish `NotificationEvent` (see `.claude/rules/notification.md`). Do NOT call push senders directly.

**WebSocket sends:** never call `SimpMessagingTemplate` from business code. Publish `ChatMessageBroadcastEvent` / go through `ChatRoomEventBroadcaster`; `ChatBroadcastListener` sends after commit (`fallbackExecution = true` covers callers without a transaction). Sending mid-transaction exposes uncommitted or rolled-back rows to clients (#151).

**Errors to the client:** throw domain exceptions (`BusinessException`), never `IllegalArgumentException` — the app branches on `errorCode`
- CONNECT / SUBSCRIBE rejected in `ChatStompInterceptor` → `ChatStompErrorHandler` sends an ERROR frame (`message` header = errorCode, body = `ErrorResponse` JSON) and the connection closes. JWT exceptions pass through unchanged: `EXPIRED_TOKEN` means refresh and reconnect
- A failed SEND (`@MessageMapping`) keeps the connection: `ChatController`'s `@MessageExceptionHandler` replies to the sender only on `/user/queue/errors` (needs `/queue` on the simple broker)

**Testing:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebSocketStompClient` — see `ChatIntegrationTest`.
