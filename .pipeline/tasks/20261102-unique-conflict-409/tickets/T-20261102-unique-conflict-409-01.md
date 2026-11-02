---
id: T-20261102-unique-conflict-409-01
title: 유니크 제약 경합 500 → 409/멱등 처리 4곳
tier: high
effort_override: null
depends_on: []
requirements: [REQ-1, REQ-2, REQ-3, REQ-4, REQ-5]
acceptance_tests: [AT-1-1, AT-2-1, AT-3-1, AT-3-2, AT-4-1, AT-4-2, AT-5-1]
touches:
  - src/main/resources/db/migration/V42__name_push_token_and_chat_participant_unique_indexes.sql
  - src/main/java/com/dogGetDrunk/meetjyou/common/exception/UniqueConstraintConflicts.kt
  - src/main/java/com/dogGetDrunk/meetjyou/chat/connection/WebSocketEventListener.kt
  - src/main/java/com/dogGetDrunk/meetjyou/chat/ChatController.kt
  - src/main/java/com/dogGetDrunk/meetjyou/post/PostService.kt
  - src/main/java/com/dogGetDrunk/meetjyou/preference/CompPreferenceRepository.kt
  - src/dockerTest/java/com/dogGetDrunk/meetjyou/common/UniqueConstraintConflictsDockerTest.kt
consumes:
  - DataIntegrityViolationException.violatedConstraintName (common/exception/ConstraintViolations.kt)
  - GlobalExceptionHandler.handleDataIntegrityViolation
produces:
  - UniqueConstraintConflicts 매핑 3건 추가 (uk_push_token_token, uk_chat_participant_user_room, uq_comp_preference)
  - ChatController DataIntegrityViolationException 전용 @MessageExceptionHandler
status: ready
---

## 목표
동시 요청이 유니크 인덱스 경합에서 졌을 때 4곳이 500 대신 409 또는 멱등 성공을 반환. 이슈 #247.

## 구현 범위
- REQ-1: `V42__...sql`로 `push_token`의 `token` 인덱스 → `uk_push_token_token`, `chat_participant`의 `user_id` 인덱스 → `uk_chat_participant_user_room` (`ALTER TABLE ... RENAME INDEX`, V40 패턴). 엔티티에 `@Table(uniqueConstraints = ...)` 이름 반영이 기존 관례면 동일하게
- REQ-2: `UniqueConstraintConflicts`에 `uk_push_token_token` → `CONCURRENT_REQUEST_CONFLICT` (주석: 재시도 시 이긴 행 재조회 후 갱신)
- REQ-3: `uk_chat_participant_user_room` → `CONCURRENT_REQUEST_CONFLICT`. `WebSocketEventListener.handleConnect`에서 `enterRoom`이 이 제약 위반을 던지면 "이미 입장"으로 보고 disconnect·rethrow 없이 `markLatestMessageAsRead`로 진행 (info 로그). 다른 예외는 기존 동작 유지
- REQ-4: `PostService.updatePost`의 comp_preference 교체가 IDENTITY insert보다 DELETE를 먼저 실행하도록 수정 (`@Modifying` JPQL bulk delete 또는 delete 후 flush — 단순한 쪽, 선택 근거를 PR에 기록). 먼저 AT-4-1로 RED 재현·기록. 남는 동시 수정 경합 대비 `uq_comp_preference` → `CONCURRENT_REQUEST_CONFLICT`
- REQ-5: `ChatController`에 `@MessageExceptionHandler(DataIntegrityViolationException::class)` 추가. 제약 이름이 `uk_chat_message_room_sender_client_id`면 info 로그 후 에러 프레임 없이 종료(`@SendToUser` 없는 Unit 반환 또는 동등), 아니면 기존 `INTERNAL_SERVER_ERROR` ErrorResponse를 `/queue/errors`로
- `UniqueConstraintConflictsDockerTest`의 `MAPPED_CONSTRAINTS`·인덱스 조회 갱신 + 신규 매핑 3건의 MySQL 예외 이름 형태 검증
- 각 수정마다 되돌리면 해당 테스트가 red인지 확인

### 하지 말 것
- 잠금 구조 변경, native upsert(`INSERT IGNORE`·`ON DUPLICATE KEY`)
- `WebSocketEventListener`의 일반 예외 rethrow 정책 변경 (#231 별도 항목)
- #231 나머지 항목, `ErrorCodeStatusTest` 의도치 않은 변경
- 운영 DB에 migration 적용 (파일 작성만)

## 참고할 기존 패턴
- src/main/resources/db/migration/V40__name_user_unique_indexes.sql (자동 이름 인덱스 rename)
- src/main/java/com/dogGetDrunk/meetjyou/common/exception/UniqueConstraintConflicts.kt
- src/dockerTest/java/com/dogGetDrunk/meetjyou/common/UniqueConstraintConflictsDockerTest.kt
- src/main/java/com/dogGetDrunk/meetjyou/user/UserService.kt:196 (제약 이름 분기 catch)
- .claude/rules/chat.md, .claude/rules/post-plan.md

## 관련 spec 발췌
- 원인: `violatedConstraintName()` = `constraintName.substringAfterLast('.')` → 이름 없는 V1 인덱스는 `token`·`user_id`로만 식별
- `CompPreference.id`는 `GenerationType.IDENTITY`, `deleteAllByPost`는 파생 delete(조회 후 remove, DELETE는 flush 시) → 같은 선호 유지 수정 시 INSERT가 먼저 나가 위반 추정. H2 테스트는 `ddl-auto: create-drop`이라 제약 없음 → dockerTest 필수
- 경합 응답 규칙: CLAUDE.md "Concurrency bugs" — 사후 처리, 409 + 관측 알림 유지
- 검증: `./gradlew test build` + `./gradlew dockerTest` (Docker 데몬 필요)
