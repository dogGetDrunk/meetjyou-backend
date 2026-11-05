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
{"project_rules": [{"path": "AGENTS.md", "content": "# AGENTS.md\n\n- 대상: 이 저장소에서 작업하는 Codex 및 AGENTS.md를 읽는 에이전트\n- 작업 시작 전 [CLAUDE.md](CLAUDE.md) 전체 필수 확인·준수 — 프로젝트 개요, Git 흐름, 코딩 컨벤션, 검증, 아키텍처, 파이프라인 안전 규칙의 단일 원본\n- 모듈별 상세 규칙: `.claude/rules/*.md`의 `paths:`와 변경 대상 경로를 대조해 해당 문서 필수 확인 — Codex의 자동 로딩 가정 금지\n- 프로젝트 파이프라인 사용법·설정: [.pipeline/README.md](.pipeline/README.md), [.pipeline/project.yaml](.pipeline/project.yaml)\n- 공통 규칙 변경 위치: `CLAUDE.md`; 이 파일에 동일 규칙 복제 금지\n"}, {"path": "CLAUDE.md", "content": "# CLAUDE.md\n\nThis file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.\n\n## Project Overview\n\n**Meetjyou (만나쥬)** — 여행 동행 찾기 서비스. Kotlin + Spring Boot + MySQL 기반. 앱 서버(nginx·certbot·alloy 포함)는 AWS EC2 `t4g.small`(arm64), MySQL과 이미지 스토리지(Object Storage)는 OCI — 배치 근거·재검토 조건은 `docs/adr/0003-multi-cloud-component-placement.md`.\n\n## Git Workflow\n\n### Before starting\n`git branch --show-current`로 현재 브랜치를 확인하고 사용자에게 알릴 것. 예상 브랜치가 아니면 작업을 시작하지 말고 사용자에게 확인받을 것.\n\n### Committing & PRs\n- 작업(논리적 단위)이 끝나면 확인 없이 커밋·푸시까지 진행. 커밋 메시지·PR 본문도 사전 확인 불필요\n- PR 생성·머지도 확인 없이 진행 — 푸시 후 PR 생성, CI 통과 시 머지. 사용자가 머지를 지시하면 재확인 없이 즉시 머지\n- 커밋 메시지와 PR 본문은 한국어 + 개조식(불릿 위주 요약, 서술형 문장 지양)으로 작성.\n\n### Branching\n- 새 브랜치는 `git fetch` 후 `origin/main`에서 분기. 로컬 main pull 불필요(워크트리도 `origin/main`에서 분기)\n- 공유 체크아웃의 사용자 브랜치·미커밋 변경은 건드리지 않음\n\n### Next task\n- \"다음 할 일\"의 출처 = 팀 노션 스프린트 보드의 내 태스크 → 연결된 GitHub 이슈. 대화 기억·세션 요약 기반 추천 금지\n- 답할 때 출처(보드 태스크명 + 이슈 번호) 명시. 보드와 연결 안 된 열린 이슈는 \"보드 미연결\"로 따로 표시\n- `/task` 후보 출처도 동일(보드 \"내 할 일\" 뷰 → 연결 이슈), 이슈 없는 보드 태스크도 시작 가능\n\n## Commands\n\n```bash\nSPRING_PROFILES_ACTIVE=dev,db,secrets \\\n  GOOGLE_APPLICATION_CREDENTIALS=\"$(pwd)/src/main/resources/firebase/meetjyou-firebase-adminsdk.json\" \\\n  ./gradlew bootRun                                      # run (dev profile, local)\n./gradlew test                                           # all tests\n./gradlew test --tests \"FullyQualifiedClass.methodName\"  # single test\n./gradlew dockerTest                                     # src/dockerTest (Testcontainers MySQL); needs a running Docker daemon\n```\n\n## Code Style\n\n- Kotlin coding conventions\n- No wildcard imports\n- Logs & comments in English (docs/commits in Korean)\n- Magic numbers / repeated strings → constants\n- Enforced in `./gradlew build`: detekt (`config/detekt/detekt.yml`, existing findings frozen in `baseline.xml` — don't add to it), Konsist `ConventionTest` (`!!`, `requireNotNull`, controller DTO `.of()`, service `SecurityUtil`, SQL that sets autocommit/isolation/read-only and stored routines — the driver caches session state via `useLocalSessionState`; bulk UPDATE/DELETE of the user table — it skips the auth-cache eviction listener), and a skipped-test guard (a skipped test fails the build)\n\n## Testing\n\n- New features ship with unit + integration tests\n- Run full `./gradlew test` before finalizing any change; report actual output\n- dockerTest lock-wait detection: poll `performance_schema.data_lock_waits`, never `information_schema.innodb_trx.trx_state` (cached view, misses real waits → flaky CI)\n\n## Verification Workflow\n\nNo hook-enforced gate — the vgate plugin was retired here on 2026-10-13. The Testing rules above and the `./gradlew build` checks are the gate.\n\n- Past gap records and project-side static checks: `docs/agent-process/` (history, no longer enforced by hooks)\n- Pre-plugin in-repo version: tag `agent-process-inrepo-final`\n\n## Conventions\n\n### Current user\nUse `CurrentUserProvider` (`common/util/CurrentUserProvider.kt`) — never call `SecurityUtil` directly in service methods.\n```kotlin\nval user = currentUserProvider.user   // throws UserNotFoundException if not found\nval uuid = currentUserProvider.uuid   // UUID only, no DB hit\n```\n\n### Null safety & exceptions\n`?: throw` Elvis operator exclusively. No `requireNotNull()`, no `if (x == null) throw`, no `!!`.\n```kotlin\nval party = partyRepository.findByUuid(uuid) ?: throw PartyNotFoundException(uuid)\n```\n\n### Error responses\n- HTTP status lives on `ErrorCode(status, message)`, one handler for every `BusinessException` — never pick a base class for its status. `ErrorCodeStatusTest` pins every code's status: a change edits that table on purpose\n- Rejected for the current state (full, closed, already member) → 409, malformed input → 400, outside dependency down → `ExternalServiceException` (503)\n\n### DTO conversion\nService returns DTOs via companion `.of()` factory. Controllers never call `.of()` themselves.\n```kotlin\nreturn GetPartyResponse.of(party)   // in service\n```\n\n### Method length\n30-line hard cap on function bodies. Extract private helpers (`validateXxx` / `buildXxx` / `resolveXxx`).\n\n### N+1 prevention\nNever call a repository inside a loop. Batch-load with `findAllByXxxIn(ids)`, then `groupBy` or `associateBy`.\n\n### Scope functions\nNo 3-chain scope functions (`.apply{}.also{}.let{}`). Use explicit statements instead.\nString concatenation via template literals only — no `+` operator.\n\n### @Transactional\nRead-only methods require `@Transactional(readOnly = true)`. Write methods use `@Transactional`.\n\n### Concurrency bugs\n- Rare races from unnatural request overlaps (e.g. lock-order deadlocks) → handle after the fact, don't restructure locking: full rollback + 409 `CONCURRENT_REQUEST_CONFLICT` (`PessimisticLockingFailureException` in `GlobalExceptionHandler`), Discord alert kept to measure frequency\n- Structural prevention (e.g. party-row-first lock) only once alerts show it actually happens — #199, accepted cases in `.claude/rules/party.md`\n- Lost unique-index races on paths without their own translation → `UniqueConstraintConflicts` (constraint name → `ErrorCode`, 409 + observation alert); an unmapped name stays a 500. A new unique index on such a path needs an entry\n- Not covered by this: races that corrupt data without an error (lost update, stale-snapshot overwrite) — a 409 never fires for them, so judge those separately\n\n### UUID columns\nEvery entity UUID column (business key or FK) carries `@JdbcTypeCode(Types.VARCHAR)`. H2 tests don't catch a missing one — the column type silently diverges from MySQL.\n\n## Architecture\n\n**Profiles:** `dev` (JWT bypass + debug logging) / `db,secrets,release` (production)\n\n**Packages:**\n```\ncom.dogGetDrunk.meetjyou/\n├── auth/               # JWT, social login (Kakao/Google/Apple), dev bypass filter\n├── user/               # accounts, profiles\n├── party/              # group parties + applications\n├── userparty/          # user↔party membership (role, member status)\n├── post/               # travel listings\n├── plan/               # trip planning + markers\n├── chat/               # WebSocket chat (room, message, participant, connection, event)\n├── notification/       # push notifications, transactional outbox\n├── notificationcenter/ # in-app notification center (notices, sent/received applications)\n├── notice/             # admin notices\n├── terms/              # terms versions + agreements\n├── version/            # app version check / forced update\n├── preference/         # user preferences + compatibility matching\n├── image/, cloud/      # OCI Object Storage PAR URLs (thumbnails are made client-side)\n├── config/             # Spring config + @ConfigurationProperties\n└── common/             # exceptions, utilities, idempotency\n```\n\n**Key patterns:**\n- Notification outbox — publish `NotificationEvent`, never send push directly; full pattern in `.claude/rules/notification.md`\n- Schema managed via Flyway (`db/migration/V*.sql`); `ddl-auto: none`\n- `SecurityConfig` defaults to `.anyRequest().authenticated()` — new endpoints are private unless whitelisted there (public paths: `.claude/rules/auth.md`). Admin actions use `@PreAuthorize(\"hasAuthority('ADMIN')\")`.\n\n### Module context\n- Module-only, non-obvious invariants (locking, state machines, delete order, open issues) live in `.claude/rules/<module>.md` with `paths:` frontmatter — loaded only when matching files are touched\n- Don't write what the code shows at a glance (class lists, endpoint lists); keep this file to cross-cutting rules\n- No registry of rule files here — rules are discovered by glob\n\n## Compact Instructions\n\nContext-compaction preserve/drop rules → global CLAUDE.md §9 (pure meta, not a project spec).\n\n## Pipeline guardrails\n\n- 파이프라인 공통 계약: 전역 `agent-pipeline guide` 및 설치본 `defaults/prompts/roles/`, 프로젝트 설정 `.pipeline/project.yaml`\n- 태스크 시작: `/task`, 또는 명령 없는 구현 요청 시 수정 전 1회 확인(\"태스크로 진행 / S 예외로 바로 수정\") — 이 확인이 스펙 카드 대체\n- 태스크 문서: `.pipeline/tasks/<YYYYMMDD-slug>/`, 승인 = intent PR 머지(M·L) 또는 `task/<키>` push(S) — 엔진이 base ref 반영 여부 검사\n- 운영 배포·운영/공유 DB migration·force push·보호 브랜치 직접 push·시크릿 접근 금지\n- 승인된 intent 수정 금지, 변경 필요 시 사람 결정 게이트\n- 구현 에이전트의 수용 테스트 수정 금지, 오류 의심 시 escalation\n- task/dispatch ID는 Orca live preamble 기준, 실패 포함 worker_done 정확히 1회\n- timeout/heartbeat 부재만으로 중복 워커 시작 금지, 종료 확인 후 재분배\n"}, {"path": ".claude/rules/post-plan.md", "content": "---\ndescription: Post / plan / idempotency rules — loaded only when working in post/, plan/ or common/idempotency/\npaths:\n  - \"**/post/**\"\n  - \"**/plan/**\"\n  - \"**/common/idempotency/**\"\n---\n\n# Post & Plan Modules\n\n## Idempotent create (`Idempotency-Key` header)\nOpting a new create endpoint in:\n1. Add a value to `IdempotencyScope`; accept an optional `Idempotency-Key` header\n2. Service: `resolveExisting` before creating, `record` after (same transaction)\n3. Controller: catch `DataIntegrityViolationException` → `resolveAfterConflict` (see `PostController`)\n\n- `resolveAfterConflict` re-checks the request hash — never replace it with a plain lookup by key (would return the winner's resource to a different body)\n- `IdempotencyKeyService.record` stays non-`@Transactional` and never catches the unique violation (session becomes rollback-only)\n- The unique constraint lives in both the JPA annotation and Flyway — tests build the H2 schema from annotations\n- Hash = SHA-256 of the serialized request DTO → changing DTO fields changes the hash. Same key + different body → 409\n- Known limit: key rows are never expired or cleaned up\n\n## Post ↔ party ↔ plan\n- Creating a post also creates its Party and ChatRoom; deleting a post does not delete the party\n- A post may only attach a plan owned by its author (`resolvePlanReference`) — otherwise `isPlanPublic=true` exposes someone else's plan. Plan changes sync to `party.plan`\n- Posts of a COMPLETED party are read-only. `updatePost` and `updatePostStatus` take the party row lock (`PartyService.lockPartyOfPost`) before loading the post, then the post row (`PostRepository.lockByUuid`) — `completeParty` writes post `status` under that lock, and `updatePost` writes party `capacity`/itinerary/`destination` (see party rules)\n- `updatePost` syncs `capacity`/`itinStart`/`itinFinish`/`location` to the party (`Party.syncFromPost`) and rejects `capacity < party.joined` with 409 `PARTY_CAPACITY_BELOW_JOINED` before any plan/field/compPreference change. `updatePostStatus` syncs party `recruitmentStatus` (RECRUITMENT_COMPLETED → CLOSED, RECRUITING → OPEN)\n- Plan read access goes through `PlanAccessGuard.validateReadAccess` (owner / public post / JOINED member)\n- No ON DELETE CASCADE on plan FKs — deleting a plan detaches `party.plan`, `post.plan`, `post.isPlanPublic` first\n- That detach is a bulk `UPDATE ... WHERE plan = ?` (`detachPlan`), party before post — never load-and-null the rows: a snapshot read would overwrite a link switched to another plan meanwhile (#201), and party-first matches `updatePost`'s party-row-first lock order (no deadlock while the edit keeps its plan; a plan switch is an accepted deadlock — see party rules)\n"}], "base_ref": "origin/main", "gate": [["./gradlew", "test", "build"]]}

Human-facing language: write PR titles and bodies, commit messages, task documents (intent, spec, decisions, tickets, AT cases, result) and any reply to a human in Korean.
---
id: T-20261105-invalid-input-errors-01
title: 마커 요청 Bean Validation과 기존 마커 보존 검증
tier: high
effort_override: null
depends_on: []
requirements: [REQ-001]
acceptance_tests: [AT-001-1, AT-001-2]
touches:
  - src/main/java/com/dogGetDrunk/meetjyou/plan/dto/CreateMarkerRequest.kt
  - src/main/java/com/dogGetDrunk/meetjyou/plan/dto/CreatePlanRequest.kt
  - src/main/java/com/dogGetDrunk/meetjyou/plan/dto/ReplaceMarkersRequest.kt
  - src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerRequestValidationTest.kt
  - src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerInputValidationIntegrationTest.kt
consumes:
  - CreatePlanRequest.markers
  - ReplaceMarkersRequest.markers
  - CreateMarkerRequest
  - jakarta.validation.Validator
produces:
  - 마커 입력 위반에 대한 400 INVALID_INPUT_VALUE
  - 정상·경계·빈 리스트의 기존 API 계약 유지
status: pending
---

## 목표

- 마커 요청의 중첩 DTO 검증과 개수·필드 제약 추가, 잘못된 생성·교체의 무변경 처리

## 구현 범위

- CreatePlanRequest.markers·ReplaceMarkersRequest.markers에 `@field:Valid`·`@field:Size(max = 200)`
- CreateMarkerRequest: place `@NotBlank`·100자, nullable memo 500자, lat −90~90·lng −180~180(경계 포함), dayNum >= 1·idx >= 0
- 반복 제한값 companion 상수화; 서비스·엔티티·스키마 변경 없음
- 생성 실패는 플랜·마커 건수 변화 0, 교체 실패는 기존 마커 uuid·내용·개수 보존을 요청 후 독립 조회로 확인
- centerLat/centerLng·UpdatePlanRequest 등 마커 밖 검증 제외

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/plan/dto/CreatePlanRequest.kt` — 기존 `@field:NotBlank`·`@field:Size` 표기
- `src/main/java/com/dogGetDrunk/meetjyou/plan/MarkerController.kt` — `replaceMarkers`의 `@Valid @RequestBody`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/GlobalExceptionHandler.kt` — `handleMethodArgumentNotValid` → 400 `INVALID_INPUT_VALUE`
- `src/test/java/com/dogGetDrunk/meetjyou/post/PostIdempotencyIntegrationTest.kt` — RANDOM_PORT 통합 테스트·인증 헤더 구성

## 참조 파일 (읽기 허용)

- `.pipeline/tasks/20261105-invalid-input-errors/spec.md`
- `CLAUDE.md`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/PlanController.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/MarkerController.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/MarkerService.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/Marker.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/Plan.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/MarkerRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/PlanRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/dto/MarkerResponse.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/plan/dto/CreatePlanResponse.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/GlobalExceptionHandler.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/ErrorResponse.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/ErrorCode.kt`
- `src/main/resources/db/migration/V1__initial_schema.sql`
- `src/test/java/com/dogGetDrunk/meetjyou/plan/support/PlanFixtures.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/user/support/UserFixtures.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/post/PostIdempotencyIntegrationTest.kt`
- `src/test/resources/application-test.yml`

## 관련 spec 발췌

- REQ-001: 두 리스트 최대 200개·중첩 검증, place 100자·memo 500자, 좌표 범위·dayNum/idx 제약
- 201개·길이 초과·공백 place·범위 초과·dayNum 0·idx −1은 생성/교체 모두 400 INVALID_INPUT_VALUE
- 경계값은 생성 201·교체 200, 빈 리스트·memo null 허용

## 분류 근거

- 공유 요청 DTO/API 제약 변경으로 blast radius high; 다른 기준의 단순성보다 high 조건 우선
- effort_override 없음; max 사용 없음

## 실행 계획·DAG

- `T-20261105-invalid-input-errors-01 ∥ T-20261105-invalid-input-errors-02 ∥ T-20261105-invalid-input-errors-03`, 선행 티켓 없음
- 01은 plan DTO 3개, 02는 멱등 서비스·post/plan 서비스·컨트롤러, 03은 `AppleJwtDecoder`만 수정 — touches 교집합 없음

## 검증·증거 담당

- AT-001-1: `./gradlew test --tests "*MarkerRequestValidationTest"`
- AT-001-2: `./gradlew test --tests "*MarkerInputValidationIntegrationTest"`
- test-writer: 위반 입력의 base assertion 실패 확인; 경계·빈 리스트·null memo는 동작 유지로 구분
- 구현 워커: 보호 테스트 불변, 수정 후 GREEN 및 intent의 수정 되돌림 RED 확인·복원, 최종 구현 SHA의 `./gradlew test build` 출력 제출
- 기존 엔진 로그 활용; 새 검증 절차 신설 없음; 위 명령은 실행 예정이며 이번 설계에서 미실행

# PR link
- Include this exact line in the PR body: `Refs dogGetDrunk/meetjyou-backend#226`
# Acceptance test files (protected, engine-checked)
- Copy with the commands below and commit as-is; never modify the content (sha256 byte equality is checked before gate)
- If you suspect an error, do not fix it; report it in the result
```sh
mkdir -p "$(dirname 'src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerInputValidationIntegrationTest.kt')" && cp '/Users/serendipity/orca/workspaces/meetjyou-backend/20261105-invalid-input-errors/.pipeline/tasks/20261105-invalid-input-errors/tests/code/src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerInputValidationIntegrationTest.kt' 'src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerInputValidationIntegrationTest.kt'
mkdir -p "$(dirname 'src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerRequestValidationTest.kt')" && cp '/Users/serendipity/orca/workspaces/meetjyou-backend/20261105-invalid-input-errors/.pipeline/tasks/20261105-invalid-input-errors/tests/code/src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerRequestValidationTest.kt' 'src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerRequestValidationTest.kt'
```
- src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerInputValidationIntegrationTest.kt: sha256 57cb581ec57204bec04fc0118868919e7cc34dfe660fd5b8dcf47891f4f48d02
- src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerRequestValidationTest.kt: sha256 d89e503891a512b7617dc6b65fd5eb3b9d0cf3e097e6a943bcdd4a24615ea01e
# Machine report (engine-read)
Before worker_done write JSON (UTF-8, no BOM) to `.pipeline/logs/runtime/reports/T-20261105-invalid-input-errors-01/implementer.json` under the worktree root, replacing any earlier file; it is git-excluded, never commit it:
```json
{
  "schema": "implement/v1",
  "ticket": "T-20261105-invalid-input-errors-01",
  "dispatch_id": "<your dispatch id>",
  "outcome": "succeeded|failed",
  "cause": "implementation|protected_test|spec|instruction|environment",
  "evidence": "paths and the exact error"
}
```
- cause: implementation = your code; protected_test = a protected acceptance test is wrong (lint, compile, fixture, assertion); spec = the ticket or spec contradicts itself or lacks information; instruction = these instructions contradict each other or the project rules; environment = build tools, dependencies, network or services
- Any cause but implementation stops the retries and hands the ticket to the designer: name the files and quote the error in evidence
- outcome succeeded: cause implementation