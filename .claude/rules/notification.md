---
description: Notification module rules — loaded only when working in notification/ package
globs: ["**/notification/**"]
---

# Notification Module

Transactional outbox pattern:
1. Event written to `notification_outbox` table **within the originating transaction**
2. Async dispatcher polls/processes outbox and sends push via Firebase

- **Never dispatch push notifications synchronously** inside a business transaction
- Outbox row includes: `event_type`, `payload` (JSON), `status` (PENDING → SENT/FAILED)
- Firebase config in `src/main/resources/firebase/` (ignored by Claude)
