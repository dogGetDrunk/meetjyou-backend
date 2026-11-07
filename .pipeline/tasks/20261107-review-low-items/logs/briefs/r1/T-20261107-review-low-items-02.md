# implementer

Implement from the ticket card alone. Obey the card's do-not list. Do not modify acceptance test files; if you suspect an error, only report it. Limit code exploration to the touches, consumes, reference patterns, and reference files named in the card. On completion submit satisfied REQ IDs, API/schema impact, a change summary, and gate results. Take gate failure logs as given and fix.

## Common contract

- Read the project rule documents supplied in Project context (`AGENTS.md`, `CLAUDE.md`, `.claude/rules` that apply to the work paths); follow base_ref and the gate commands

- No exploration beyond the input documents; escalate when more information is needed
  - Input documents = instruction body + files whose paths the instruction names (touches, consumes, reference files, protected tests); named paths may be read
- No questions to the coordinator or the Orca inbox (no reply path): state information missing from the inputs in the report/result, with grounds
- No production deploys, production/shared DB migrations, force pushes, or direct pushes to protected branches
- No access to secret or credential files
- Do not modify the approved intent.md; a needed change goes to the human decision gate
- These instructions (and the ticket card, if any) = the human-approved specification: skip the global instructions' spec card and pre-confirmation; external writes these instructions define are approved, any other external write is forbidden
- When limits are exhausted, wait and resume; never switch models on your own
- Follow the live preamble's CLI, handle, task ID, dispatch ID, and heartbeat interval
- At each natural work boundary, check the Orca inbox, handle messages, then ack
- Send worker_done exactly once, including on failure, with task ID, dispatch ID, and succeeded/failed
- After the completion signal stay idle; never reuse an old dispatch ID
- Result: 3 sentences starting with result:, 1000 characters total at most
- Result must include: satisfied requirement IDs, changed files, verification commands and their actual results, remaining blocking items
- Write worker_done, the result: sentences, and free text in JSON reports in English

## Delivery contract

- Approved external writes: commits and pushes on the ticket work branch and draft PR creation; any other GitHub write (issues, comments, labels, merge, ready transition) is forbidden
- If the instructions contain `# PR link`, include that line verbatim in the PR body
- If the instructions contain `# Acceptance test files`, copy with the given cp command and commit; never modify the content (sha256 checked before gate, mismatch counts as failure)
- Verify on the ticket-scoped work branch, then commit, push, and create a draft PR
- No direct commits or pushes to default or protected branches
- PR merge is coordinator-only; the implementer never merges
- Before worker_done confirm every working-tree change is committed; never report an uncommitted state as success
- Submit PR number, commit SHA, satisfied REQ IDs, endpoint/schema impact, verification results; before worker_done also write the `# Machine report` the instructions name (on failure its `cause` decides who acts next: anything but `implementation` goes to the designer, no retry)


Project context (explicitly supplied):
{"project_rules": [{"path": "AGENTS.md", "content": "# AGENTS.md\n\n- 대상: 이 저장소에서 작업하는 Codex 및 AGENTS.md를 읽는 에이전트\n- 작업 시작 전 [CLAUDE.md](CLAUDE.md) 전체 필수 확인·준수 — 프로젝트 개요, Git 흐름, 코딩 컨벤션, 검증, 아키텍처, 파이프라인 안전 규칙의 단일 원본\n- 모듈별 상세 규칙: `.claude/rules/*.md`의 `paths:`와 변경 대상 경로를 대조해 해당 문서 필수 확인 — Codex의 자동 로딩 가정 금지\n- 프로젝트 파이프라인 사용법·설정: [.pipeline/README.md](.pipeline/README.md), [.pipeline/project.yaml](.pipeline/project.yaml)\n- 공통 규칙 변경 위치: `CLAUDE.md`; 이 파일에 동일 규칙 복제 금지\n"}, {"path": "CLAUDE.md", "content": "# CLAUDE.md\n\nThis file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.\n\n## Project Overview\n\n**Meetjyou (만나쥬)** — 여행 동행 찾기 서비스. Kotlin + Spring Boot + MySQL 기반. 앱 서버(nginx·certbot·alloy 포함)는 AWS EC2 `t4g.small`(arm64), MySQL과 이미지 스토리지(Object Storage)는 OCI — 배치 근거·재검토 조건은 `docs/adr/0003-multi-cloud-component-placement.md`.\n\n## Git Workflow\n\n### Before starting\n`git branch --show-current`로 현재 브랜치를 확인하고 사용자에게 알릴 것. 예상 브랜치가 아니면 작업을 시작하지 말고 사용자에게 확인받을 것.\n\n### Committing & PRs\n- 작업(논리적 단위)이 끝나면 확인 없이 커밋·푸시까지 진행. 커밋 메시지·PR 본문도 사전 확인 불필요\n- PR 생성·머지도 확인 없이 진행 — 푸시 후 PR 생성, CI 통과 시 머지. 사용자가 머지를 지시하면 재확인 없이 즉시 머지\n- 커밋 메시지와 PR 본문은 한국어 + 개조식(불릿 위주 요약, 서술형 문장 지양)으로 작성.\n\n### Branching\n- 새 브랜치는 `git fetch` 후 `origin/main`에서 분기. 로컬 main pull 불필요(워크트리도 `origin/main`에서 분기)\n- 공유 체크아웃의 사용자 브랜치·미커밋 변경은 건드리지 않음\n\n### Next task\n- \"다음 할 일\"의 출처 = 팀 노션 스프린트 보드의 내 태스크 → 연결된 GitHub 이슈. 대화 기억·세션 요약 기반 추천 금지\n- 답할 때 출처(보드 태스크명 + 이슈 번호) 명시. 보드와 연결 안 된 열린 이슈는 \"보드 미연결\"로 따로 표시\n- `/task` 후보 출처도 동일(보드 \"내 할 일\" 뷰 → 연결 이슈), 이슈 없는 보드 태스크도 시작 가능\n\n## Commands\n\n```bash\nSPRING_PROFILES_ACTIVE=dev,db,secrets \\\n  GOOGLE_APPLICATION_CREDENTIALS=\"$(pwd)/src/main/resources/firebase/meetjyou-firebase-adminsdk.json\" \\\n  ./gradlew bootRun                                      # run (dev profile, local)\n./gradlew test                                           # all tests\n./gradlew test --tests \"FullyQualifiedClass.methodName\"  # single test\n./gradlew dockerTest                                     # src/dockerTest (Testcontainers MySQL); needs a running Docker daemon\n```\n\n## Code Style\n\n- Kotlin coding conventions\n- No wildcard imports\n- Logs & comments in English (docs/commits in Korean)\n- Magic numbers / repeated strings → constants\n- Enforced in `./gradlew build`: detekt (`config/detekt/detekt.yml`, existing findings frozen in `baseline.xml` — don't add to it), Konsist `ConventionTest` (`!!`, `requireNotNull`, controller DTO `.of()`, service `SecurityUtil`, SQL that sets autocommit/isolation/read-only and stored routines — the driver caches session state via `useLocalSessionState`; bulk UPDATE/DELETE of the user table — it skips the auth-cache eviction listener), and a skipped-test guard (a skipped test fails the build)\n\n## Testing\n\n- New features ship with unit + integration tests\n- Run full `./gradlew test` before finalizing any change; report actual output\n- dockerTest lock-wait detection: poll `performance_schema.data_lock_waits`, never `information_schema.innodb_trx.trx_state` (cached view, misses real waits → flaky CI)\n\n## Verification Workflow\n\nNo hook-enforced gate — the vgate plugin was retired here on 2026-10-13. The Testing rules above and the `./gradlew build` checks are the gate.\n\n- Past gap records and project-side static checks: `docs/agent-process/` (history, no longer enforced by hooks)\n- Pre-plugin in-repo version: tag `agent-process-inrepo-final`\n\n## Conventions\n\n### Current user\nUse `CurrentUserProvider` (`common/util/CurrentUserProvider.kt`) — never call `SecurityUtil` directly in service methods.\n```kotlin\nval user = currentUserProvider.user   // throws UserNotFoundException if not found\nval uuid = currentUserProvider.uuid   // UUID only, no DB hit\n```\n\n### Null safety & exceptions\n`?: throw` Elvis operator exclusively. No `requireNotNull()`, no `if (x == null) throw`, no `!!`.\n```kotlin\nval party = partyRepository.findByUuid(uuid) ?: throw PartyNotFoundException(uuid)\n```\n\n### Error responses\n- HTTP status lives on `ErrorCode(status, message)`, one handler for every `BusinessException` — never pick a base class for its status. `ErrorCodeStatusTest` pins every code's status: a change edits that table on purpose\n- Rejected for the current state (full, closed, already member) → 409, malformed input → 400, outside dependency down → `ExternalServiceException` (503)\n\n### DTO conversion\nService returns DTOs via companion `.of()` factory. Controllers never call `.of()` themselves.\n```kotlin\nreturn GetPartyResponse.of(party)   // in service\n```\n\n### Method length\n30-line hard cap on function bodies. Extract private helpers (`validateXxx` / `buildXxx` / `resolveXxx`).\n\n### N+1 prevention\nNever call a repository inside a loop. Batch-load with `findAllByXxxIn(ids)`, then `groupBy` or `associateBy`.\n\n### Scope functions\nNo 3-chain scope functions (`.apply{}.also{}.let{}`). Use explicit statements instead.\nString concatenation via template literals only — no `+` operator.\n\n### @Transactional\nRead-only methods require `@Transactional(readOnly = true)`. Write methods use `@Transactional`.\n\n### Concurrency bugs\n- Rare races from unnatural request overlaps (e.g. lock-order deadlocks) → handle after the fact, don't restructure locking: full rollback + 409 `CONCURRENT_REQUEST_CONFLICT` (`PessimisticLockingFailureException` in `GlobalExceptionHandler`), Discord alert kept to measure frequency\n- Structural prevention (e.g. party-row-first lock) only once alerts show it actually happens — #199, accepted cases in `.claude/rules/party.md`\n- Lost unique-index races on paths without their own translation → `UniqueConstraintConflicts` (constraint name → `ErrorCode`, 409 + observation alert); an unmapped name stays a 500. A new unique index on such a path needs an entry\n- Not covered by this: races that corrupt data without an error (lost update, stale-snapshot overwrite) — a 409 never fires for them, so judge those separately\n\n### UUID columns\nEvery entity UUID column (business key or FK) carries `@JdbcTypeCode(Types.VARCHAR)`. H2 tests don't catch a missing one — the column type silently diverges from MySQL.\n\n## Architecture\n\n**Profiles:** `dev` (JWT bypass + debug logging) / `db,secrets,release` (production)\n\n**Packages:**\n```\ncom.dogGetDrunk.meetjyou/\n├── auth/               # JWT, social login (Kakao/Google/Apple), dev bypass filter\n├── user/               # accounts, profiles\n├── party/              # group parties + applications\n├── userparty/          # user↔party membership (role, member status)\n├── post/               # travel listings\n├── plan/               # trip planning + markers\n├── chat/               # WebSocket chat (room, message, participant, connection, event)\n├── notification/       # push notifications, transactional outbox\n├── notificationcenter/ # in-app notification center (notices, sent/received applications)\n├── notice/             # admin notices\n├── terms/              # terms versions + agreements\n├── version/            # app version check / forced update\n├── preference/         # user preferences + compatibility matching\n├── image/, cloud/      # OCI Object Storage PAR URLs (thumbnails are made client-side)\n├── config/             # Spring config + @ConfigurationProperties\n└── common/             # exceptions, utilities, idempotency\n```\n\n**Key patterns:**\n- Notification outbox — publish `NotificationEvent`, never send push directly; full pattern in `.claude/rules/notification.md`\n- Schema managed via Flyway (`db/migration/V*.sql`); `ddl-auto: none`\n- `SecurityConfig` defaults to `.anyRequest().authenticated()` — new endpoints are private unless whitelisted there (public paths: `.claude/rules/auth.md`). Admin actions use `@PreAuthorize(\"hasAuthority('ADMIN')\")`.\n\n### Module context\n- Module-only, non-obvious invariants (locking, state machines, delete order, open issues) live in `.claude/rules/<module>.md` with `paths:` frontmatter — loaded only when matching files are touched\n- Don't write what the code shows at a glance (class lists, endpoint lists); keep this file to cross-cutting rules\n- No registry of rule files here — rules are discovered by glob\n\n## Compact Instructions\n\nContext-compaction preserve/drop rules → global CLAUDE.md §9 (pure meta, not a project spec).\n\n## Pipeline guardrails\n\n- 파이프라인 공통 계약: 전역 `agent-pipeline guide` 및 설치본 `defaults/prompts/roles/`, 프로젝트 설정 `.pipeline/project.yaml`\n- 태스크 시작: `/task`, 또는 명령 없는 구현 요청 시 수정 전 1회 확인(\"태스크로 진행 / S 예외로 바로 수정\") — 이 확인이 스펙 카드 대체\n- 태스크 문서: `.pipeline/tasks/<YYYYMMDD-slug>/`, 승인 = intent PR 머지(M·L) 또는 `task/<키>` push(S) — 엔진이 base ref 반영 여부 검사\n- 운영 배포·운영/공유 DB migration·force push·보호 브랜치 직접 push·시크릿 접근 금지\n- 승인된 intent 수정 금지, 변경 필요 시 사람 결정 게이트\n- 구현 에이전트의 수용 테스트 수정 금지, 오류 의심 시 escalation\n- task/dispatch ID는 Orca live preamble 기준, 실패 포함 worker_done 정확히 1회\n- timeout/heartbeat 부재만으로 중복 워커 시작 금지, 종료 확인 후 재분배\n"}, {"path": ".claude/rules/notification.md", "content": "---\ndescription: Notification module rules — loaded only when working in notification/ package\npaths:\n  - \"**/notification/**\"\n---\n\n# Notification Module\n\nOutbox pattern:\n1. Business code publishes `NotificationEvent` (or `NoticeBroadcastEvent` / `TermsReconsentEvent`)\n2. `*EventHandler` (`@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`) writes a `notification_outbox` row — preference check + `dedupKey` dedup\n3. `NotificationDispatcher` (`@Scheduled`) claims rows and sends via FCM\n\n- **Never dispatch push notifications synchronously** inside a business transaction\n- Publisher must run inside a transaction — AFTER_COMMIT listeners silently drop events otherwise (ADR-0002)\n- Outbox row: `type`, `data_json`, `dedup_key`, `status` (`PENDING → SENDING → SENT`; transient failure → back to `PENDING` with backoff on `available_at`; retries exhausted or no deliverable token → `DEAD`; `FAILED` is unused), `attempts`, `delivered_token_ids`\n- **`available_at` has two meanings:** retry time for `PENDING`, lease expiry for `SENDING`. The claim query picks both once it has passed, so a row stranded by a crash mid-send is reclaimed after the lease (10 min) — there is no separate reaper\n- The claim sets the lease with the DB clock (`NOW()`) and counts the attempt up front; a row reclaimed at the attempt limit goes `DEAD` without sending (poison guard)\n- Delivery is tracked per token in `delivered_token_ids` (push_token ids): a retry skips tokens that already got it. A permanent failure on one token is left out of the row verdict; only `UNREGISTERED` deactivates the token (`INVALID_ARGUMENT` may be a payload problem)\n- Delivery is at-least-once: a crash mid-send can duplicate that row's pushes\n- **Accepted risk:** the outbox row is written after the originating tx commits, so a crash in that window loses the push. In-app views cover most types — rationale and revisit conditions in ADR-0002 (2026-10-11 update)\n- Firebase config in `src/main/resources/firebase/` (ignored by Claude)\n"}], "base_ref": "origin/main", "gate": [["./gradlew", "test", "build"]]}

Human-facing language: write PR titles and bodies, commit messages, task documents (intent, spec, decisions, tickets, AT cases, result) and any reply to a human in Korean.
---
id: T-20261107-review-low-items-02
title: 예약 작업 예외 Discord 알림 + FCM 타임아웃·발송 기록 실패 격리
tier: high
effort_override: null
depends_on: []
requirements: [REQ-003, REQ-004]
acceptance_tests: [AT-003-1, AT-003-2, AT-004-1, AT-004-2]
touches:
  - src/main/java/com/dogGetDrunk/meetjyou/config/ScheduleConfig.kt
  - src/main/java/com/dogGetDrunk/meetjyou/config/ScheduledTaskErrorHandler.kt
  - src/main/java/com/dogGetDrunk/meetjyou/notification/sender/FirebaseConfig.kt
  - src/main/java/com/dogGetDrunk/meetjyou/notification/dispatcher/NotificationDispatcher.kt
consumes:
  - DiscordAlertService.sendOperationalAlert(source, exceptionClass, summary, detail)
  - org.springframework.scheduling.annotation.SchedulingConfigurer
  - org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
  - org.springframework.util.ErrorHandler
  - NotificationOutboxRepository.updateResult
  - PushTokenRepository.deactivateAllByIdIn
  - com.google.firebase.FirebaseOptions.Builder.setConnectTimeout/setReadTimeout/setWriteTimeout
produces:
  - 'ScheduledTaskErrorHandler(discordAlertService: DiscordAlertService) : org.springframework.util.ErrorHandler (config 패키지, @Component 또는 ScheduleConfig @Bean)'
  - 'ScheduleConfig : SchedulingConfigurer — @Bean scheduledTaskScheduler(): ThreadPoolTaskScheduler (poolSize 상수 2, threadNamePrefix "scheduled-", errorHandler), configureTasks에서 registrar.setTaskScheduler(그 빈)'
  - 'FirebaseConfig.buildFirebaseOptions(credentials: GoogleCredentials): FirebaseOptions (public, 타임아웃 상수 10,000ms 3종)'
  - 'NotificationDispatcher.dispatchBatch: 항목별 기록 실패 격리, deactivateTokens 항상 실행'
status: pending
---

## 목표

- 어떤 `@Scheduled` 작업이든 예외 시 Discord 운영 알림 1건(작업별 dedup)
- FCM 호출에 유한 타임아웃, 결과 기록 실패 한 건이 같은 배치의 나머지·무효 토큰 비활성화를 끊지 않음

## 구현 범위

- 할 것
  - `ScheduledTaskErrorHandler.handleError(t)`: ERROR 로그(스택 포함) → `sendOperationalAlert(source, exceptionClass = t::class.java.simpleName, summary = 메시지 요약)`, 다시 던지지 않음
    - `source` = `"ScheduledTask:<simpleName>.<method>"` — `t.stackTrace`에서 `com.dogGetDrunk.meetjyou` 로 시작하는 첫 프레임(클래스명은 마지막 `.` 뒤), 없으면 `"ScheduledTask"`. 접두 패키지·`"ScheduledTask"`는 상수
    - summary: 메시지 null이면 클래스명 등 non-null 대체, 길이 상한은 상수
  - `ScheduleConfig`: `SchedulingConfigurer` 구현 + `@Bean` `scheduledTaskScheduler`(`ThreadPoolTaskScheduler`, 스레드 수 상수 2, 접두 `scheduled-`, `setErrorHandler(scheduledTaskErrorHandler)`), `configureTasks`에서 `registrar.setTaskScheduler(scheduledTaskScheduler)`
    - 근거: 실측상 Boot `taskScheduler`는 `messageBrokerTaskScheduler` 때문에 생성되지 않고 `@Scheduled`가 브로커 스케줄러에서 돎 → Customizer는 효과 없음 (spec REQ-003)
  - `FirebaseConfig`: `buildFirebaseOptions(credentials)` 분리, 연결·읽기·쓰기 타임아웃 상수 `10_000`ms. `GoogleCredentials.getApplicationDefault()`는 `firebaseApp()` 빈 메서드에 남김
  - `NotificationDispatcher.dispatchBatch`
    - `exhausted` DEAD 기록을 항목별 try/catch — 실패 시 ERROR 로그(outboxId) 후 다음 항목
    - 루프 catch 안 `recordRetry` 실패도 ERROR 로그(outboxId) 후 다음 항목
    - `deactivateTokens(invalidTokenIds)`는 루프 후 항상 실행(`items.isEmpty()` 조기 반환 경로 포함 여부는 기존과 동일 — 모인 토큰 없으면 호출 없음)
    - 30줄 상한 지키도록 `recordSafely` 류 private 헬퍼 추출
- 하지 말 것
  - `scheduledTaskScheduler` 외 새 `TaskScheduler`/`ScheduledExecutorService` 빈 생성 금지, `messageBrokerTaskScheduler`·`WebSocketConfig` 수정 금지, 스레드 수 2 외 값 금지
  - 대상 예약 작업 4개(`NotificationDispatcher.scheduledDispatch`·`reportDeadItems`, `PostViewService.cleanupOldViewLogs`, `WithdrawnUserPurgeScheduler.purgeExpiredWithdrawals`) 수정 금지
  - `dispatchBatch`·`scheduledDispatch`에 트랜잭션 추가 금지 (C-r1-07 전제)
  - 기존 `NotificationDispatcherTest`·`FcmNotificationSenderTest` 수정 금지

## 확인된 전제 (메인 설계, 2026-11-07 컨텍스트 실측)

- `TaskScheduler` 빈은 `messageBrokerTaskScheduler` 하나뿐, Boot `taskScheduler` 없음, `@Scheduled` 4개가 `MessageBroker-*` 스레드에서 실행 → 위 `SchedulingConfigurer` 방식으로 확정
- 두 `TaskScheduler` 빈이 생겨도 타입 주입하는 곳 없음(src/main 검색) — 모호성 오류가 나면 escalation

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/user/WithdrawnUserPurgeScheduler.kt` — `sendOperationalAlert` 호출 형태·상수
- `src/main/java/com/dogGetDrunk/meetjyou/config/ScheduleConfig.kt` — `@EnableScheduling`
- `src/test/java/com/dogGetDrunk/meetjyou/notification/dispatcher/NotificationDispatcherTest.kt` — 디스패처 mockk 구성
- `src/test/java/com/dogGetDrunk/meetjyou/auth/AuthSecurityReproIntegrationTest.kt` — `@SpringBootTest` + `@MockBean`

## 참조 파일 (읽기 허용)

- `src/main/java/com/dogGetDrunk/meetjyou/common/discord/DiscordAlertService.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/config/WebSocketConfig.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/user/WithdrawnUserPurgeScheduler.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/user/WithdrawnUserPurgeService.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/post/view/PostViewService.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/outbox/NotificationOutbox.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/outbox/NotificationOutboxRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/push/PushTokenRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/sender/PushNotificationSender.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/target/NotificationTargetResolver.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/target/PushTarget.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/notification/dispatcher/NotificationDispatcherTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/notification/sender/FcmNotificationSenderTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/common/discord/DiscordAlertServiceTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/user/WithdrawnUserPurgeSchedulerTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/auth/AuthSecurityReproIntegrationTest.kt`
- `src/test/resources/application-test.yml`
- `.claude/rules/notification.md`
- `.claude/rules/user.md`

## 관련 spec 발췌

- REQ-003: `SchedulingConfigurer` + 전용 `scheduledTaskScheduler`(스레드 2, `ErrorHandler`) — Boot `taskScheduler`는 브로커 스케줄러 때문에 없음(실측). ERROR 로그 + `sendOperationalAlert`, 미전파(LOG_AND_SUPPRESS + 알림). `source` = `"ScheduledTask:<클래스 simpleName>.<메서드>"`(앱 패키지 첫 프레임, 없으면 `"ScheduledTask"`) — dedup 키 `status:exceptionClass:location(=source)`(C-r1-05). 대상 4개 작업 수정 없음(C-r1-04), 스레드 수 운영 실효값 2 유지
- REQ-004: 연결·읽기·쓰기 10,000ms 상수, 옵션 생성부를 자격 증명 인자 함수로 분리. `recordRetry`·`exhausted` DEAD 기록 실패 → ERROR 로그(outboxId) 후 다음 항목, 루프 후 `deactivateTokens` 항상. 기록 실패 항목은 SENDING + 리스로 600초 뒤 재선점(추가 처리 없음). `updateResult`·`deactivateAllByIdIn`은 리포지토리 `@Transactional` 건별 커밋 — 배치 루프 트랜잭션 추가 금지(C-r1-07)

## 분류 근거

- 스케줄러 공통 오류 처리는 모든 예약 작업에 영향, 디스패처는 outbox 리스·재시도 상태와 트랜잭션 경계 전제에 의존 → State/concurrency high → high
- REQ-003·004는 코드상 독립이나 spec 마일스톤이 묶음(T-02), 같은 "예약 작업이 멈추거나 조용히 실패" 주제·touches 4개로 작음
- effort_override 없음

## 실행 계획·DAG

- 선행 없음, 다른 티켓과 병렬. touches 교집합 없음

## 검증·증거 담당

- AT-003-1: `./gradlew test --tests "*ScheduledTaskErrorHandlerTest"`
- AT-003-2: `./gradlew test --tests "*ScheduledTaskAlertIntegrationTest"`
- AT-004-1: `./gradlew test --tests "*FirebaseConfigTimeoutTest"`
- AT-004-2: `./gradlew test --tests "*NotificationDispatcherRecordFailureTest"` + 기존 `*NotificationDispatcherTest`
- 구현 워커: 보호 테스트 불변, 최종 SHA에서 `./gradlew test build` 실제 출력 제출

# PR link
- Include this exact line in the PR body: `Refs dogGetDrunk/meetjyou-backend#231`
# Acceptance test files (protected, engine-checked)
- Copy with the commands below and commit as-is; never modify the content (sha256 byte equality is checked before gate)
- If you suspect an error, do not fix it; report it in the result
```sh
mkdir -p "$(dirname 'src/test/java/com/dogGetDrunk/meetjyou/config/ScheduledTaskAlertIntegrationTest.kt')" && cp '/Users/serendipity/orca/workspaces/meetjyou-backend/20261107-review-low-items/.pipeline/tasks/20261107-review-low-items/tests/code/src/test/java/com/dogGetDrunk/meetjyou/config/ScheduledTaskAlertIntegrationTest.kt' 'src/test/java/com/dogGetDrunk/meetjyou/config/ScheduledTaskAlertIntegrationTest.kt'
mkdir -p "$(dirname 'src/test/java/com/dogGetDrunk/meetjyou/config/ScheduledTaskErrorHandlerTest.kt')" && cp '/Users/serendipity/orca/workspaces/meetjyou-backend/20261107-review-low-items/.pipeline/tasks/20261107-review-low-items/tests/code/src/test/java/com/dogGetDrunk/meetjyou/config/ScheduledTaskErrorHandlerTest.kt' 'src/test/java/com/dogGetDrunk/meetjyou/config/ScheduledTaskErrorHandlerTest.kt'
mkdir -p "$(dirname 'src/test/java/com/dogGetDrunk/meetjyou/notification/dispatcher/NotificationDispatcherRecordFailureTest.kt')" && cp '/Users/serendipity/orca/workspaces/meetjyou-backend/20261107-review-low-items/.pipeline/tasks/20261107-review-low-items/tests/code/src/test/java/com/dogGetDrunk/meetjyou/notification/dispatcher/NotificationDispatcherRecordFailureTest.kt' 'src/test/java/com/dogGetDrunk/meetjyou/notification/dispatcher/NotificationDispatcherRecordFailureTest.kt'
mkdir -p "$(dirname 'src/test/java/com/dogGetDrunk/meetjyou/notification/sender/FirebaseConfigTimeoutTest.kt')" && cp '/Users/serendipity/orca/workspaces/meetjyou-backend/20261107-review-low-items/.pipeline/tasks/20261107-review-low-items/tests/code/src/test/java/com/dogGetDrunk/meetjyou/notification/sender/FirebaseConfigTimeoutTest.kt' 'src/test/java/com/dogGetDrunk/meetjyou/notification/sender/FirebaseConfigTimeoutTest.kt'
```
- src/test/java/com/dogGetDrunk/meetjyou/config/ScheduledTaskAlertIntegrationTest.kt: sha256 67fcc017cd3580887d7bce94d954dd309ecee170f5e029dc962492659d1eb680
- src/test/java/com/dogGetDrunk/meetjyou/config/ScheduledTaskErrorHandlerTest.kt: sha256 70f9a679bc3ad5042ce6a50a40bf54bbe764dffc2de05c2091ead1fc863e0c62
- src/test/java/com/dogGetDrunk/meetjyou/notification/dispatcher/NotificationDispatcherRecordFailureTest.kt: sha256 6d52f858cde0b8ffd3b28a4cfdbf4f361a36c1d2f0f36b15575342a7f1fe3ac3
- src/test/java/com/dogGetDrunk/meetjyou/notification/sender/FirebaseConfigTimeoutTest.kt: sha256 1b9b27631f10abea1c1d82b52d0eea27c0429c1fba4ab32ff3efe19678af5e41
# Machine report (engine-read)
Before worker_done write JSON (UTF-8, no BOM) to `.pipeline/logs/runtime/reports/T-20261107-review-low-items-02/implementer.json` under the worktree root, replacing any earlier file; it is git-excluded, never commit it:
```json
{
  "schema": "implement/v1",
  "ticket": "T-20261107-review-low-items-02",
  "dispatch_id": "<your dispatch id>",
  "outcome": "succeeded|failed",
  "cause": "implementation|protected_test|spec|instruction|environment",
  "evidence": "paths and the exact error"
}
```
- cause: implementation = your code; protected_test = a protected acceptance test is wrong (lint, compile, fixture, assertion); spec = the ticket or spec contradicts itself or lacks information; instruction = these instructions contradict each other or the project rules; environment = build tools, dependencies, network or services
- Any cause but implementation stops the retries and hands the ticket to the designer: name the files and quote the error in evidence
- outcome succeeded: cause implementation