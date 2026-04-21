---
description: Auth module rules — loaded only when working in auth/ package
globs: ["**/auth/**"]
---

# Auth Module

OAuth2 OIDC via Kakao + Google. JWT issued on successful login.

- `dev` profile: `DevBypassAuthFilter` injects a fixed user — no real JWT needed
- `release` profile: full JWT validation via Spring Security filter chain
- **Never hard-code user IDs** — always extract from `SecurityContextHolder`

**Testing:** In `dev` profile tests, the bypass filter is active — no need to mock auth.
