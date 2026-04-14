# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Meetjyou (만나쥬)** — 여행 동행 찾기 서비스. Kotlin + Spring Boot + MySQL 기반으로 Oracle OCI에 배포되어 있다.

## Commands

```bash
./gradlew bootRun                                        # run (dev profile)
./gradlew test                                           # all tests
./gradlew test --tests "FullyQualifiedClass.methodName"  # single test
```

## Testing

- 새로운 기능을 구현한 이후엔 반드시 해당 기능을 검증하는 테스트 코드를 추가할 것
- 기능 추가 또는 코드 수정 후 최종 반영 전 항상 `./gradlew test`로 전체 테스트를 실행해 오류가 없는지 확인할 것

## Code Style

- No wildcard imports
- Follow Kotlin coding conventions
- All log messages in English

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
- Notification outbox — events written to `notification_outbox` in same transaction, dispatched async
- Schema managed via `schema.sql` + `data.sql` (no migration tool); `ddl-auto: none`
- Most endpoints are currently `permit-all` (authorization enforcement is a TODO)
