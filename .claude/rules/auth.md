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
- **Token types:** one HS256 key signs both; the `token_type` claim (`access`/`refresh`) separates them (pre-claim tokens: `jti` ⇒ refresh). `Authorization: Bearer` carries **access tokens only** — the filter and STOMP CONNECT reject anything else. Refresh tokens travel in the JSON body of `/auth/refresh`·`/auth/logout` (`RefreshTokenRequest`)
- **`JwtAuthFilter` skips all of `/api/v1/auth/**`** — those endpoints authenticate with their own credential, and an app interceptor may attach an expired access token to `/auth/refresh`. Fail-closed: don't put an endpoint that needs an authenticated user under `/auth/`; it gets no principal and `anyRequest().authenticated()` returns 401
- Role checks use the DB `user.role` loaded per request, not the token's `role` claim. Admin is granted by SQL only (no in-app promotion endpoint)
- **Never hard-code user IDs** — in services use `CurrentUserProvider`, never `SecurityContextHolder`/`SecurityUtil` directly
- `SecurityConfig` public paths (`permitAll()`): actuator health, WS handshake/pub-sub, swagger, `auth/registration|nonce|login|refresh|logout`, `dev/auth/**`, POST `internal/load-test-token` (static secret header), and GET-only on `notices/**`, `terms/**`, version check/latest, nickname-duplicate check
- **Open issue:** refresh token rotation is not atomic (double-spend window, #152)

**Testing:** tests run on the `test` profile (bypass filter inactive) — mock `CurrentUserProvider` (mockk) or set `SecurityContextHolder` in the test.
