---
description: Auth module rules — loaded only when working in auth/ package
paths:
  - "**/auth/**"
  - "**/config/SecurityConfig.kt"
---

# Auth Module

Social login (Kakao / Google / Apple) — client sends the provider token, server verifies it (`social/*Verifier`, `SocialVerifierRegistry`) and issues its own JWT + refresh token.

- `dev` profile + `dev.bypass.enabled=true`: `DevBypassAuthFilter` injects a fixed user (`DevBypassConfig`)
- Otherwise: `JwtAuthFilter` validates the JWT
- **Never hard-code user IDs** — in services use `CurrentUserProvider`, never `SecurityContextHolder`/`SecurityUtil` directly
- `SecurityConfig` public paths (`permitAll()`): actuator health, WS handshake/pub-sub, swagger, `auth/registration|nonce|login|refresh|logout`, `dev/auth/**`, POST `internal/load-test-token` (static secret header), and GET-only on `notices/**`, `terms/**`, version check/latest, nickname-duplicate check
- **Open issue:** refresh token rotation is not atomic (double-spend window, #152)

**Testing:** tests run on the `test` profile (bypass filter inactive) — mock `CurrentUserProvider` (mockk) or set `SecurityContextHolder` in the test.
