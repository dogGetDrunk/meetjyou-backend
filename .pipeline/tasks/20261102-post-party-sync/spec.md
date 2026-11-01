# Spec

- 태스크: `20261102-post-party-sync` (M), intent 승인본 기준 (PR #242)
- 결정: D1 모집글 원본 동기화 / D2 정원 < joined → 409 / D3 모집 완료 후 PENDING 승인 허용 / D4 재개 시 대칭 OPEN

## 요구사항

### REQ-001 모집글 수정 시 파티 필드 동기화

- 요구사항: `PostService.updatePost` 성공 시 같은 트랜잭션에서 연결 파티의 `capacity`·`itinStart`·`itinFinish`·`destination`을 요청값(`capacity`·`itinStart`·`itinFinish`·`location`)으로 갱신
- 수용 기준:
  - 수정 후 `Party.capacity == Post.capacity`, `Party.itinStart == Post.itinStart`, `Party.itinFinish == Post.itinFinish`, `Party.destination == Post.location`
  - 정원 3 → 10 수정 후, joined 3인 파티에 신규 신청·승인이 `PARTY_FULL` 없이 성공
  - 수정 실패(예외) 시 모집글·파티 모두 미변경 (단일 트랜잭션 롤백)
- 수용 테스트 ID: AT-001-1, AT-001-2, AT-001-3

### REQ-002 정원 축소 검증

- 요구사항: 요청 `capacity` < 락 하에서 읽은 `party.joined`이면 `PartyCapacityBelowJoinedException` (409 `PARTY_CAPACITY_BELOW_JOINED`)
- 수용 기준:
  - `capacity == joined` 허용, `capacity < joined` 거부
  - 거부 시 모집글 다른 필드, compPreference, plan 연결 포함 아무것도 변경되지 않음
  - 검증은 plan·compPreference·필드 변경보다 먼저 실행
  - `ErrorCodeStatusTest` 표에 `PARTY_CAPACITY_BELOW_JOINED to HttpStatus.CONFLICT` 추가
- 수용 테스트 ID: AT-002-1, AT-002-2, AT-002-3

### REQ-003 모집 상태 동기화

- 요구사항: `PostService.updatePostStatus`가 파티 `recruitmentStatus` 동기화
  - `RECRUITMENT_COMPLETED` → `PartyRecruitmentStatus.CLOSED`
  - `RECRUITING` → `PartyRecruitmentStatus.OPEN` (D4)
- 수용 기준:
  - 모집 완료 후 `requestJoinParty` → `PartyRecruitmentClosedException` (기존 409 `PARTY_RECRUITMENT_CLOSED`)
  - 재개 후 `requestJoinParty` 성공
  - 모집 완료 상태에서 기존 PENDING 신청 `approveJoinRequest` 성공 (D3, `approveJoinRequest` 무변경)
  - 같은 상태로의 재요청은 성공(멱등), 상태 그대로
  - COMPLETED 파티는 기존 `validatePostWritable`로 거부(현 동작 유지)
- 수용 테스트 ID: AT-003-1, AT-003-2, AT-003-3

### REQ-004 락 순서

- 요구사항: `updatePost`는 모집글·파티를 읽기 전에 `partyService.lockPartyOfPost(postUuid)`로 파티 행 락 획득 (`updatePostStatus`와 동일 패턴)
  - 근거: `party.capacity`는 `joined`와 같은 컬럼군 → `.claude/rules/party.md` "same-column writes" 규칙. 락 + locked refresh 이후 읽은 `joined`로 REQ-002 판정
  - 결과: `approveJoinRequest`(party lock → capacity check)와 정원 수정 직렬화
- 수용 기준:
  - 락은 plan 변경 여부와 무관하게 항상 획득
  - `updatePost` ↔ `completeParty` 동시 실행 시 데드락 없음: 편집은 파티 락 대기 → 완료 커밋 후 진행 → `validatePostWritable` 거부(현 `InvalidInputException`), 부분 반영 없음
- 수용 테스트 ID: AT-004-1 (MySQL dockerTest)

### REQ-006 락 이후 Post 최신 읽기 (C1-1)

- 요구사항: `updatePost`·`updatePostStatus`는 파티 락 다음 Post를 locking read + locked refresh로 로드 — 신규 `PostRowLock.lockByUuid(uuid): Post?` (`PartyRowLock`과 동일 구현: `PESSIMISTIC_WRITE` 쿼리 + `entityManager.refresh(post, PESSIMISTIC_WRITE)`), `PostRepository`가 상속
  - 근거: `findUuidByPostUuid`가 REPEATABLE READ snapshot 생성 → 이후 일반 `findByUuid`는 락 대기 중 커밋된 값을 못 봄. `@DynamicUpdate` dirty check가 stale 값 기준이라 Post UPDATE 생략 → Post·Party 불일치
  - 락 순서: party → post (`completeParty`와 동일)
- 수용 기준:
  - 정원 3→10 수정 커밋 중 락 대기하던 정원 3 수정 → 커밋 후 Post·Party 모두 3
  - 모집 완료 커밋 중 락 대기하던 모집 재개 → 커밋 후 Post `RECRUITING`, Party `OPEN`
- 수용 테스트 ID: AT-006-1, AT-006-2 (MySQL dockerTest)

### REQ-007 deletePlan detach 순서 party → post (C1-2)

- 요구사항: `PlanService.deletePlan`에서 `partyRepository.detachPlan(plan)` → `postRepository.detachPlan(plan)` 순서로 변경, 주석 갱신
  - 근거: REQ-004로 `updatePost`가 party → post 순서가 됨. 기존 post-first는 옛 `updatePost` 쓰기 순서(post → party)에 맞춘 것(#201) → 그대로 두면 `deletePlan ↔ updatePost` 교착 재발. 409 수용 대신 순서 정렬: #201로 제거된 교착을 되살리지 않음, 변경 2줄
- 수용 기준:
  - `PlanServiceTest` verifyOrder: party detach → post detach → marker 삭제 → plan 삭제
  - plan 전환 편집 ↔ deletePlan 동시 실행 시 교착 없음, 새 plan 연결 유지 (`PartyAggregateDeadlockDockerTest` plan 시나리오 갱신: 편집이 파티 락 보유 중 deletePlan이 party 행에서 대기)
  - #201 시나리오(삭제 쓰기 직전 편집 커밋 → 새 연결 유지) 계속 통과
- 수용 테스트 ID: AT-007-1, AT-007-2

### REQ-005 규칙 문서·기존 테스트 정리

- 요구사항:
  - `.claude/rules/party.md`: "Locked today"에 `capacity`·일정·목적지(`PostService.updatePost`, `lockPartyOfPost` 경유) 추가, "Accepted deadlocks (#165)"에서 `updatePost ↔ completeParty` 제거
  - `.claude/rules/post-plan.md`: detach 순서 "party before post", 근거를 party-row-first로 갱신. "write to post `status` takes the party row lock" 문장에 `updatePost`(정원·일정) 포함
  - `PostUpdatePartyCompleteDeadlockDockerTest`: 데드락 고정 시나리오 → AT-004-1(데드락 없음·편집 거부)로 대체, KDoc 갱신
  - party.md 같은 문단의 `deletePlan` 괄호 설명을 party → post 순서로 수정, "Pinned by" 포인터는 남은 시나리오에 맞게 정리 (C2-1)
  - 기존 단위 테스트(`UpdatePostStatusServiceTest`, `UpdatePostPlanSyncTest` 등)의 `findByUuid` stub·순서 검증을 `lockPartyOfPost` → `lockByUuid` 경로로 갱신, party → post 순서 검증 유지 (C2-2)
- 수용 기준: 문서 diff 존재, 해당 dockerTest가 새 시나리오로 통과, 기존 테스트가 새 호출 경로로 통과
- 수용 테스트 ID: AT-004-1

## 확정 인터페이스

### API 시그니처·DTO

- 엔드포인트·요청/응답 DTO 무변경: `PUT /posts/{postUuid}`, `PATCH /posts/{postUuid}/status`
- 응답 의미 추가: `PUT /posts/{postUuid}`에서 409 `PARTY_CAPACITY_BELOW_JOINED` 가능
- `ErrorCode.PARTY_CAPACITY_BELOW_JOINED(HttpStatus.CONFLICT, "Capacity cannot be lower than the current member count")` — `PARTY_FULL` 인접
- `common/exception/business/party/PartyCapacityBelowJoinedException(partyUuid: UUID, capacity: Int, joined: Int) : InvalidInputException(ErrorCode.PARTY_CAPACITY_BELOW_JOINED, ...)` — `PartyFullException` 패턴
- `Party` 엔티티 메서드 (서비스에서 필드 직접 대입 금지):
  - `fun syncFromPost(capacity: Int, itinStart: Instant, itinFinish: Instant, destination: String)`
  - `fun closeRecruitment()` / `fun openRecruitment()`
  - 정원 검증은 서비스 책임(예외에 partyUuid 필요), 엔티티는 대입만
- `PostRowLock` (`post/PostRowLock.kt`, `PartyRowLock` 구조 복제) + `PostRepository : PostRowLock`
- `PostService.updatePost` 순서: `lockPartyOfPost` → `postRepository.lockByUuid` → 작성자 검증 → `validatePostWritable` → 정원 검증(REQ-002) → plan 변경 → post 필드 → `party.syncFromPost` → compPreference
- `PostService.updatePostStatus`: `findByUuid` → `lockByUuid` 교체, 기존 흐름 끝에 `post.party` 모집 상태 동기화
- `PlanService.deletePlan`: detach 순서 party → post

### DB 스키마

- 변경 없음 (Flyway migration 없음)

## 마일스톤

- M1: REQ-001~007 구현·머지, `./gradlew test build` + `./gradlew dockerTest` 통과
- 제외(intent): backfill, 파티 측 수정 API, 프론트, `requestJoinParty` 비락 정원 판정 재설계
