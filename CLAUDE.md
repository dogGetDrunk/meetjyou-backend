# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Meetjyou (만나쥬)** — 여행 동행 찾기 서비스. Kotlin + Spring Boot + MySQL 기반으로 Oracle OCI에 배포되어 있다.

## Git Workflow

### Before starting
`git branch --show-current`로 현재 브랜치를 확인하고 사용자에게 알릴 것. 예상 브랜치가 아니면 작업을 시작하지 말고 사용자에게 확인받을 것.

### Committing & PRs
- 커밋 생성, PR 생성은 사용자가 명시적으로 요청할 때만 수행. 작업을 마쳤다고 자동으로 커밋하거나 PR을 올리지 말 것.
- 커밋 메시지와 PR 본문은 한국어 + 개조식(불릿 위주 요약, 서술형 문장 지양)으로 작성.

### After a PR merges
로컬을 main으로 전환하고 pull할 것: `git checkout main && git pull origin main`

## Commands

```bash
SPRING_PROFILES_ACTIVE=dev,db,secrets \
  GOOGLE_APPLICATION_CREDENTIALS="$(pwd)/src/main/resources/firebase/meetjyou-firebase-adminsdk.json" \
  ./gradlew bootRun                                      # run (dev profile, local)
./gradlew test                                           # all tests
./gradlew test --tests "FullyQualifiedClass.methodName"  # single test
```

## Code Style

- Kotlin coding conventions
- No wildcard imports
- Logs & comments in English (docs/commits in Korean)
- Magic numbers / repeated strings → constants

## Testing

- New features ship with unit + integration tests
- Run full `./gradlew test` before finalizing any change; report actual output

## Conventions

### Current user
Use `CurrentUserProvider` (`common/util/CurrentUserProvider.kt`) — never call `SecurityUtil` directly in service methods.
```kotlin
val user = currentUserProvider.user   // throws UserNotFoundException if not found
val uuid = currentUserProvider.uuid   // UUID only, no DB hit
```

### Null safety & exceptions
`?: throw` Elvis operator exclusively. No `requireNotNull()`, no `if (x == null) throw`, no `!!`.
```kotlin
val party = partyRepository.findByUuid(uuid) ?: throw PartyNotFoundException(uuid)
```

### DTO conversion
Service returns DTOs via companion `.of()` factory. Controllers never call `.of()` themselves.
```kotlin
return GetPartyResponse.of(party)   // in service
```

### Method length
30-line hard cap on function bodies. Extract private helpers (`validateXxx` / `buildXxx` / `resolveXxx`).

### N+1 prevention
Never call a repository inside a loop. Batch-load with `findAllByXxxIn(ids)`, then `groupBy` or `associateBy`.

### Scope functions
No 3-chain scope functions (`.apply{}.also{}.let{}`). Use explicit statements instead.
String concatenation via template literals only — no `+` operator.

### @Transactional
Read-only methods require `@Transactional(readOnly = true)`. Write methods use `@Transactional`.

## Architecture

**Profiles:** `dev` (JWT bypass + debug logging) / `db,secrets,release` (production)

**Packages:**
```
com.dogGetDrunk.meetjyou/
├── auth/          # JWT, OAuth2 OIDC (Kakao/Google), dev bypass filter
├── user/          # accounts, profiles
├── party/         # group parties + applications
├── post/          # travel listings
├── plan/          # trip planning + markers
├── chat/          # WebSocket chat (room, message, participant, connection, event)
├── notification/  # push notifications, transactional outbox
├── preference/    # user preferences + compatibility matching
├── image/         # upload/thumbnail via OCI
├── config/        # Spring config + @ConfigurationProperties
└── common/        # exceptions, utilities
```

**Key patterns:**
- `DevBypassAuthFilter` — skips JWT in `dev` profile
- Notification outbox — see `.claude/rules/notification.md` for the full pattern
- Schema managed via Flyway (`db/migration/V*.sql`); `ddl-auto: none`
- `SecurityConfig` defaults to `.anyRequest().authenticated()`; `permitAll()` is scoped to actuator health, WS handshake/pub-sub, swagger, `auth/registration|nonce|login|refresh|logout`, `dev/auth/**`, and GET-only on `notices/**`, `terms/**`, version check/latest, nickname-duplicate check. Admin actions use `@PreAuthorize("hasAuthority('ADMIN')")`.

## Compact Instructions

Context-compaction preserve/drop rules → global CLAUDE.md §9 (pure meta, not a project spec).
