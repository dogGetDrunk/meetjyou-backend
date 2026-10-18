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
- **JWT `sub` = user uuid** (not email — a social account may have none). Refresh is bound by the signed `userUuid` claim matching the refresh-token record's owner; nothing reads `sub`, so pre-change tokens (`sub` = email) keep working
- **Provider email is optional**: verifiers never reject a login for a missing/unverified email — they pass `email = null`. Don't add an auth step that requires `user.email`
- Role checks use the DB `user.role` loaded per request, not the token's `role` claim. Admin is granted by SQL only (no in-app promotion endpoint)
- **Never hard-code user IDs** — in services use `CurrentUserProvider`, never `SecurityContextHolder`/`SecurityUtil` directly
- `SecurityConfig` public paths (`permitAll()`): actuator health, WS handshake/pub-sub, swagger, `auth/registration|nonce|login|refresh|logout`, `dev/auth/**`, POST `internal/load-test-token` (static secret header), and GET-only on `notices/**`, `terms/**`, version check/latest, nickname-duplicate check
- **Apple JWKS** (`AppleJwksCache`): whole key set cached as one Nimbus `JWKSourceBuilder` source — refresh failure keeps serving the last set for 24h, unknown-kid re-fetch is rate-limited (#177). Don't go back to a per-kid TTL cache: expiry then drops still-valid keys during an Apple outage. Kakao (Spring `RemoteJWKSet`: stale only up to 15 min, no kid-miss cooldown) and Google (no stale-on-error) don't have this yet
- **Open issue:** refresh token rotation is not atomic (double-spend window, #152)
- **Open issue:** the social-login nonce lives in the in-memory `HttpSession` — lost on restart (a login in flight retries once) and breaks with more than one app instance. Move it to shared storage before scaling out (same trigger as the purge-job lock in `user.md`)

**Testing:** tests run on the `test` profile (bypass filter inactive) — mock `CurrentUserProvider` (mockk) or set `SecurityContextHolder` in the test.
