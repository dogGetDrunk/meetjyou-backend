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
- Outbox row: `type`, `data_json`, `dedup_key`, `status` (`PENDING → SENDING → SENT`; transient failure → back to `PENDING` with backoff on `available_at`; retries exhausted or no deliverable token → `DEAD`; `FAILED` is unused), `attempts`, `delivered_token_ids`
- **`available_at` has two meanings:** retry time for `PENDING`, lease expiry for `SENDING`. The claim query picks both once it has passed, so a row stranded by a crash mid-send is reclaimed after the lease (10 min) — there is no separate reaper
- The claim sets the lease with the DB clock (`NOW()`) and counts the attempt up front; a row reclaimed at the attempt limit goes `DEAD` without sending (poison guard)
- Delivery is tracked per token in `delivered_token_ids` (push_token ids): a retry skips tokens that already got it. A permanent failure on one token is left out of the row verdict; only `UNREGISTERED` deactivates the token (`INVALID_ARGUMENT` may be a payload problem)
- Delivery is at-least-once: a crash mid-send can duplicate that row's pushes
- **Accepted risk:** the outbox row is written after the originating tx commits, so a crash in that window loses the push. In-app views cover most types — rationale and revisit conditions in ADR-0002 (2026-10-11 update)
- Firebase config in `src/main/resources/firebase/` (ignored by Claude)
