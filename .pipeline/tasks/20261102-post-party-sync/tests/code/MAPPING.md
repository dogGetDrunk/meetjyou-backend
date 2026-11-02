# 수용 테스트 대응표

| AT ID | 요구사항 | 파일 · 테스트 | 상태 |
| --- | --- | --- | --- |
| AT-001-1 | REQ-001, REQ-004 | `PostPartySyncIntegrationTest` · 두 엔티티의 필드가 동기화되고 경계값을 허용한다; `UpdatePostPartySyncServiceTest` · 잠금 후 네 필드를 파티에 동기화한다; `UpdatePostPlanSyncTest` · 계획서 교체·유지·해제·없음 | 부분 작성; H2 네 필드 재조회 추가, 계획서 변형은 기존 단위 테스트 담당 |
| AT-001-2 | REQ-001 | `PostPartySyncIntegrationTest` · 신청과 승인이 성공하고 참여 인원이 증가한다 | 작성; 실행 미검증 |
| AT-001-3 | REQ-001, REQ-002 | `PostPartySyncIntegrationTest` · 트랜잭션 전체가 롤백된다 | 부분 작성; 저장 실패와 필드 롤백만 확인, 기존 plan·compPreference 스냅샷 미검증 |
| AT-002-1 | REQ-002 | `UpdatePostPartySyncServiceTest` · 경계값을 허용한다; `PostPartySyncIntegrationTest` · 두 엔티티의 필드가 동기화되고 경계값을 허용한다 | 단위와 H2 작성; 실행 미검증 |
| AT-002-2 | REQ-002, REQ-004 | `UpdatePostPartySyncServiceTest` · 계획서 조회 및 선호도 변경 전에 거부한다; `PostPartySyncIntegrationTest` · 정원 예외가 발생하고 게시글과 파티는 유지된다; `PostLockFreshReadDockerTest` · 최신 참여 인원으로 축소를 거부하고 승인을 유지한다 | 단위·H2·MySQL 작성; H2의 plan·compPreference 보존 미검증, 실행 미검증 |
| AT-002-3 | REQ-002 | `UpdatePostPartySyncServiceTest` · 계획서 조회 및 선호도 변경 전에 거부한다; `ErrorCodeStatusTest` · 상태 표; `PostPartySyncIntegrationTest` · 정원 예외가 발생하고 게시글과 파티는 유지된다 | 부분 작성; H2 예외 타입 추가, 예외 상세와 plan·compPreference 보존 미검증; HTTP는 상태 표와 예외 단언으로 대체 |
| AT-003-1 | REQ-003 | `UpdatePostStatusServiceTest` · 게시글을 읽기 전에 연결된 파티 행을 잠근다; 파티 모집 상태는 CLOSED로 유지된다; `PostPartySyncIntegrationTest` · 상태가 멱등이고 신규 신청은 완료 후 거부 재개 후 허용된다 | 단위와 H2 작성; 실행 미검증 |
| AT-003-2 | REQ-003 | `UpdatePostStatusServiceTest` · 게시글과 파티 모집 상태가 열린 상태로 유지된다; `PostPartySyncIntegrationTest` · 상태가 멱등이고 신규 신청은 완료 후 거부 재개 후 허용된다 | 단위와 H2 작성; 실행 미검증 |
| AT-003-3 | REQ-003 | `UpdatePostStatusServiceTest` · InvalidInputException을 던지고 status는 변하지 않는다; PostUpdateAccessDeniedException을 던진다; `PostPartySyncIntegrationTest` · 승인이 성공하고 모집은 닫힌 상태로 유지된다; 두 상태 요청 모두 거부된다 | 단위와 H2 양 상태 요청 작성; 실행 미검증 |
| AT-004-1 | REQ-004, REQ-005 | `PostUpdatePartyCompleteDeadlockDockerTest` · 계획서 변경 여부별 완료 후 편집 거부 | 부분 작성; 일정·compPreference 보존과 문서 diff 미검증 |
| AT-006-1 | REQ-006 | `PostLockFreshReadDockerTest` · 게시글과 파티가 모두 원래 정원으로 커밋된다 | 작성; 사전 로드 변형만 사용, 실행 미검증 |
| AT-006-2 | REQ-006, REQ-003 | `PostLockFreshReadDockerTest` · 게시글과 파티가 모두 모집 중으로 커밋된다 | 작성; 사전 로드 변형만 사용, 실행 미검증 |
| AT-007-1 | REQ-007 | `PlanServiceTest` · 파티 → 게시글 순으로 FK 참조를 해제한 뒤 Plan을 삭제한다 | 작성; 검증 미실행 |
| AT-007-2 | REQ-007, REQ-004 | `PartyAggregateDeadlockDockerTest` · 파티 행 대기 후 새 계획서 유지; #201 쓰기 전 편집 커밋 | 부분 작성; marker 삭제 확인 미작성 |

파일 경로는 `src/test/java/com/dogGetDrunk/meetjyou/` 또는 `src/dockerTest/java/com/dogGetDrunk/meetjyou/` 아래의 패키지 경로 기준. 모든 작성본은 컴파일·실행 미검증.

남은 blocking: AT-001-3은 plan·compPreference의 기존 데이터 보존 미검증, AT-002-2·3은 plan·compPreference 보존과 예외 상세 미검증. AT-004-1의 일정·compPreference 보존과 AT-007-2의 marker 삭제 미검증. MySQL AT-006-1·2는 사전 로드 변형 하나만 사용하며 일반 경로는 별도 작성하지 않음. H2는 실제 repository와 TransactionTemplate을 사용해 서비스 트랜잭션을 재현하고 저장 예외는 repository spy와 preference mock으로 주입; 실제 구현 전이므로 컴파일·실행하지 않았고 spy 호환성도 미확인.
