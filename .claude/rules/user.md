---
description: User module rules — loaded only when working in user/ package
paths:
  - "**/user/**"
---

# User Module

- Withdrawal = `status = DELETED` + `withdrawnAt` + refresh tokens revoked. Nothing cascades to posts, parties or plans
- DELETED users are rejected in `JwtAuthFilter` and in `UserAuthService` (register / login / refresh) — a new auth entry point must check it too
- Nickname availability has two checks: `isDuplicateNickname` (API, honors the 30-day withdrawal grace period) vs `validateNicknameAvailable` (create/update, raw UNIQUE column)
- **Open issue:** the two nickname checks disagree — the API reports a withdrawn user's nickname as free after 30 days, but create/update still returns 409
