---
description: Party / membership rules — loaded only when working in party/ or userparty/ packages
paths:
  - "**/party/**"
  - "**/userparty/**"
---

# Party Module

- `Party` and `Post` are `@DynamicUpdate`: a flush only writes changed columns, so an unlocked write path (image state, plan link, post edit) can't revert a concurrent `joined`/`name`/post `status` change it never touched
- **Same-column writes and stale-read checks still need the row lock** — load with `findByUuidForUpdate` *before* the party is loaded anywhere in the transaction (a later locked re-query keeps the stale snapshot). Locked today: `joined` (approve/ban/leave), `name` (rename), `completeParty` ↔ `PostService.updatePostStatus` (both write post `status`; the latter locks via `PartyService.lockPartyOfPost`)
- `party.joined` is a hand-maintained counter (approve `++`, ban/leave `--`, host counted from creation via `joined = 1`). A new membership path must adjust it under the same row lock
- Parties are created only through `PostService.createPost` (party create endpoint is disabled)
- Change member status only via `UserParty.pending()/approve()/reject()` — they also reset `statusChangedAt` and the `hostRead`/`applicantRead` unread flags
- Join re-apply: PENDING → returned as-is (do not touch `statusChangedAt`, the notification dedupKey depends on it), REJECTED → allowed after 24h, JOINED/BANNED/LEFT → rejected. Cancelling a PENDING request deletes the row
- State changes are retry-idempotent: an already-applied target state returns success, and lookups that decide 404 vs 403 run before the host check
- No FK to `party` has ON DELETE CASCADE — delete order: chat data (`purgeRoomData`) → chat room → post → user_party → party
- COMPLETED parties are read-only (`validatePartyWritable`); chat membership is synced manually (`enterRoom` / `removeParticipant` + room event broadcast)
- Reuse host checks: `verifyPartyHost`, `assertCurrentUserIsHost`, `requireActiveHostMembership` ("active" = JOINED)
- Methods that call OCI Object Storage stay non-`@Transactional` (no DB connection held during network I/O)

**Testing:** one Kotest `BehaviorSpec` per use case, `PartyService` built by hand from relaxed mockk; locking and `@DynamicUpdate` are covered by `UpdatePartyNameIntegrationTest` and `PartyPostLostUpdateIntegrationTest`.
