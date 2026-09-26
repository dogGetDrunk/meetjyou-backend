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

**Open issue:** WS broadcast is sent before commit → ghost message on rollback (#151).

**Testing:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebSocketStompClient` — see `ChatIntegrationTest`.
