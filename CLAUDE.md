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
- Enforced in `./gradlew build`: detekt (`config/detekt/detekt.yml`, existing findings frozen in `baseline.xml` — don't add to it), Konsist `ConventionTest` (`!!`, `requireNotNull`, controller DTO `.of()`, service `SecurityUtil`), and a skipped-test guard (a skipped test fails the build)

## Testing

- New features ship with unit + integration tests
- Run full `./gradlew test` before finalizing any change; report actual output

## Verification Workflow

Full spec: `docs/agent-process/README.md`. Hooks in `.claude/settings.json` enforce parts of it.

- **Ledger first** — when 2+ production files change or the design is affected, copy `.claude/templates/ledger.md` to `.claude/work/ledger-<branch>.md` (`/` → `-`), fill requirements (quoted request / conventions / impact analysis), get user approval, then implement. Bug fixes record the failing (red) test output before the fix.
- **Evidence, not assertion** — run the full `./gradlew test` (or `build`) in the foreground, without pipes, `--tests`, or `--dry-run`; the hook records only that. Before reporting completion on multi-file work, run the `requirement-verifier` subagent against the ledger.
- **Completion report** — end with a `result:` line. Include the requirement table with evidence (command, counts, time, `file:line`) and a mandatory "확인하지 못한 것" section ("없음" if empty).
- **Claim hygiene** — mark unverified diagnoses and effort estimates as `[추정]` until checked. Relay subagent or summarizer output as fact only after checking the source. No external facts without a source.
- **Gap protocol (self-triggered, no user instruction needed)** — when the user challenges finished work, when a verification subagent reports a gap, or when you find a miss yourself: fix; add one verdict line per recorded challenge to `.claude/work/gap-triage-<branch>.md` (`- 갭: <요약> → L<n>` naming the layer that missed it / `- 갭 아님: <이유>`); for each real gap add a `### G<n>` entry to `docs/agent-process/gap-log.md` with `- 놓친 층: L<n>` and a `- 추가한 장치:` citing a backticked file changed on this branch or `이슈 #<n>`, and add that mechanism. Prefer hooks/tests/lint rules over prose rules.

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
