---
description: Post / plan / idempotency rules — loaded only when working in post/, plan/ or common/idempotency/
paths:
  - "**/post/**"
  - "**/plan/**"
  - "**/common/idempotency/**"
---

# Post & Plan Modules

## Idempotent create (`Idempotency-Key` header)
Opting a new create endpoint in:
1. Add a value to `IdempotencyScope`; accept an optional `Idempotency-Key` header
2. Service: `resolveExisting` before creating, `record` after (same transaction)
3. Controller: catch `DataIntegrityViolationException` → `resolveAfterConflict` (see `PostController`)

- `resolveAfterConflict` re-checks the request hash — never replace it with a plain lookup by key (would return the winner's resource to a different body)
- `IdempotencyKeyService.record` stays non-`@Transactional` and never catches the unique violation (session becomes rollback-only)
- The unique constraint lives in both the JPA annotation and Flyway — tests build the H2 schema from annotations
- Hash = SHA-256 of the serialized request DTO → changing DTO fields changes the hash. Same key + different body → 409
- Known limit: key rows are never expired or cleaned up

## Post ↔ party ↔ plan
- Creating a post also creates its Party and ChatRoom; deleting a post does not delete the party
- A post may only attach a plan owned by its author (`resolvePlanReference`) — otherwise `isPlanPublic=true` exposes someone else's plan. Plan changes sync to `party.plan`
- Posts of a COMPLETED party are read-only. `updatePost` and `updatePostStatus` take the party row lock (`PartyService.lockPartyOfPost`) before loading the post, then the post row (`PostRepository.lockByUuid`) — `completeParty` writes post `status` under that lock, and `updatePost` writes party `capacity`/itinerary/`destination` (see party rules)
- `updatePost` syncs `capacity`/`itinStart`/`itinFinish`/`location` to the party (`Party.syncFromPost`) and rejects `capacity < party.joined` with 409 `PARTY_CAPACITY_BELOW_JOINED` before any plan/field/compPreference change. `updatePostStatus` syncs party `recruitmentStatus` (RECRUITMENT_COMPLETED → CLOSED, RECRUITING → OPEN)
- Plan read access goes through `PlanAccessGuard.validateReadAccess` (owner / public post / JOINED member)
- No ON DELETE CASCADE on plan FKs — deleting a plan detaches `party.plan`, `post.plan`, `post.isPlanPublic` first
- That detach is a bulk `UPDATE ... WHERE plan = ?` (`detachPlan`), party before post — never load-and-null the rows: a snapshot read would overwrite a link switched to another plan meanwhile (#201), and party-first matches `updatePost`'s party-row-first lock order (no deadlock while the edit keeps its plan; a plan switch is an accepted deadlock — see party rules)
