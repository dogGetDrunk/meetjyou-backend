---
description: Chat module rules — loaded only when working in chat/ package
globs: ["**/chat/**"]
---

# Chat Module

WebSocket-based chat. Key classes:
- `ChatRoom` / `ChatMessage` / `ChatParticipant` — core domain
- `ChatConnectionService` — manages WS sessions
- `ChatEventService` — publishes events to notification outbox

**Pattern:** All chat events go through `notification_outbox` (same-transaction write). Do NOT call notification services directly.

**Testing:** Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + SockJS client for integration tests.
