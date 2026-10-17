---
description: User module rules — loaded only when working in user/ package
paths:
  - "**/user/**"
---

# User Module

- Social identity lives in `user_social_account` — `(provider, external_id)` unique service-wide, one account per provider per user. Owned by `User` (`socialAccounts`, cascade + orphan removal): add via `user.addSocialAccount(...)`, no separate repository. Registration and login resolve the user through `UserRepository.findBySocialAccount` only
- Link / unlink (`SocialAccountService`, `/users/me/social-accounts`)
  - Link: identity already mine → 200 no-op (retry); owned by another user, a withdrawn one in grace included → 409; same provider already linked → 409. Concurrent links are settled by the two unique constraints — `flushNewLink` maps the violation to 409
  - Unlink: last account refused (400). Rows are read with `findSocialAccountsForUpdate` (`FOR UPDATE`, ignores the REPEATABLE READ snapshot), so it stays correct whatever the transaction read earlier — count those rows, never `user.socialAccounts` (may be stale). `SocialAccountConcurrencyDockerTest` guards both races
  - `socialAccounts` is read-only outside `User`; `removeSocialAccount` skips the last-account check, so call it only from `SocialAccountService.unlink`
- `user.email` / `user_social_account.email` are nullable (V39): contact data only, null when the provider shared no verified email. Never use them for identity or lookups
- `user.auth_provider` / `user.external_id` are still written at sign-up (V37 expand step, keeps an image rollback working) but never read for identity — don't add lookups on them. A user saved without a social row (dev auth, load-test account, test fixtures) cannot social-login
- Withdrawal lifecycle `NORMAL → DELETED → PURGED`, grace period = `User.WITHDRAWAL_GRACE_PERIOD` (30 days, single source for restore, purge and nickname check)
  - Withdraw: `user.withdraw(now)` + `UserSessionRevoker.revokeAll` (refresh tokens revoked, push tokens deleted). Nothing cascades to posts, parties or plans
  - Restore: social login within the grace period only (`UserAuthService.restoreIfWithdrawn`). Refresh never restores
  - Purge: `WithdrawnUserPurgeScheduler` daily 04:00 KST → profile image deleted first (outside the transaction), then `WithdrawnUserPurgeService.purge` bulk-deletes social accounts, preferences, refresh/push tokens and `anonymize()`s the row. The row is never hard-deleted (FKs from posts, parties, chats)
  - Anonymized values must fit MySQL-only limits (`nickname VARCHAR(10) UNIQUE`, `status VARCHAR(10)`) — H2 can't catch an overflow; `UserAnonymizationDockerTest` guards it
- `chk_user_withdrawn_at` (V38, mirrored by `@Check` on `User`): any non-`NORMAL` row must have `withdrawn_at` — change `status` only through `withdraw` / `restore` / `anonymize`
- Only `NORMAL` is active: `User.isActive()` is the check in `JwtAuthFilter`, `ChatStompInterceptor` and `UserAuthService` (register / login / refresh) — a new auth entry point must use it, not `status == DELETED`
- Nickname availability has two checks: `isDuplicateNickname` (API, honors the grace period) vs `validateNicknameAvailable` (create/update, raw UNIQUE column). They disagree only between grace expiry and the next purge run (≤ 1 day)
- No distributed lock on the purge job — revisit (e.g. ShedLock) before running more than one app instance
- **Open issue:** V37 contract step pending — drop `user.auth_provider` / `user.external_id` (`external_id` is still NOT NULL UNIQUE on `user`) and move `BasicUserResponse.authProvider` onto the social accounts
