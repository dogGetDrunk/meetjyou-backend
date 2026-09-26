---
description: Notification module rules — loaded only when working in notification/ package
paths:
  - "**/notification/**"
---

# Notification Module

Outbox pattern:
1. Business code publishes `NotificationEvent` (or `NoticeBroadcastEvent` / `TermsReconsentEvent`)
2. `*EventHandler` (`@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`) writes a `notification_outbox` row — preference check + `dedupKey` dedup
3. `NotificationDispatcher` (`@Scheduled`) claims rows and sends via FCM

- **Never dispatch push notifications synchronously** inside a business transaction
- Publisher must run inside a transaction — AFTER_COMMIT listeners silently drop events otherwise (ADR-0002)
- Outbox row: `type`, `data_json`, `dedup_key`, `status` (`PENDING → SENDING → SENT`; transient failure → back to `PENDING` with backoff on `available_at`; permanent failure or retries exhausted → `DEAD`; `FAILED` is unused), `attempts`
- **Known gaps (#150):** outbox written outside the originating tx (loss window), no reaper for stuck `SENDING`, multi-token dispatch not idempotent
- Firebase config in `src/main/resources/firebase/` (ignored by Claude)
