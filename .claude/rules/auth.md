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
- Role checks use the DB `user.role`, not the token's `role` claim. Admin is granted by SQL only (no in-app promotion endpoint)
- **`AuthenticatedUserCache`** (#154): `JwtAuthFilter` reads uuid + role of active users through a 5s `expireAfterWrite` cache; inactive/unknown users are never cached
  - In-app changes apply on the next request: `UserAuthCacheEvictionListener` (`@PostUpdate`/`@PostRemove` on `User`) evicts after the transaction ends. Change user status/role through the entity, never a bulk JPQL/SQL update (ConventionTest)
  - SQL changes outside the app (granting/revoking ADMIN) apply within the TTL. Never switch to `expireAfterAccess` — a client that keeps calling would keep a revoked entry alive
  - TTL 5s is from prod iOS traffic (screens fire bursts of parallel calls): 5s hits 91.6%, 30s 94.8%. Revisit if the app stops bursting (e.g. after #194)
- **Never hard-code user IDs** — in services use `CurrentUserProvider`, never `SecurityContextHolder`/`SecurityUtil` directly
- Actuator (management port 8081, own filter chain): only `health`·`metrics`·`prometheus` are `permitAll`, every other endpoint `denyAll` even if exposed (#156). First layer is the `127.0.0.1` port binding in `docker-compose.yml` — `ManagementPortBindingTest` guards it
- `SecurityConfig` public paths (`permitAll()`): actuator health, WS handshake/pub-sub, swagger, `auth/registration|nonce|login|refresh|logout`, `dev/auth/**`, POST `internal/load-test-token` (static secret header), and GET-only on `notices/**`, `terms/**`, version check/latest, nickname-duplicate check
- **Apple JWKS** (`AppleJwksCache`): whole key set cached as one Nimbus `JWKSourceBuilder` source — refresh failure keeps serving the last set for 24h, unknown-kid re-fetch is rate-limited (#177). Don't go back to a per-kid TTL cache: expiry then drops still-valid keys during an Apple outage. Kakao (Spring `RemoteJWKSet`: stale only up to 15 min, no kid-miss cooldown) and Google (no stale-on-error) don't have this yet
- **Apple REST API** (`AppleAuthClient`, revocation only): `client_secret` is an ES256 JWT minted per call from `auth.apple.team-id`/`key-id`/`private-key` (.p8, one line OK). All three blank = revocation disabled (app still boots), partial = startup failure. Explicit 3s/5s timeouts — the calls run inside the user's request
- **Refresh rotation concurrency** (#152): refresh / logout read token rows only via `findForUpdateByJti` (`FOR UPDATE`), the grace replacement and every logout chain hop included — a plain read lets two requests rotate the same token. Locks follow chain order (old → new); keep it that way to stay deadlock-free. Logout revokes the chain head, not just the presented token. `RefreshTokenReuseDetectedException` is `noRollbackFor` on `refreshToken` — the `revokeAllByUser` before it must commit (a plain `InvalidJwtException` there rolled it back). `RefreshTokenRotationConcurrencyDockerTest` guards all of this
- **Open issue:** reuse detection revokes every session of the user, not just the leaked one — needs a session/family table (#181)
- **Open issue:** the social-login nonce lives in the in-memory `HttpSession` — lost on restart (a login in flight retries once) and breaks with more than one app instance. Move it to shared storage before scaling out (same trigger as the purge-job lock in `user.md`)

**Testing:** tests run on the `test` profile (bypass filter inactive) — mock `CurrentUserProvider` (mockk) or set `SecurityContextHolder` in the test.
