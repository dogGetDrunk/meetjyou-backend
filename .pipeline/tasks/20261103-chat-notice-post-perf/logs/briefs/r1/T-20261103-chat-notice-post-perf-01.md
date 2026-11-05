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
{"project_rules": [{"path": "AGENTS.md", "content": "# AGENTS.md\n\n- 대상: 이 저장소에서 작업하는 Codex 및 AGENTS.md를 읽는 에이전트\n- 작업 시작 전 [CLAUDE.md](CLAUDE.md) 전체 필수 확인·준수 — 프로젝트 개요, Git 흐름, 코딩 컨벤션, 검증, 아키텍처, 파이프라인 안전 규칙의 단일 원본\n- 모듈별 상세 규칙: `.claude/rules/*.md`의 `paths:`와 변경 대상 경로를 대조해 해당 문서 필수 확인 — Codex의 자동 로딩 가정 금지\n- 프로젝트 파이프라인 사용법·설정: [.pipeline/README.md](.pipeline/README.md), [.pipeline/project.yaml](.pipeline/project.yaml)\n- 공통 규칙 변경 위치: `CLAUDE.md`; 이 파일에 동일 규칙 복제 금지\n"}, {"path": "CLAUDE.md", "content": "# CLAUDE.md\n\nThis file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.\n\n## Project Overview\n\n**Meetjyou (만나쥬)** — 여행 동행 찾기 서비스. Kotlin + Spring Boot + MySQL 기반. 앱 서버(nginx·certbot·alloy 포함)는 AWS EC2 `t4g.small`(arm64), MySQL과 이미지 스토리지(Object Storage)는 OCI — 배치 근거·재검토 조건은 `docs/adr/0003-multi-cloud-component-placement.md`.\n\n## Git Workflow\n\n### Before starting\n`git branch --show-current`로 현재 브랜치를 확인하고 사용자에게 알릴 것. 예상 브랜치가 아니면 작업을 시작하지 말고 사용자에게 확인받을 것.\n\n### Committing & PRs\n- 작업(논리적 단위)이 끝나면 확인 없이 커밋·푸시까지 진행. 커밋 메시지·PR 본문도 사전 확인 불필요\n- PR 생성·머지도 확인 없이 진행 — 푸시 후 PR 생성, CI 통과 시 머지. 사용자가 머지를 지시하면 재확인 없이 즉시 머지\n- 커밋 메시지와 PR 본문은 한국어 + 개조식(불릿 위주 요약, 서술형 문장 지양)으로 작성.\n\n### Branching\n- 새 브랜치는 `git fetch` 후 `origin/main`에서 분기. 로컬 main pull 불필요(워크트리도 `origin/main`에서 분기)\n- 공유 체크아웃의 사용자 브랜치·미커밋 변경은 건드리지 않음\n\n### Next task\n- \"다음 할 일\"의 출처 = 팀 노션 스프린트 보드의 내 태스크 → 연결된 GitHub 이슈. 대화 기억·세션 요약 기반 추천 금지\n- 답할 때 출처(보드 태스크명 + 이슈 번호) 명시. 보드와 연결 안 된 열린 이슈는 \"보드 미연결\"로 따로 표시\n- `/task` 후보 출처도 동일(보드 \"내 할 일\" 뷰 → 연결 이슈), 이슈 없는 보드 태스크도 시작 가능\n\n## Commands\n\n```bash\nSPRING_PROFILES_ACTIVE=dev,db,secrets \\\n  GOOGLE_APPLICATION_CREDENTIALS=\"$(pwd)/src/main/resources/firebase/meetjyou-firebase-adminsdk.json\" \\\n  ./gradlew bootRun                                      # run (dev profile, local)\n./gradlew test                                           # all tests\n./gradlew test --tests \"FullyQualifiedClass.methodName\"  # single test\n./gradlew dockerTest                                     # src/dockerTest (Testcontainers MySQL); needs a running Docker daemon\n```\n\n## Code Style\n\n- Kotlin coding conventions\n- No wildcard imports\n- Logs & comments in English (docs/commits in Korean)\n- Magic numbers / repeated strings → constants\n- Enforced in `./gradlew build`: detekt (`config/detekt/detekt.yml`, existing findings frozen in `baseline.xml` — don't add to it), Konsist `ConventionTest` (`!!`, `requireNotNull`, controller DTO `.of()`, service `SecurityUtil`, SQL that sets autocommit/isolation/read-only and stored routines — the driver caches session state via `useLocalSessionState`; bulk UPDATE/DELETE of the user table — it skips the auth-cache eviction listener), and a skipped-test guard (a skipped test fails the build)\n\n## Testing\n\n- New features ship with unit + integration tests\n- Run full `./gradlew test` before finalizing any change; report actual output\n- dockerTest lock-wait detection: poll `performance_schema.data_lock_waits`, never `information_schema.innodb_trx.trx_state` (cached view, misses real waits → flaky CI)\n\n## Verification Workflow\n\nNo hook-enforced gate — the vgate plugin was retired here on 2026-10-13. The Testing rules above and the `./gradlew build` checks are the gate.\n\n- Past gap records and project-side static checks: `docs/agent-process/` (history, no longer enforced by hooks)\n- Pre-plugin in-repo version: tag `agent-process-inrepo-final`\n\n## Conventions\n\n### Current user\nUse `CurrentUserProvider` (`common/util/CurrentUserProvider.kt`) — never call `SecurityUtil` directly in service methods.\n```kotlin\nval user = currentUserProvider.user   // throws UserNotFoundException if not found\nval uuid = currentUserProvider.uuid   // UUID only, no DB hit\n```\n\n### Null safety & exceptions\n`?: throw` Elvis operator exclusively. No `requireNotNull()`, no `if (x == null) throw`, no `!!`.\n```kotlin\nval party = partyRepository.findByUuid(uuid) ?: throw PartyNotFoundException(uuid)\n```\n\n### Error responses\n- HTTP status lives on `ErrorCode(status, message)`, one handler for every `BusinessException` — never pick a base class for its status. `ErrorCodeStatusTest` pins every code's status: a change edits that table on purpose\n- Rejected for the current state (full, closed, already member) → 409, malformed input → 400, outside dependency down → `ExternalServiceException` (503)\n\n### DTO conversion\nService returns DTOs via companion `.of()` factory. Controllers never call `.of()` themselves.\n```kotlin\nreturn GetPartyResponse.of(party)   // in service\n```\n\n### Method length\n30-line hard cap on function bodies. Extract private helpers (`validateXxx` / `buildXxx` / `resolveXxx`).\n\n### N+1 prevention\nNever call a repository inside a loop. Batch-load with `findAllByXxxIn(ids)`, then `groupBy` or `associateBy`.\n\n### Scope functions\nNo 3-chain scope functions (`.apply{}.also{}.let{}`). Use explicit statements instead.\nString concatenation via template literals only — no `+` operator.\n\n### @Transactional\nRead-only methods require `@Transactional(readOnly = true)`. Write methods use `@Transactional`.\n\n### Concurrency bugs\n- Rare races from unnatural request overlaps (e.g. lock-order deadlocks) → handle after the fact, don't restructure locking: full rollback + 409 `CONCURRENT_REQUEST_CONFLICT` (`PessimisticLockingFailureException` in `GlobalExceptionHandler`), Discord alert kept to measure frequency\n- Structural prevention (e.g. party-row-first lock) only once alerts show it actually happens — #199, accepted cases in `.claude/rules/party.md`\n- Lost unique-index races on paths without their own translation → `UniqueConstraintConflicts` (constraint name → `ErrorCode`, 409 + observation alert); an unmapped name stays a 500. A new unique index on such a path needs an entry\n- Not covered by this: races that corrupt data without an error (lost update, stale-snapshot overwrite) — a 409 never fires for them, so judge those separately\n\n### UUID columns\nEvery entity UUID column (business key or FK) carries `@JdbcTypeCode(Types.VARCHAR)`. H2 tests don't catch a missing one — the column type silently diverges from MySQL.\n\n## Architecture\n\n**Profiles:** `dev` (JWT bypass + debug logging) / `db,secrets,release` (production)\n\n**Packages:**\n```\ncom.dogGetDrunk.meetjyou/\n├── auth/               # JWT, social login (Kakao/Google/Apple), dev bypass filter\n├── user/               # accounts, profiles\n├── party/              # group parties + applications\n├── userparty/          # user↔party membership (role, member status)\n├── post/               # travel listings\n├── plan/               # trip planning + markers\n├── chat/               # WebSocket chat (room, message, participant, connection, event)\n├── notification/       # push notifications, transactional outbox\n├── notificationcenter/ # in-app notification center (notices, sent/received applications)\n├── notice/             # admin notices\n├── terms/              # terms versions + agreements\n├── version/            # app version check / forced update\n├── preference/         # user preferences + compatibility matching\n├── image/, cloud/      # OCI Object Storage PAR URLs (thumbnails are made client-side)\n├── config/             # Spring config + @ConfigurationProperties\n└── common/             # exceptions, utilities, idempotency\n```\n\n**Key patterns:**\n- Notification outbox — publish `NotificationEvent`, never send push directly; full pattern in `.claude/rules/notification.md`\n- Schema managed via Flyway (`db/migration/V*.sql`); `ddl-auto: none`\n- `SecurityConfig` defaults to `.anyRequest().authenticated()` — new endpoints are private unless whitelisted there (public paths: `.claude/rules/auth.md`). Admin actions use `@PreAuthorize(\"hasAuthority('ADMIN')\")`.\n\n### Module context\n- Module-only, non-obvious invariants (locking, state machines, delete order, open issues) live in `.claude/rules/<module>.md` with `paths:` frontmatter — loaded only when matching files are touched\n- Don't write what the code shows at a glance (class lists, endpoint lists); keep this file to cross-cutting rules\n- No registry of rule files here — rules are discovered by glob\n\n## Compact Instructions\n\nContext-compaction preserve/drop rules → global CLAUDE.md §9 (pure meta, not a project spec).\n\n## Pipeline guardrails\n\n- 파이프라인 공통 계약: 전역 `agent-pipeline guide` 및 설치본 `defaults/prompts/roles/`, 프로젝트 설정 `.pipeline/project.yaml`\n- 태스크 시작: `/task`, 또는 명령 없는 구현 요청 시 수정 전 1회 확인(\"태스크로 진행 / S 예외로 바로 수정\") — 이 확인이 스펙 카드 대체\n- 태스크 문서: `.pipeline/tasks/<YYYYMMDD-slug>/`, 승인 = intent PR 머지(M·L) 또는 `task/<키>` push(S) — 엔진이 base ref 반영 여부 검사\n- 운영 배포·운영/공유 DB migration·force push·보호 브랜치 직접 push·시크릿 접근 금지\n- 승인된 intent 수정 금지, 변경 필요 시 사람 결정 게이트\n- 구현 에이전트의 수용 테스트 수정 금지, 오류 의심 시 escalation\n- task/dispatch ID는 Orca live preamble 기준, 실패 포함 worker_done 정확히 1회\n- timeout/heartbeat 부재만으로 중복 워커 시작 금지, 종료 확인 후 재분배\n"}, {"path": ".claude/rules/chat.md", "content": "---\ndescription: Chat module rules — loaded only when working in chat/ package\npaths:\n  - \"**/chat/**\"\n---\n\n# Chat Module\n\nSTOMP over WebSocket (`/ws-chat`, pub `/pub/**`, sub `/sub/**`). Key classes:\n- `ChatRoom` / `ChatMessage` / `ChatParticipant` — core domain (`room/`, `message/`, `participant/`)\n- `ChatService` — message handling; `ChatReadService` — read/unread state\n- `connection/` — `ChatStompInterceptor`, `ChatSessionTracker` (presence), `WebSocketEventListener`, `ChatWebSocketSessionRegistry` (open sessions, for server-side close)\n- `event/ChatRoomEventBroadcaster` — room events to subscribers\n\n**Notifications:** publish `NotificationEvent` (see `.claude/rules/notification.md`). Do NOT call push senders directly.\n\n**WebSocket sends:** never call `SimpMessagingTemplate` from business code. Publish `ChatMessageBroadcastEvent` / go through `ChatRoomEventBroadcaster`; `ChatBroadcastListener` sends after commit (`fallbackExecution = true` covers callers without a transaction). Sending mid-transaction exposes uncommitted or rolled-back rows to clients (#151).\n\n**Access lifetime (#221):** a connection is authorized once on CONNECT, so access loss must close it — nothing filters outbound frames\n- One connection = one room (`roomUuid` CONNECT header, stored as a session attribute). SUBSCRIBE is an allowlist: `/sub/chat/room/{connected room}` (+ `/events`) and `/user/queue/errors` only. The simple broker treats a subscription as an Ant pattern, so a looser rule lets a wildcard receive every room\n- Kick / leave → `ChatParticipantService.removeParticipant` publishes `ChatAccessRevokedEvent`; withdrawal → `UserSessionRevoker.revokeAll` publishes `UserSessionsRevokedEvent`. `ChatSessionRevocationListener` closes the matching sessions after commit — close code 1008, reason `MEMBER_BANNED` / `MEMBER_LEFT` (`ChatAccessRevocationReason`) or `USER_WITHDRAWN`; the reconnect is rejected on CONNECT\n- The removed member never receives the room's MEMBER_BANNED / MEMBER_LEFT event (its session closes first, and sending the event first would still race the async outbound channel) — the close reason is its only signal. A new removal path needs its own reason value\n- A new way to lose room access must publish one of these events\n- SEND rechecks membership and `User.isActive()` per message — covers the gap between commit and close\n- Accepted race: a CONNECT validated just before the removal commits can set its attributes after the close scan and stay open until it reconnects (#221 PR)\n\n**Errors to the client:** throw domain exceptions (`BusinessException`), never `IllegalArgumentException` — the app branches on `errorCode`\n- CONNECT / SUBSCRIBE rejected in `ChatStompInterceptor` → `ChatStompErrorHandler` sends an ERROR frame (`message` header = errorCode, body = `ErrorResponse` JSON) and the connection closes. JWT exceptions pass through unchanged: `EXPIRED_TOKEN` means refresh and reconnect\n- A failed SEND (`@MessageMapping`) keeps the connection: `ChatController`'s `@MessageExceptionHandler` replies to the sender only on `/user/queue/errors` (needs `/queue` on the simple broker)\n\n**Testing:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebSocketStompClient` — see `ChatIntegrationTest`.\n"}], "base_ref": "origin/main", "gate": [["./gradlew", "test", "build"]]}

Human-facing language: write PR titles and bodies, commit messages, task documents (intent, spec, decisions, tickets, AT cases, result) and any reply to a human in Korean.
---
id: T-20261103-chat-notice-post-perf-01
title: 채팅방 목록 안 읽은 수 단일 집계
tier: high
effort_override: null
depends_on: []
requirements: [REQ-001]
acceptance_tests: [AT-001-1, AT-001-2]
touches:
  - 'src/main/java/com/dogGetDrunk/meetjyou/chat/'
  - 'src/test/java/com/dogGetDrunk/meetjyou/chat/ChatUnreadAggregationTest.kt'
  - 'src/test/java/com/dogGetDrunk/meetjyou/chat/ChatUnreadAggregationIntegrationTest.kt'
consumes:
  - 'ChatReadService.buildUnreadCountMap'
  - 'UserPartyRepository.findAllWithPartyByUserUuidAndMemberStatus(JOINED)'
  - 'ChatRoomRepository.findAllWithPartyByPartyUuidIn'
  - 'UserParty.lastReadMessageId'
produces:
  - 'ChatMessageRepository.countUnreadByRoomUuids(roomUuids, requesterUuid): List<RoomUnreadCountProjection>'
  - 'ChatReadService: 방 0개면 집계 0회, 방 1개·10개면 집계 1회'
status: blocked
---

## 목표

방별 Repository 호출을 요청자 JOINED 멤버십 기준 집계 한 번으로 교체, 기존 unread 값·목록 접근 범위 유지.

## 분류·결합도·DAG

- high: 성능 목표를 실제 SQL로 입증해야 하는 작업(검증 가능성의 상 조건), Repository와 서비스 연결 변경
- T-02·T-03과 변경 모듈·보호 파일 비중첩, 선행 티켓 없음; 독립 실행 가능

## 구현 범위

- 포함: ChatReadService 집계 경로·ChatMessageRepository 집계 쿼리·RoomUnreadCountProjection 필요한 조정, AT-001 보호 테스트 복사
- 집계 조건: ChatMessage → ChatRoom → 같은 party의 요청자 UserParty, JOINED, 자기 sender 제외, cursor null 또는 message id 초과, room uuid별 group by
- 전달 roomUuids로 제한, 빈 입력은 Repository 호출 전 반환; 집계 row 없는 방은 응답에서 0
- 기존 두 count 메서드는 허용 chat 모듈 안의 호출부 확인 후 다른 사용이 없을 때만 삭제; 범위 밖 호출 가능성이 남으면 유지·보고
- 제외: ChatParticipant·멤버십 정책·읽음 갱신·STOMP/알림 계약 변경, 다른 목록 API 최적화, 인덱스·캐시 도입

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/chat/`의 ChatReadService·ChatMessageRepository·RoomUnreadCountProjection: 기존 unread map과 projection 패턴
- `src/main/java/com/dogGetDrunk/meetjyou/userparty/`의 UserParty: 요청자 JOINED·lastReadMessageId
- `src/main/java/com/dogGetDrunk/meetjyou/chat/room/`의 ChatRoom: party 연결

## 참조 파일 (읽기 허용)

- `AGENTS.md`
- `CLAUDE.md`
- `.pipeline/README.md`
- `.pipeline/project.yaml`
- `src/test/resources/` — 기존 test profile 설정만 읽기, 시크릿 접근·설정 변경 금지
- `src/main/java/com/dogGetDrunk/meetjyou/chat/` — ChatReadService, ChatRoom/Repository, ChatMessage/Repository, unread projection·목록 DTO, 목록 호출부
- `src/main/java/com/dogGetDrunk/meetjyou/userparty/` — UserParty/Repository, MemberStatus, JOINED 목록 선택·읽음 커서
- `src/main/java/com/dogGetDrunk/meetjyou/party/` — fixture의 Party 및 요청자 party 연결
- `src/main/java/com/dogGetDrunk/meetjyou/user/` — fixture의 User/Repository·NORMAL 상태
- `src/main/java/com/dogGetDrunk/meetjyou/common/util/CurrentUserProvider.kt` — 요청자 uuid 접근
- `src/test/java/com/dogGetDrunk/meetjyou/chat/` — 기존 chat fixture·통합 테스트 패턴; 보호 파일 외 수정 금지
- `.claude/rules/chat.md` — 채팅 접근·이벤트 불변식
- `.claude/rules/party.md` — UserParty 상태·DynamicUpdate 불변식

## 관련 spec 발췌

- REQ-001: 안 읽은 수 집계 쿼리 정확히 1회(방 0개면 0회), Repository 호출과 실제 chat_message SELECT 양쪽 검증
- 기존 결과: 타인 메시지 중 cursor 없음=전체, cursor 있음=id 초과; 빈 방=0
- 목록 방의 요청자 JOINED 멤버십과 uk_user_party(user_id, party_id)로 조인 동등성 성립(C-r1-07); 다른 사용자·목록 밖 방 집계 제외
- 테스트 fixture 저장·flush·clear 이후 측정 시작, 전체 API SQL 수 고정 금지(C-r1-05)

## 실행 순서

- [ ] AT-001-1·AT-001-2 보호 파일 복사 및 현재 구현 실패 확인
- [ ] 단일 집계 쿼리 추가, buildUnreadCountMap 호출 교체·빈 입력 처리
- [ ] 요청자·방 필터와 누락 row의 0 처리 자체 리뷰, 구 메서드의 허용 범위 내 참조 확인
- [ ] 대상 테스트 → 전체 test/build → dockerTest 실행 및 같은 HEAD 증거 기록

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
mkdir -p "$(dirname 'src/test/java/com/dogGetDrunk/meetjyou/chat/ChatUnreadAggregationIntegrationTest.kt')" && cp '/Users/serendipity/orca/workspaces/meetjyou-backend/20261103-chat-notice-post-perf/.pipeline/tasks/20261103-chat-notice-post-perf/tests/code/src/test/java/com/dogGetDrunk/meetjyou/chat/ChatUnreadAggregationIntegrationTest.kt' 'src/test/java/com/dogGetDrunk/meetjyou/chat/ChatUnreadAggregationIntegrationTest.kt'
mkdir -p "$(dirname 'src/test/java/com/dogGetDrunk/meetjyou/chat/ChatUnreadAggregationTest.kt')" && cp '/Users/serendipity/orca/workspaces/meetjyou-backend/20261103-chat-notice-post-perf/.pipeline/tasks/20261103-chat-notice-post-perf/tests/code/src/test/java/com/dogGetDrunk/meetjyou/chat/ChatUnreadAggregationTest.kt' 'src/test/java/com/dogGetDrunk/meetjyou/chat/ChatUnreadAggregationTest.kt'
```
- src/test/java/com/dogGetDrunk/meetjyou/chat/ChatUnreadAggregationIntegrationTest.kt: sha256 d7143c2b6be330483a80702e624d4b429295653c1338042ca62b9cb5448c6d98
- src/test/java/com/dogGetDrunk/meetjyou/chat/ChatUnreadAggregationTest.kt: sha256 00e7e3c5e6309d8cb2a8b4b8a3461ee1382adab8004c04be59a61135ac0937e1
# Machine report (engine-read)
Before worker_done write JSON (UTF-8, no BOM) to `.pipeline/logs/runtime/reports/T-20261103-chat-notice-post-perf-01/implementer.json` under the worktree root, replacing any earlier file; it is git-excluded, never commit it:
```json
{
  "schema": "implement/v1",
  "ticket": "T-20261103-chat-notice-post-perf-01",
  "dispatch_id": "<your dispatch id>",
  "outcome": "succeeded|failed",
  "cause": "implementation|protected_test|spec|instruction|environment",
  "evidence": "paths and the exact error"
}
```
- cause: implementation = your code; protected_test = a protected acceptance test is wrong (lint, compile, fixture, assertion); spec = the ticket or spec contradicts itself or lacks information; instruction = these instructions contradict each other or the project rules; environment = build tools, dependencies, network or services
- Any cause but implementation stops the retries and hands the ticket to the designer: name the files and quote the error in evidence
- outcome succeeded: cause implementation