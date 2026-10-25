---
description: User module rules — loaded only when working in user/ package
paths:
  - "**/user/**"
---

# User Module

- Social identity lives in `user_social_account` — `(provider, external_id)` unique service-wide, one account per provider per user. Owned by `User` (`socialAccounts`, cascade + orphan removal): add via `user.addSocialAccount(...)`, no separate repository. Registration and login resolve the user through `UserRepository.findBySocialAccount` only
- Link / unlink (`SocialAccountService`, `/users/me/social-accounts`)
  - Link: identity already mine → 200 no-op (retry); owned by another user, a withdrawn one in grace included → 409; same provider already linked → 409. Concurrent links are settled by the two unique constraints — `flushNewLink` maps the violation to 409
  - Unlink: last account refused (409, state conflict). Rows are read with `findSocialAccountsForUpdate` (`FOR UPDATE`, ignores the REPEATABLE READ snapshot), so it stays correct whatever the transaction read earlier — count those rows, never `user.socialAccounts` (may be stale). `SocialAccountConcurrencyDockerTest` guards both races
  - `socialAccounts` is read-only outside `User`; `removeSocialAccount` skips the last-account check, so call it only from `SocialAccountService.unlink`
- `user.email` / `user_social_account.email` are nullable (V39): contact data only, null when the provider shared no verified email. Never use them for identity or lookups
- `user.auth_provider` / `user.external_id` are still written at sign-up (V37 expand step, keeps an image rollback working) but never read for identity — don't add lookups on them. A user saved without a social row (dev auth, load-test account, test fixtures) cannot social-login
- Withdrawal lifecycle `NORMAL → DELETED → PURGED`, grace period = `User.WITHDRAWAL_GRACE_PERIOD` (30 days, single source for restore and purge)
  - Withdraw: `user.withdraw(now)` + `UserSessionRevoker.revokeAll` (refresh tokens revoked, push tokens deleted). Nothing cascades to posts, parties or plans
  - Restore: social login within the grace period only (`UserAuthService.restoreIfWithdrawn`). Refresh never restores
  - Purge: `WithdrawnUserPurgeScheduler` daily 04:00 KST → profile image deleted first (outside the transaction), then `WithdrawnUserPurgeService.purge` bulk-deletes social accounts, preferences, refresh/push tokens, notification settings and idempotency keys, then `anonymize()`s the row. The row is never hard-deleted (FKs from posts, parties, chats). Kept on purpose: `user_terms` (consent evidence) and `post_view_logs` (usage records) — a new per-user table needs a keep/delete call here
  - Visibility: withdrawn users (DELETED or PURGED) have no public profile — `getPublicUserProfile` reads `NORMAL` only → `UserNotFound` 404. The admin list skips PURGED rows only. Preference reads go through `UserPreferenceData.from` (gender, age, ≥1 personality required), so a missing one on a non-purged user is broken data
  - Anonymized values must fit MySQL-only limits (`nickname VARCHAR(10) UNIQUE`, `status VARCHAR(10)`) — H2 can't catch an overflow; `UserAnonymizationDockerTest` guards it
- `chk_user_withdrawn_at` (V38, mirrored by `@Check` on `User`): any non-`NORMAL` row must have `withdrawn_at` — change `status` only through `withdraw` / `restore` / `anonymize`
- Only `NORMAL` is active: `User.isActive()` is the check in `JwtAuthFilter`, `ChatStompInterceptor`, `SocialRegistrationService` (register) and `UserAuthService` (login / refresh) — a new auth entry point must use it, not `status == DELETED`
- **Unique races on sign-up / profile update** (#152): the existence checks are only a fast path; the unique indexes decide
  - `createUser` (`saveAndFlush`) and the `updateUser` nickname change (`flush`) run inside `translateUniqueViolation` → violation mapped by constraint name (`UserUniqueConstraints`, names fixed in V40; MySQL reports `table.index`, the prefix is stripped) to 409. An unmapped constraint stays a 500 on purpose. A new unique index on `user` needs an explicit name and a mapping
  - Registration is not `@Transactional` (`SocialRegistrationService.register`): provider check outside any transaction → create in one → on a `DuplicateException` a fresh one looks the identity up; created within `jwt.rotation-overlap-seconds` → logged in (double-submitted sign-up), otherwise the 409 stands
  - `UserUniqueRaceDockerTest` guards all paths; it detects lock waits via `performance_schema.data_lock_waits` (`innodb_trx` missed the nickname-index wait)
- Nickname availability: `isDuplicateNickname` (API) and `validateNicknameAvailable` (create/update) both check the raw UNIQUE column — keep them on one rule, or the API says "available" and the sign-up then gets a 409. A withdrawn account's nickname frees up only when the purge job anonymizes the row (≤ 1 day after the grace period)
- No distributed lock on the purge job — revisit (e.g. ShedLock) before running more than one app instance
- **Open issue:** V37 contract step pending — drop `user.auth_provider` / `user.external_id` (`external_id` is still NOT NULL UNIQUE on `user`) and move `BasicUserResponse.authProvider` onto the social accounts
