---
id: T-20261102-post-party-sync-01
title: 모집글·파티 동기화와 aggregate 락 순서 원자적 정렬
tier: high
effort_override: null
depends_on: []
requirements:
- REQ-001
- REQ-002
- REQ-003
- REQ-004
- REQ-005
- REQ-006
- REQ-007
acceptance_tests:
- AT-001-1
- AT-001-2
- AT-001-3
- AT-002-1
- AT-002-2
- AT-002-3
- AT-003-1
- AT-003-2
- AT-003-3
- AT-004-1
- AT-007-1
- AT-007-2
touches:
- src/main/java/com/dogGetDrunk/meetjyou/post/PostService.kt
- src/main/java/com/dogGetDrunk/meetjyou/post/PostRepository.kt
- src/main/java/com/dogGetDrunk/meetjyou/post/PostRowLock.kt
- src/main/java/com/dogGetDrunk/meetjyou/party/Party.kt
- src/main/java/com/dogGetDrunk/meetjyou/plan/PlanService.kt
- src/main/java/com/dogGetDrunk/meetjyou/common/exception/ErrorCode.kt
- src/main/java/com/dogGetDrunk/meetjyou/common/exception/business/party/PartyCapacityBelowJoinedException.kt
- src/test/java/com/dogGetDrunk/meetjyou/post/UpdatePostPartySyncServiceTest.kt
- src/test/java/com/dogGetDrunk/meetjyou/post/PostPartySyncIntegrationTest.kt
- src/test/java/com/dogGetDrunk/meetjyou/post/UpdatePostStatusServiceTest.kt
- src/test/java/com/dogGetDrunk/meetjyou/post/UpdatePostPlanSyncTest.kt
- src/test/java/com/dogGetDrunk/meetjyou/plan/PlanServiceTest.kt
- src/test/java/com/dogGetDrunk/meetjyou/common/exception/ErrorCodeStatusTest.kt
- src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PostUpdatePartyCompleteDeadlockDockerTest.kt
- src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PartyAggregateDeadlockDockerTest.kt
- .claude/rules/party.md
- .claude/rules/post-plan.md
consumes:
- 'PartyService.lockPartyOfPost(postUuid): Unit'
- 'PartyRowLock.lockByUuid(uuid): Party?'
- PostService.applyPlanChange / validatePostWritable / assertIsAuthor
- 'PostRepository.detachPlan(plan): Int / PartyRepository.detachPlan(plan): Int'
- PartyService.requestJoinParty / approveJoinRequest / completeParty
- CurrentUserProvider.uuid / CompPreferenceRepository / PreferenceRepository
- 'PartyRowLockImpl: PESSIMISTIC_WRITE query + locked refresh'
produces:
- 'PostRowLock.lockByUuid(uuid: UUID): Post? / PostRepository : PostRowLock'
- 'Party.syncFromPost(capacity: Int, itinStart: Instant, itinFinish: Instant, destination: String)'
- 'Party.closeRecruitment(): Unit / Party.openRecruitment(): Unit'
- 'PartyCapacityBelowJoinedException(partyUuid: UUID, capacity: Int, joined: Int)'
- 'ErrorCode.PARTY_CAPACITY_BELOW_JOINED: HTTP 409'
- 'PostService.updatePost / updatePostStatus: party → post lock + synchronized Party'
- 'PlanService.deletePlan: party detach → post detach → marker delete → plan delete'
- 기존 회귀 테스트 갱신·단위/H2 수용 테스트·규칙 문서 정합성
status: in_progress
---

## 목표
모집글 편집·모집 상태 변경의 Party 동기화와 party → post 락 순서 일괄 반영, 단위/H2 및 기존 MySQL 회귀 테스트 확보.

## 구현 범위
- REQ-001·002·003·004·006: `PostService.kt` 동일 파일 변경의 단일 티켓 묶음; `Party.kt` 메서드·오류 코드·PostRowLock 함께 구현
- `PostRowLock.kt` 신규: `PartyRowLock` 구조 그대로, `PESSIMISTIC_WRITE` 조회 후 `entityManager.refresh(post, PESSIMISTIC_WRITE)`, 없으면 null; `PostRepository : PostRowLock` 상속
- `updatePost`: `lockPartyOfPost` → `postRepository.lockByUuid` → 작성자 검증 → `validatePostWritable` → `capacity < party.joined` 검증 → plan 변경 → post 필드 → `party.syncFromPost` → compPreference 순서
- plan 변경 유무와 무관한 파티 락; 동일 쓰기 트랜잭션 유지; 정원 검증은 locked refresh로 확보한 최신 joined 기준, Party 메서드는 대입만 수행
- `PARTY_CAPACITY_BELOW_JOINED(HttpStatus.CONFLICT, "Capacity cannot be lower than the current member count")`를 `PARTY_FULL` 인접 배치; `PartyFullException` 패턴의 `InvalidInputException` 하위 예외와 ErrorCodeStatusTest 표 추가
- `updatePostStatus`: 기존 파티 락 다음 `findByUuid`를 `lockByUuid`로 교체; 기존 흐름 끝에 `RECRUITMENT_COMPLETED → closeRecruitment`, `RECRUITING → openRecruitment` 반영; 같은 상태 재요청 성공 및 COMPLETED 거부 유지
- REQ-007: `PlanService.deletePlan` bulk detach를 party → post로 변경, 영어 주석 갱신; PlanServiceTest verifyOrder 수정; 기존 두 plan 동시성 시나리오 갱신·보존
- REQ-005: party.md Locked today에 capacity·일정·목적지와 updatePost/lockPartyOfPost 추가; Accepted deadlocks에서 updatePost ↔ completeParty 제거; deletePlan 괄호 설명을 party → post로 정정; Pinned by는 남은 허용 교착 시나리오에 한정
- post-plan.md: updatePost의 정원·일정 쓰기도 파티 락 대상임을 명시; detach 순서를 party before post, 근거를 party-row-first로 갱신
- `PostUpdatePartyCompleteDeadlockDockerTest`를 AT-004-1의 대기 후 편집 거부로 교체, KDoc 갱신; `PartyAggregateDeadlockDockerTest`는 AT-007-2의 party 행 대기 및 #201 새 링크 보존 검증, 나머지 시나리오 유지
- 기존 `UpdatePostStatusServiceTest`, `UpdatePostPlanSyncTest`의 변경 대상 호출 stub·검증을 `lockPartyOfPost → lockByUuid`로 갱신; 없는 post·타 작성자·COMPLETED 거부 회귀 유지; DTO 검증 `UpdatePostStatusTest` 그대로 실행
- 새 단위 `UpdatePostPartySyncServiceTest`와 H2 `PostPartySyncIntegrationTest`에 대응 AT 구현; AT-002-2의 MySQL 승인 경쟁 및 AT-006-1·2는 T-20261102-post-party-sync-02에서 완성
- 제외: API/DTO·DB 스키마·Flyway·backfill·PartyService의 신청/승인 로직 변경·파티 측 수정 API·프론트

## 참고할 기존 패턴
- `src/main/java/com/dogGetDrunk/meetjyou/party/PartyRowLock.kt`: 잠금 조회 + 잠금 refresh
- `src/main/java/com/dogGetDrunk/meetjyou/common/exception/business/party/PartyFullException.kt`: 비즈니스 예외
- `src/test/java/com/dogGetDrunk/meetjyou/post/UpdatePostStatusServiceTest.kt`, `UpdatePostPlanSyncTest.kt`: 직접 구성한 PostService + mockk BehaviorSpec
- `src/test/java/com/dogGetDrunk/meetjyou/party/PartyPostLostUpdateIntegrationTest.kt`, `UpdatePartyNameIntegrationTest.kt`: H2 SpringExtension/실제 HTTP·별도 트랜잭션 재조회
- `src/dockerTest/java/com/dogGetDrunk/meetjyou/party/PartyAggregateDeadlockDockerTest.kt`: SqlPausePoint·독립 connection·락 대기 관측
- `.claude/rules/party.md`, `.claude/rules/post-plan.md`: 해당 경로 규칙

## 관련 spec 발췌
- REQ-001: “같은 트랜잭션에서 연결 파티의 capacity·itinStart·itinFinish·destination을 요청값으로 갱신”
- REQ-002: “요청 capacity < 락 하에서 읽은 party.joined이면 … 409”; plan·compPreference·필드 변경보다 먼저 검증
- REQ-003: 모집 완료 CLOSED / 재개 OPEN; 기존 PENDING 승인 허용, approveJoinRequest 무변경
- REQ-004·006: 모집글·파티 읽기 전 파티 락, 다음 Post locking read + locked refresh; party → post 순서
- REQ-007: deletePlan detach party → post; #201 교착 재도입 방지
- REQ-005: 규칙 문서·기존 테스트의 새 경로 정합성 확보

## 분류·결합도·실행 계획
- high 근거: 트랜잭션 경계·락·동시성·공유 repository 인터페이스·여러 레이어 변경의 상 조건 충족; 표의 low 예시보다 상 조건 우선
- effort_override null: 승인된 tier 기본 effort 사용, max 및 임의 모델 변경 금지
- PostService의 동일 파일 변경은 일괄 묶음; deletePlan 순서를 따로 선행/후행 머지하면 기존/신규 쓰기 순서와 충돌하므로 함께 반영
- DAG: `T-20261102-post-party-sync-01 (ready) → T-20261102-post-party-sync-02 (pending)`; 병렬 티켓 없음
- 순서: 기존 AT RED 확인 → 엔티티·오류·repository + 서비스·detach 함께 구현 → 단위/H2·기존 docker 회귀/문서 갱신 → gate → T-02에 확정 인터페이스 인계
- base_ref: `origin/main`; 보호 수용 문서 `tests/cases/AT-*.md` 수정 금지, spec 밖 판단 필요 시 메인 설계 escalation

## 검증·완료 기준
- `./gradlew test build`: 프로젝트 gate, 전체 단위/H2·detekt·Konsist·skip guard 통과
- `./gradlew dockerTest`: 기존 MySQL 회귀 전체 통과, AT-004-1·AT-007-2 새 시나리오 통과; Docker daemon 필요, 운영/공유 DB 사용 금지
- `git diff --check` 및 두 규칙 문서 diff 확인; 테스트 실패/미실행은 그대로 기록, skip·명령 완화 금지
- AT-001-1~3·AT-002-1~3(AT-002-2 MySQL 경쟁은 후속 티켓)·AT-003-1~3·AT-004-1·AT-007-1~2 충족; REQ-006 구현 산출물은 후속 MySQL 수용 검증 전 전체 태스크 완료 주장 금지

## 수용 테스트 코드 (보호 대상)
- 원본: 이 태스크 폴더 `tests/code/` 아래 src와 같은 경로 구조 (별도 세션 Terra 작성)
- 구현 첫 커밋: 이 티켓 acceptance_tests의 보호 대상 파일을 `tests/code/<경로>` → `<경로>`로 그대로 복사 (diff 0)
- 복사본 수정 금지. 테스트 오류 의심 시 수정하지 말고 escalation
- 리뷰어: `diff .pipeline/tasks/20261102-post-party-sync/tests/code/<경로> <경로>` 무출력 확인
