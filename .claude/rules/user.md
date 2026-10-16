---
description: User module rules — loaded only when working in user/ package
paths:
  - "**/user/**"
---

# User Module

- Social identity lives in `user_social_account` — `(provider, external_id)` unique service-wide, one account per provider per user. Owned by `User` (`socialAccounts`, cascade + orphan removal): add via `user.addSocialAccount(...)`, no separate repository. Registration and login resolve the user through `UserRepository.findBySocialAccount` only
- `user.auth_provider` / `user.external_id` are still written at sign-up (V37 expand step, keeps an image rollback working) but never read for identity — don't add lookups on them. A user saved without a social row (dev auth, load-test account, test fixtures) cannot social-login
- Withdrawal = `status = DELETED` + `withdrawnAt` + refresh tokens revoked. Nothing cascades to posts, parties or plans
- DELETED users are rejected in `JwtAuthFilter` and in `UserAuthService` (register / login / refresh) — a new auth entry point must check it too
- Nickname availability has two checks: `isDuplicateNickname` (API, honors the 30-day withdrawal grace period) vs `validateNicknameAvailable` (create/update, raw UNIQUE column)
- **Open issue:** the two nickname checks disagree — the API reports a withdrawn user's nickname as free after 30 days, but create/update still returns 409
- **Open issue:** V37 contract step pending — drop `user.auth_provider` / `user.external_id` (`external_id` is still NOT NULL UNIQUE on `user`) and move `BasicUserResponse.authProvider` onto the social accounts
