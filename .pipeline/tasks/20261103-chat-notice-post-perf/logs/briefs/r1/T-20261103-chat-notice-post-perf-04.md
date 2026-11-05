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
- When limits are exhausted, wait and resume; never switch models on your own
- Follow the live preamble's CLI, handle, task ID, dispatch ID, and heartbeat interval
- At each natural work boundary, check the Orca inbox, handle messages, then ack
- Send worker_done exactly once, including on failure, with task ID, dispatch ID, and succeeded/failed
- After the completion signal stay idle; never reuse an old dispatch ID
- Result: 3 sentences starting with result:, 1000 characters total at most
- Result must include: satisfied requirement IDs, changed files, verification commands and their actual results, remaining blocking items
- Write worker_done, the result: sentences, and free text in JSON reports in English

## Delivery contract

- These instructions and the ticket card = the human-approved specification: proceed without a spec card or pre-confirmation
  - Commits and pushes on the ticket work branch and draft PR creation are approved external writes, not subject to the global instructions' "confirm before external writes"
  - Any other GitHub write (issues, comments, labels, merge, ready transition) is forbidden
- If the instructions contain `# PR link`, include that line verbatim in the PR body
- If the instructions contain `# Acceptance test files`, copy with the given cp command and commit; never modify the content (sha256 checked before gate, mismatch counts as failure)
- Verify on the ticket-scoped work branch, then commit, push, and create a draft PR
- No direct commits or pushes to default or protected branches
- PR merge is coordinator-only; the implementer never merges
- Before worker_done confirm every working-tree change is committed; never report an uncommitted state as success
- Submit PR number, commit SHA, satisfied REQ IDs, endpoint/schema impact, verification results; before worker_done also write the `# Machine report` the instructions name (on failure its `cause` decides who acts next: anything but `implementation` goes to the designer, no retry)


Project context (explicitly supplied):
{"project_rules": [{"path": "AGENTS.md", "content": "# AGENTS.md\n\n- 대상: 이 저장소에서 작업하는 Codex 및 AGENTS.md를 읽는 에이전트\n- 작업 시작 전 [CLAUDE.md](CLAUDE.md) 전체 필수 확인·준수 — 프로젝트 개요, Git 흐름, 코딩 컨벤션, 검증, 아키텍처, 파이프라인 안전 규칙의 단일 원본\n- 모듈별 상세 규칙: `.claude/rules/*.md`의 `paths:`와 변경 대상 경로를 대조해 해당 문서 필수 확인 — Codex의 자동 로딩 가정 금지\n- 프로젝트 파이프라인 사용법·설정: [.pipeline/README.md](.pipeline/README.md), [.pipeline/project.yaml](.pipeline/project.yaml)\n- 공통 규칙 변경 위치: `CLAUDE.md`; 이 파일에 동일 규칙 복제 금지\n"}, {"path": "CLAUDE.md", "content": "# CLAUDE.md\n\nThis file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.\n\n## Project Overview\n\n**Meetjyou (만나쥬)** — 여행 동행 찾기 서비스. Kotlin + Spring Boot + MySQL 기반. 앱 서버(nginx·certbot·alloy 포함)는 AWS EC2 `t4g.small`(arm64), MySQL과 이미지 스토리지(Object Storage)는 OCI — 배치 근거·재검토 조건은 `docs/adr/0003-multi-cloud-component-placement.md`.\n\n## Git Workflow\n\n### Before starting\n`git branch --show-current`로 현재 브랜치를 확인하고 사용자에게 알릴 것. 예상 브랜치가 아니면 작업을 시작하지 말고 사용자에게 확인받을 것.\n\n### Committing & PRs\n- 작업(논리적 단위)이 끝나면 확인 없이 커밋·푸시까지 진행. 커밋 메시지·PR 본문도 사전 확인 불필요\n- PR 생성·머지도 확인 없이 진행 — 푸시 후 PR 생성, CI 통과 시 머지. 사용자가 머지를 지시하면 재확인 없이 즉시 머지\n- 커밋 메시지와 PR 본문은 한국어 + 개조식(불릿 위주 요약, 서술형 문장 지양)으로 작성.\n\n### Branching\n- 새 브랜치는 `git fetch` 후 `origin/main`에서 분기. 로컬 main pull 불필요(워크트리도 `origin/main`에서 분기)\n- 공유 체크아웃의 사용자 브랜치·미커밋 변경은 건드리지 않음\n\n### Next task\n- \"다음 할 일\"의 출처 = 팀 노션 스프린트 보드의 내 태스크 → 연결된 GitHub 이슈. 대화 기억·세션 요약 기반 추천 금지\n- 답할 때 출처(보드 태스크명 + 이슈 번호) 명시. 보드와 연결 안 된 열린 이슈는 \"보드 미연결\"로 따로 표시\n- `/task` 후보 출처도 동일(보드 \"내 할 일\" 뷰 → 연결 이슈), 이슈 없는 보드 태스크도 시작 가능\n\n## Commands\n\n```bash\nSPRING_PROFILES_ACTIVE=dev,db,secrets \\\n  GOOGLE_APPLICATION_CREDENTIALS=\"$(pwd)/src/main/resources/firebase/meetjyou-firebase-adminsdk.json\" \\\n  ./gradlew bootRun                                      # run (dev profile, local)\n./gradlew test                                           # all tests\n./gradlew test --tests \"FullyQualifiedClass.methodName\"  # single test\n./gradlew dockerTest                                     # src/dockerTest (Testcontainers MySQL); needs a running Docker daemon\n```\n\n## Code Style\n\n- Kotlin coding conventions\n- No wildcard imports\n- Logs & comments in English (docs/commits in Korean)\n- Magic numbers / repeated strings → constants\n- Enforced in `./gradlew build`: detekt (`config/detekt/detekt.yml`, existing findings frozen in `baseline.xml` — don't add to it), Konsist `ConventionTest` (`!!`, `requireNotNull`, controller DTO `.of()`, service `SecurityUtil`, SQL that sets autocommit/isolation/read-only and stored routines — the driver caches session state via `useLocalSessionState`; bulk UPDATE/DELETE of the user table — it skips the auth-cache eviction listener), and a skipped-test guard (a skipped test fails the build)\n\n## Testing\n\n- New features ship with unit + integration tests\n- Run full `./gradlew test` before finalizing any change; report actual output\n- dockerTest lock-wait detection: poll `performance_schema.data_lock_waits`, never `information_schema.innodb_trx.trx_state` (cached view, misses real waits → flaky CI)\n\n## Verification Workflow\n\nNo hook-enforced gate — the vgate plugin was retired here on 2026-10-13. The Testing rules above and the `./gradlew build` checks are the gate.\n\n- Past gap records and project-side static checks: `docs/agent-process/` (history, no longer enforced by hooks)\n- Pre-plugin in-repo version: tag `agent-process-inrepo-final`\n\n## Conventions\n\n### Current user\nUse `CurrentUserProvider` (`common/util/CurrentUserProvider.kt`) — never call `SecurityUtil` directly in service methods.\n```kotlin\nval user = currentUserProvider.user   // throws UserNotFoundException if not found\nval uuid = currentUserProvider.uuid   // UUID only, no DB hit\n```\n\n### Null safety & exceptions\n`?: throw` Elvis operator exclusively. No `requireNotNull()`, no `if (x == null) throw`, no `!!`.\n```kotlin\nval party = partyRepository.findByUuid(uuid) ?: throw PartyNotFoundException(uuid)\n```\n\n### Error responses\n- HTTP status lives on `ErrorCode(status, message)`, one handler for every `BusinessException` — never pick a base class for its status. `ErrorCodeStatusTest` pins every code's status: a change edits that table on purpose\n- Rejected for the current state (full, closed, already member) → 409, malformed input → 400, outside dependency down → `ExternalServiceException` (503)\n\n### DTO conversion\nService returns DTOs via companion `.of()` factory. Controllers never call `.of()` themselves.\n```kotlin\nreturn GetPartyResponse.of(party)   // in service\n```\n\n### Method length\n30-line hard cap on function bodies. Extract private helpers (`validateXxx` / `buildXxx` / `resolveXxx`).\n\n### N+1 prevention\nNever call a repository inside a loop. Batch-load with `findAllByXxxIn(ids)`, then `groupBy` or `associateBy`.\n\n### Scope functions\nNo 3-chain scope functions (`.apply{}.also{}.let{}`). Use explicit statements instead.\nString concatenation via template literals only — no `+` operator.\n\n### @Transactional\nRead-only methods require `@Transactional(readOnly = true)`. Write methods use `@Transactional`.\n\n### Concurrency bugs\n- Rare races from unnatural request overlaps (e.g. lock-order deadlocks) → handle after the fact, don't restructure locking: full rollback + 409 `CONCURRENT_REQUEST_CONFLICT` (`PessimisticLockingFailureException` in `GlobalExceptionHandler`), Discord alert kept to measure frequency\n- Structural prevention (e.g. party-row-first lock) only once alerts show it actually happens — #199, accepted cases in `.claude/rules/party.md`\n- Lost unique-index races on paths without their own translation → `UniqueConstraintConflicts` (constraint name → `ErrorCode`, 409 + observation alert); an unmapped name stays a 500. A new unique index on such a path needs an entry\n- Not covered by this: races that corrupt data without an error (lost update, stale-snapshot overwrite) — a 409 never fires for them, so judge those separately\n\n### UUID columns\nEvery entity UUID column (business key or FK) carries `@JdbcTypeCode(Types.VARCHAR)`. H2 tests don't catch a missing one — the column type silently diverges from MySQL.\n\n## Architecture\n\n**Profiles:** `dev` (JWT bypass + debug logging) / `db,secrets,release` (production)\n\n**Packages:**\n```\ncom.dogGetDrunk.meetjyou/\n├── auth/               # JWT, social login (Kakao/Google/Apple), dev bypass filter\n├── user/               # accounts, profiles\n├── party/              # group parties + applications\n├── userparty/          # user↔party membership (role, member status)\n├── post/               # travel listings\n├── plan/               # trip planning + markers\n├── chat/               # WebSocket chat (room, message, participant, connection, event)\n├── notification/       # push notifications, transactional outbox\n├── notificationcenter/ # in-app notification center (notices, sent/received applications)\n├── notice/             # admin notices\n├── terms/              # terms versions + agreements\n├── version/            # app version check / forced update\n├── preference/         # user preferences + compatibility matching\n├── image/, cloud/      # OCI Object Storage PAR URLs (thumbnails are made client-side)\n├── config/             # Spring config + @ConfigurationProperties\n└── common/             # exceptions, utilities, idempotency\n```\n\n**Key patterns:**\n- Notification outbox — publish `NotificationEvent`, never send push directly; full pattern in `.claude/rules/notification.md`\n- Schema managed via Flyway (`db/migration/V*.sql`); `ddl-auto: none`\n- `SecurityConfig` defaults to `.anyRequest().authenticated()` — new endpoints are private unless whitelisted there (public paths: `.claude/rules/auth.md`). Admin actions use `@PreAuthorize(\"hasAuthority('ADMIN')\")`.\n\n### Module context\n- Module-only, non-obvious invariants (locking, state machines, delete order, open issues) live in `.claude/rules/<module>.md` with `paths:` frontmatter — loaded only when matching files are touched\n- Don't write what the code shows at a glance (class lists, endpoint lists); keep this file to cross-cutting rules\n- No registry of rule files here — rules are discovered by glob\n\n## Compact Instructions\n\nContext-compaction preserve/drop rules → global CLAUDE.md §9 (pure meta, not a project spec).\n\n## Pipeline guardrails\n\n- 파이프라인 공통 계약: 전역 `agent-pipeline guide` 및 설치본 `defaults/prompts/roles/`, 프로젝트 설정 `.pipeline/project.yaml`\n- 태스크 시작: `/task`, 또는 명령 없는 구현 요청 시 수정 전 1회 확인(\"태스크로 진행 / S 예외로 바로 수정\") — 이 확인이 스펙 카드 대체\n- 태스크 문서: `.pipeline/tasks/<YYYYMMDD-slug>/`, 승인 = intent PR 머지(M·L) 또는 `task/<키>` push(S) — 엔진이 base ref 반영 여부 검사\n- 운영 배포·운영/공유 DB migration·force push·보호 브랜치 직접 push·시크릿 접근 금지\n- 승인된 intent 수정 금지, 변경 필요 시 사람 결정 게이트\n- 구현 에이전트의 수용 테스트 수정 금지, 오류 의심 시 escalation\n- task/dispatch ID는 Orca live preamble 기준, 실패 포함 worker_done 정확히 1회\n- timeout/heartbeat 부재만으로 중복 워커 시작 금지, 종료 확인 후 재분배\n"}, {"path": ".claude/rules/notification.md", "content": "---\ndescription: Notification module rules — loaded only when working in notification/ package\npaths:\n  - \"**/notification/**\"\n---\n\n# Notification Module\n\nOutbox pattern:\n1. Business code publishes `NotificationEvent` (or `NoticeBroadcastEvent` / `TermsReconsentEvent`)\n2. `*EventHandler` (`@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`) writes a `notification_outbox` row — preference check + `dedupKey` dedup\n3. `NotificationDispatcher` (`@Scheduled`) claims rows and sends via FCM\n\n- **Never dispatch push notifications synchronously** inside a business transaction\n- Publisher must run inside a transaction — AFTER_COMMIT listeners silently drop events otherwise (ADR-0002)\n- Outbox row: `type`, `data_json`, `dedup_key`, `status` (`PENDING → SENDING → SENT`; transient failure → back to `PENDING` with backoff on `available_at`; retries exhausted or no deliverable token → `DEAD`; `FAILED` is unused), `attempts`, `delivered_token_ids`\n- **`available_at` has two meanings:** retry time for `PENDING`, lease expiry for `SENDING`. The claim query picks both once it has passed, so a row stranded by a crash mid-send is reclaimed after the lease (10 min) — there is no separate reaper\n- The claim sets the lease with the DB clock (`NOW()`) and counts the attempt up front; a row reclaimed at the attempt limit goes `DEAD` without sending (poison guard)\n- Delivery is tracked per token in `delivered_token_ids` (push_token ids): a retry skips tokens that already got it. A permanent failure on one token is left out of the row verdict; only `UNREGISTERED` deactivates the token (`INVALID_ARGUMENT` may be a payload problem)\n- Delivery is at-least-once: a crash mid-send can duplicate that row's pushes\n- **Accepted risk:** the outbox row is written after the originating tx commits, so a crash in that window loses the push. In-app views cover most types — rationale and revisit conditions in ADR-0002 (2026-10-11 update)\n- Firebase config in `src/main/resources/firebase/` (ignored by Claude)\n"}, {"path": ".claude/rules/terms.md", "content": "---\ndescription: Terms module rules — loaded only when working in terms/ package\npaths:\n  - \"**/terms/**\"\n---\n\n# Terms Module\n\n- Required-terms consent is enforced only at registration (`UserService.createUser` → `validateRequiredTermsAgreement`)\n- `TermsReconsentEvent` only sends a push (notification outbox) — it does not block API access for users who haven't re-consented\n- Active terms = `status = ACTIVE` **and** `effectiveAt <= now`\n- `user_terms` is an append-only consent history: each change inserts a row, the latest `id` per (user, terms type) is the current state. Never update rows in place or add a unique key over `action` — the same action must be insertable again (V41, #224). The H2 schema lacks Flyway constraints, so pin schema behavior in `src/dockerTest` (`UserTermsConsentHistoryDockerTest`)\n- **Open issue:** publishing sets the previous version INACTIVE immediately; if the new version's `effectiveAt` is in the future, that terms type disappears until then and required-consent validation silently skips it\n"}], "base_ref": "origin/main", "gate": [["./gradlew", "test", "build"]]}

Human-facing language: write PR titles and bodies, commit messages, task documents (intent, spec, decisions, tickets, AT cases, result) and any reply to a human in Korean.
---
id: T-20261103-chat-notice-post-perf-04
title: 관리자 공지·약관 재발송 API와 MySQL 경쟁 검증
tier: high
effort_override: null
depends_on: [T-20261103-chat-notice-post-perf-03]
requirements: [REQ-005]
acceptance_tests: [AT-005-1, AT-005-2, AT-005-3, AT-005-4]
touches:
  - 'src/main/java/com/dogGetDrunk/meetjyou/notice/'
  - 'src/main/java/com/dogGetDrunk/meetjyou/terms/'
  - 'src/main/java/com/dogGetDrunk/meetjyou/notification/'
  - 'src/main/java/com/dogGetDrunk/meetjyou/common/exception/'
  - 'src/test/java/com/dogGetDrunk/meetjyou/common/'
  - 'src/test/java/com/dogGetDrunk/meetjyou/notice/NoticeBroadcastApiIntegrationTest.kt'
  - 'src/test/java/com/dogGetDrunk/meetjyou/terms/TermsReconsentBroadcastApiIntegrationTest.kt'
  - 'src/dockerTest/java/com/dogGetDrunk/meetjyou/notification/BroadcastConcurrencyDockerTest.kt'
consumes:
  - 'T-20261103-chat-notice-post-perf-03의 NoticeBroadcaster.broadcast(noticeUuid: UUID, critical: Boolean): Int'
  - 'T-20261103-chat-notice-post-perf-03의 TermsReconsentBroadcaster.broadcast(termsUuid: UUID): Int'
  - 'T-03 원천 잠금·prefix 검사·BROADCAST_ALREADY_SENT(409)'
  - '기존 NOTICE_NOT_FOUND·TERMS_NOT_FOUND·CONCURRENT_REQUEST_CONFLICT'
  - 'SqlPausePoint·performance_schema.data_lock_waits'
produces:
  - 'POST /api/v1/notices/{uuid}/broadcast → BroadcastResultResponse'
  - 'POST /api/v1/terms/{termsUuid}/reconsent-broadcast → BroadcastResultResponse'
  - 'ResendNoticeBroadcastRequest(val critical: Boolean = false)'
  - 'BroadcastResultResponse(val recipientCount: Int), companion of(count: Int)'
  - 'TermsReconsentBroadcaster.broadcast(termsUuid: UUID, validateApplicability: Boolean = false): Int'
  - 'TERMS_RECONSENT_NOT_APPLICABLE(409)'
status: ready
---

## 목표

원천 row의 기존 전량 발송 트랜잭션을 재사용하는 관리자 복구 API 제공, 두 경로의 동시 요청에서 중복 outbox 차단.

## 분류·결합도·DAG

- high: 관리자 권한·공유 DTO/API·원천 row 잠금·멱등성·경쟁 조건(상 조건 다수)
- T-03과 동일 모듈·발송 메서드·오류 코드 결합, T-03 이후에만 실행; T-01·T-02에는 비의존

## 구현 범위

- 포함: NoticeController 기존 /notices 아래 POST /{uuid}/broadcast, body의 critical 기본 false; TermsController 기존 /terms 아래 POST /{termsUuid}/reconsent-broadcast
- 두 endpoint @PreAuthorize("hasAuthority('ADMIN')"), 기존 API prefix /api/v1 유지; 서비스가 BroadcastResultResponse.of(count) 반환, controller .of() 호출 금지
- API 조정 서비스 바깥 트랜잭션 없음 → T-03 발송 빈 프록시 호출, 여기서 HTTP 200 recipientCount 반환
- NoticeBroadcaster는 T-03 시그니처 그대로 재사용; TermsReconsentBroadcaster는 validateApplicability=false 기본 인자만 확장, 원 핸들러 호출은 false 유지, 재발송은 true
- true일 때 원천 terms row 잠금 후 ACTIVE·effectiveAt<=now·동일 type의 INACTIVE 존재를 같은 tx 안에서 검사; 부적격 TERMS_RECONSENT_NOT_APPLICABLE(409), 기존 미래 ACTIVE 원 발송 동작 불변
- 기발송 409 BROADCAST_ALREADY_SENT, 원천 미존재 기존 404, 비관리자 403; ErrorCodeStatusTest에 새 코드 표 반영
- T-03의 잠금→dedup 검사→수신자 계산→outbox 저장 공통 경로 재사용; API 앞단의 비잠금 검사만으로 판정하거나 별도 outbox 저장 루프 작성 금지
- 재발송 수신자 = 호출 시점의 NORMAL/notified/해당 타입 preference 규칙, 가입·설정 변경 반영
- dockerTest: 공지/약관 × 재발송/재발송·원 발송/재발송 = 4개; 원 발송은 실제 발행 API AFTER_COMMIT, SqlPausePoint로 잠금 취득 후 insert 직전 정지, data_lock_waits 폴링으로 실제 대기 확인
- 제외: failed original 뒤 대기 재발송 복구의 추가 경쟁 시나리오(C-r1-06 기각), 자동 재시도·푸시 직접 전송·원 발행 정책 수정·운영 부하 테스트

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/notice/`·`src/main/java/com/dogGetDrunk/meetjyou/terms/` 기존 관리자 endpoint 권한·404 패턴
- T-03 발송 빈과 동일 원천 row 잠금·outbox tx 사용, 별도 동시성 제어 도입 금지
- `.claude/rules/party.md`·`user.md`의 MySQL lock-wait 폴링 선례, SqlPausePoint 테스트 helper

## 참조 파일 (읽기 허용)

- `AGENTS.md`
- `CLAUDE.md`
- `.pipeline/README.md`
- `.pipeline/project.yaml`
- `src/test/resources/` — 기존 test profile 설정만 읽기, 시크릿 접근·설정 변경 금지
- `src/main/java/com/dogGetDrunk/meetjyou/notice/` — T-03 NoticeBroadcaster, NoticeController·API service·DTO, Notice/Repository
- `src/main/java/com/dogGetDrunk/meetjyou/terms/` — T-03 TermsReconsentBroadcaster, TermsController·service·DTO, Terms/Repository·status/type/effectiveAt
- `src/main/java/com/dogGetDrunk/meetjyou/notification/` — T-03 원 핸들러·outbox/Repository·preference, BroadcastResultResponse 배치 위치는 기존 DTO 구조 준수
- `src/main/java/com/dogGetDrunk/meetjyou/user/` — NORMAL/notified·재발송 후보 fixture
- `src/main/java/com/dogGetDrunk/meetjyou/common/` — ErrorCode/BusinessException·GlobalExceptionHandler 및 관련 파일만
- `src/main/java/com/dogGetDrunk/meetjyou/config/SecurityConfig.kt` — ADMIN/GET permitAll 경계 확인만, whitelist 확대 금지
- `src/test/java/com/dogGetDrunk/meetjyou/` — ErrorCodeStatusTest 및 MockMvc 인증 fixture만, 오류 표는 touches의 common 테스트 모듈 안에서만 변경, 범위 밖이면 escalation
- `src/dockerTest/` — SqlPausePoint·data_lock_waits helper와 기존 Testcontainers 설정만, fixture 직접 의존만
- `.claude/rules/auth.md` — ADMIN·사용자 active 인증 불변식
- `.claude/rules/terms.md` — 현재 유효 약관 조건·원 발송 경계
- `.claude/rules/notification.md` — outbox 단일 tx·발행 이벤트 경계

## 확정 인터페이스 (spec 발췌, 테스트·구현 공통 계약)

#### 발송 빈·핸들러·예외 (테스트가 의존하는 계약, plan 단계 확정)

```kotlin
// package com.dogGetDrunk.meetjyou.notification.broadcast
@Service
class NoticeBroadcaster(
    private val noticeRepository: NoticeRepository,
    private val userRepository: UserRepository,
    private val preferenceRepository: UserNotificationPreferenceRepository,
    private val outboxRepository: NotificationOutboxRepository,
    private val templateFactory: NotificationTemplateFactory,
    private val objectMapper: ObjectMapper,
) {
    // 순서: noticeRepository.findByUuidForUpdate(uuid) ?: throw NoticeNotFoundException
    //   → outboxRepository.existsByDedupKeyStartingWith("notice:$uuid:") 이면 throw BroadcastAlreadySentException
    //   → userRepository.findAllByStatus(NORMAL) → critical=false면 preferenceRepository.findDisabledUserIdsByType(NOTICE) 1회
    //   → outboxRepository.saveAll(rows) → rows.size 반환
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun broadcast(noticeUuid: UUID, critical: Boolean): Int
}

@Service
class TermsReconsentBroadcaster(
    private val termsRepository: TermsRepository,
    private val userTermsRepository: UserTermsRepository,
    private val preferenceRepository: UserNotificationPreferenceRepository,
    private val outboxRepository: NotificationOutboxRepository,
    private val templateFactory: NotificationTemplateFactory,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),   // 선례 AppleClientSecretFactory, 단위 테스트는 고정 Clock 주입
) {
    // 공통: termsRepository.findByUuidForUpdate(uuid) ?: throw TermsNotFoundException
    //   → existsByDedupKeyStartingWith("terms_reconsent:$uuid:") 이면 throw BroadcastAlreadySentException
    //   → userTermsRepository.findLatestByTermsType(terms.type, AGREED) → NORMAL 필터 → findDisabledUserIdsByType(TERMS_RECONSENT) 1회
    //   → saveAll → rows.size 반환. title·body의 displayText는 잠근 Terms row에서 읽음
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun broadcastOnPublish(termsUuid: UUID): Int          // T-03, 적격성 검사 없음(현행 원 발송 의미)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun resend(termsUuid: UUID): Int                      // T-04, 잠금 직후 적격성: ACTIVE && effectiveAt <= clock.instant()
                                                          //   && termsRepository.existsByTypeAndStatus(type, INACTIVE), 아니면 throw TermsReconsentNotApplicableException
}

// Repository 추가 (잠금 = @Lock(LockModeType.PESSIMISTIC_WRITE) + @Query)
NoticeRepository.findByUuidForUpdate(uuid: UUID): Notice?
TermsRepository.findByUuidForUpdate(uuid: UUID): Terms?
TermsRepository.existsByTypeAndStatus(type: TermsType, status: TermsStatus): Boolean      // T-04
UserNotificationPreferenceRepository.findDisabledUserIdsByType(type: NotificationType): Set<Long>
NotificationOutboxRepository.existsByDedupKeyStartingWith(prefix: String): Boolean

// 예외
// common/exception/business/notification/BroadcastAlreadySentException.kt   (T-03)
class BroadcastAlreadySentException(dedupPrefix: String) : BusinessException(ErrorCode.BROADCAST_ALREADY_SENT, dedupPrefix)
// common/exception/business/terms/TermsReconsentNotApplicableException.kt     (T-04)
class TermsReconsentNotApplicableException(termsUuid: UUID) : BusinessException(ErrorCode.TERMS_RECONSENT_NOT_APPLICABLE, termsUuid.toString())

// 핸들러 (기존 클래스·패키지 유지, 생성자 교체, 메서드에 @Transactional 없음)
class NoticeBroadcastEventHandler(private val noticeBroadcaster: NoticeBroadcaster, private val discordAlertService: DiscordAlertService)
class TermsReconsentEventHandler(private val termsReconsentBroadcaster: TermsReconsentBroadcaster, private val discordAlertService: DiscordAlertService)
// on(event): try { broadcaster 호출 }
//   catch BroadcastAlreadySentException / NoticeNotFoundException(TermsNotFoundException) → INFO 로그, 알림 없음
//   catch Exception → ERROR 로그 + discordAlertService.sendOperationalAlert(
//       source = "NoticeBroadcast:<uuid>" | "TermsReconsentBroadcast:<uuid>",
//       exceptionClass = e.javaClass.simpleName,
//       summary 에 "0 sent" 와 "POST /api/v1/notices/<uuid>/broadcast" | "POST /api/v1/terms/<uuid>/reconsent-broadcast" 포함)

// 재발송 서비스 (T-04) — @Transactional 없음: 바깥 트랜잭션이 있으면 REQUIRES_NEW 발송이 커넥션 2개를 잡음
NoticeService.resendBroadcast(uuid: UUID, request: ResendNoticeBroadcastRequest): BroadcastResultResponse
TermsService.resendReconsentBroadcast(termsUuid: UUID): BroadcastResultResponse
// BroadcastResultResponse 위치: notification/broadcast/dto/
```

#### 테스트 지원 계약

- 실패 주입·SQL 계수 = 각 테스트 파일 안에 정의한 Hibernate `StatementInspector` 구현 (top-level class + companion `ThreadLocal`·카운터, 선례 dockerTest `SqlPausePoint`)
  - 등록: `@SpringBootTest(properties = ["spring.jpa.properties.hibernate.session_factory.statement_inspector=<FQCN>"])`, `src/test/resources` 변경 없음
  - outbox 실패 주입: 무장한 스레드에서 N번째(기본 2번째) `insert into notification_outbox` 문에서 `RuntimeException` → 첫 row는 실제 insert된 뒤 실패, 구현의 save/saveAll 선택과 무관
- 커밋 시점 예외(`UnexpectedRollbackException` 등) 격리는 핸들러 단위 테스트로 고정 (발송 빈 mock이 해당 예외 throw → 핸들러 비전파 + 알림 1회) — 통합 테스트의 beforeCommit 주입 변형은 두지 않음
- 시간 경계(`effectiveAt == now`)는 `TermsReconsentBroadcaster` 단위 테스트에서 고정 `Clock`으로, 통합 테스트는 now±1h만

## 관련 spec 발췌

- REQ-005 API 계약: 공지 body {"critical": false} 기본값, 약관 body 없음; 성공 200 recipientCount; 기발송 409; API별 404/403
- 약관 재발송은 ACTIVE && effectiveAt<=now && 같은 type INACTIVE 존재, 잠금 후 판정; effectiveAt=now 허용(C-r1-01)
- 수신자는 재발송 시점 재계산, 정상 title/body/dataJson/dedupKey 유지
- AT-005-3·4는 각 공지·약관 모두 실행; 선행 lock 취득+insert 직전 pause → 경쟁 lock wait 관측 → release → outbox 전원 1세트·중복0, 나머지 409(C-r1-06)
- 원 발송 중 재발송은 원 발행 API 성공 + 재발송 지정409, 원 핸들러 직접 호출 불허

## 실행 순서

- [ ] T-03 완료·증거 확인 후 AT-005 보호 파일 복사, endpoint/부적격·경쟁 RED 확인
- [ ] 관리자 요청·응답 DTO와 비트랜잭션 service/controller 조정, 기존 발송 빈 재사용
- [ ] terms 발송 메서드 기본 false 적격성 flag 확장·재발송 true·잠금 안 판정·오류 표 매핑
- [ ] 두 API 성공·중복·404·403·critical·유효시점·재계산 회귀 확인
- [ ] MySQL 4개 경쟁에서 실제 lock wait와 outbox 유일성 확인, executor/latch·pause finally 해제
- [ ] 전체 test/build → dockerTest, 동일 HEAD CI 증거와 함께 보고

## 실행·보호·완료 조건

- base_ref: `origin/main`; 이 카드의 범위만 변경, 승인된 intent 수정 금지
- test-writer가 선작성한 보호 파일을 태스크 `tests/code/<저장소 상대 경로>`에서 엔진 지시대로 복사; 보호 파일 수정 금지, 오류 의심 시 근거와 함께 escalation
- 구현 전 해당 AT 실패(RED), 구현 후 통과(GREEN) 확인; 애플리케이션 코드·테스트 코드 작성은 별도 역할
- 필수 gate: `./gradlew test build`; 추가 필수 증거: `./gradlew dockerTest`
- 로컬 Docker 미가동 시 dockerTest 미실행을 통과로 보고하지 않음; `.github/workflows/ci.yml`의 `./gradlew build dockerTest`가 동일한 불변 HEAD에서 통과한 증거로만 대체, 머지는 CI 통과 후
- 최종 HEAD·실행 명령·exit code·테스트 개수·실패/미실행 사유 기록; 코드 리뷰 의견으로 gate 결과 대체 금지
- 운영 배포·운영/공유 DB 접근·시크릿 접근 금지; 로컬 Testcontainers의 Flyway 적용만 허용
- 주석·로그 영어, 서비스 DTO `.of()`, CurrentUserProvider, `?: throw`, 명시적 트랜잭션, 반복문 Repository 조회 금지 등 CLAUDE.md 준수; 기존 IDENTITY outbox 건별 저장은 승인 spec 범위로 유지

## 입력·경로 한계

- 이 계획은 지정된 문서만으로 작성; 기존 애플리케이션 소스·테스트·CI 파일 미열람
- 아래 소스 디렉터리는 CLAUDE.md의 패키지 지도에 따른 모듈 읽기 범위; 세부 하위 파일 경로·기존 메서드의 전체 시그니처는 확인하지 않은 상태
- 다음 워커의 탐색 범위는 touches 및 참조 파일에 명시된 디렉터리 안의 열거 클래스·직접 의존 파일로 한정, 새 테스트 경로는 이 카드에서 지정
- 열거 클래스·직접 의존이 허용 범위에 없으면 추정 경로로 확장하지 않고 메인 설계자에게 누락 경로 escalation; 시크릿·자격증명 파일은 어떤 디렉터리 안에서도 제외

# PR link
- Include this exact line in the PR body: `Refs dogGetDrunk/meetjyou-backend#225`
# Acceptance test files (protected, engine-checked)
- Copy with the commands below and commit as-is; never modify the content (sha256 byte equality is checked before gate)
- If you suspect an error, do not fix it; report it in the result
```sh
mkdir -p "$(dirname 'src/dockerTest/java/com/dogGetDrunk/meetjyou/notification/BroadcastConcurrencyDockerTest.kt')" && cp '/Users/serendipity/orca/workspaces/meetjyou-backend/20261103-chat-notice-post-perf/.pipeline/tasks/20261103-chat-notice-post-perf/tests/code/src/dockerTest/java/com/dogGetDrunk/meetjyou/notification/BroadcastConcurrencyDockerTest.kt' 'src/dockerTest/java/com/dogGetDrunk/meetjyou/notification/BroadcastConcurrencyDockerTest.kt'
mkdir -p "$(dirname 'src/test/java/com/dogGetDrunk/meetjyou/notice/NoticeBroadcastApiIntegrationTest.kt')" && cp '/Users/serendipity/orca/workspaces/meetjyou-backend/20261103-chat-notice-post-perf/.pipeline/tasks/20261103-chat-notice-post-perf/tests/code/src/test/java/com/dogGetDrunk/meetjyou/notice/NoticeBroadcastApiIntegrationTest.kt' 'src/test/java/com/dogGetDrunk/meetjyou/notice/NoticeBroadcastApiIntegrationTest.kt'
mkdir -p "$(dirname 'src/test/java/com/dogGetDrunk/meetjyou/terms/TermsReconsentBroadcastApiIntegrationTest.kt')" && cp '/Users/serendipity/orca/workspaces/meetjyou-backend/20261103-chat-notice-post-perf/.pipeline/tasks/20261103-chat-notice-post-perf/tests/code/src/test/java/com/dogGetDrunk/meetjyou/terms/TermsReconsentBroadcastApiIntegrationTest.kt' 'src/test/java/com/dogGetDrunk/meetjyou/terms/TermsReconsentBroadcastApiIntegrationTest.kt'
```
- src/dockerTest/java/com/dogGetDrunk/meetjyou/notification/BroadcastConcurrencyDockerTest.kt: sha256 0de8c6674a1e1d240cbd6ab7a630c131197870543676638485a44df71de907ec
- src/test/java/com/dogGetDrunk/meetjyou/notice/NoticeBroadcastApiIntegrationTest.kt: sha256 62310d13bd88589443d7008d405e960800699e1d3d83961a71ef25aef1f8e477
- src/test/java/com/dogGetDrunk/meetjyou/terms/TermsReconsentBroadcastApiIntegrationTest.kt: sha256 2bd31ebebfd4a86bbb3da8933056717e76ce2f1ca8b62bc2adc189ada1aec9f9
# Machine report (engine-read)
Before worker_done write JSON (UTF-8, no BOM) to `.pipeline/logs/runtime/reports/T-20261103-chat-notice-post-perf-04/implementer.json` under the worktree root, replacing any earlier file; it is git-excluded, never commit it:
```json
{
  "schema": "implement/v1",
  "ticket": "T-20261103-chat-notice-post-perf-04",
  "dispatch_id": "<your dispatch id>",
  "outcome": "succeeded|failed",
  "cause": "implementation|protected_test|spec|instruction|environment",
  "evidence": "paths and the exact error"
}
```
- cause: implementation = your code; protected_test = a protected acceptance test is wrong (lint, compile, fixture, assertion); spec = the ticket or spec contradicts itself or lacks information; instruction = these instructions contradict each other or the project rules; environment = build tools, dependencies, network or services
- Any cause but implementation stops the retries and hands the ticket to the designer: name the files and quote the error in evidence
- outcome succeeded: cause implementation