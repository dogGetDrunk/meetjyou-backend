# Intent

> 메인 설계 에이전트가 사람과 대화로 작성. 작성을 마치면 첫 줄의 template 표시 삭제.
> 승인 기록은 `approval.json`(`agent-pipeline approve-intent`), 승인 후 이 파일 수정 금지.

## 해결할 문제

- 동시 요청 2건이 같은 유니크 인덱스에 insert 경합 → 진 쪽이 `DataIntegrityViolationException`
- 아래 4곳은 `UniqueConstraintConflicts` 미매핑(또는 해당 핸들러 미적용) → 500 + 장애 알림
  - `push_token.token` (V1, 이름 없는 UNIQUE, MySQL 자동 이름 `token`) — `PushTokenService.register` findByToken 후 insert
  - `chat_participant (user_id, room_id)` (V1, 이름 없음, MySQL 자동 이름 `user_id`) — `ChatParticipantService.enterRoom` check-then-act
  - `uq_comp_preference` (V14) — `PostService.updatePost` delete-then-insert, 동시 수정 시 경합 가능
  - `uk_chat_message_room_sender_client_id` (V32) — `ChatService` clientMessageId check-then-act, STOMP 경로라 `ChatController`의 `@MessageExceptionHandler(Exception)`이 `INTERNAL_SERVER_ERROR` 반환

## 의도

- 경합에서 진 요청이 500 대신 상황에 맞는 응답을 받게 함 (재시도 가능한 409 또는 멱등 성공)
- 프로젝트 규칙(CLAUDE.md "Concurrency bugs") 그대로 적용: 잠금 구조 변경 없이 사후 처리, 관측 알림 유지

## 포함 범위

- 위 4곳의 경합 결과를 500이 아닌 응답으로 변환
- 이름 없는 V1 제약 2개의 식별 방식 결정 및 반영 (아래 열린 결정)
- STOMP 경로의 `DataIntegrityViolationException` 처리
- 각 경로 단위·통합 테스트, 제약 이름 검증 dockerTest 갱신 (`UniqueConstraintConflictsDockerTest`)

## 제외 범위

- #231의 나머지 항목 (auth·purge·마커·읽음 커서 등)
- 잠금 순서 재구성 등 구조적 예방
- 에러 없이 데이터가 어긋나는 경합 (lost update 등)

## 열린 결정 (사람 결정 필요)

- **D1. 이름 없는 V1 제약 2개 식별 방식**
  - A안 (추천): Flyway migration으로 `RENAME INDEX` (`token` → `uk_push_token_token`, `user_id` → `uk_chat_participant_user_room`) 후 새 이름으로 매핑
    - 근거: `violatedConstraintName()`이 `substringAfterLast('.')`로 테이블명을 버림 → 자동 이름 `user_id`·`token`은 지금은 유일하지만, 이후 다른 테이블에 같은 자동 이름이 생기면 조용히 오매핑. InnoDB `RENAME INDEX`는 메타데이터만 변경
    - 비용: 운영 DB에 migration 1개 추가 (적용은 배포 시 사람이 수행, 에이전트는 파일만 작성)
  - B안: migration 없이 자동 이름 `token`·`user_id` 그대로 매핑
    - 기각 사유 후보: 범용 이름이라 오매핑 위험, 의미 불명확
- **D2. 경로별 응답**: 409 `CONCURRENT_REQUEST_CONFLICT` vs 멱등 성공(예: 같은 clientMessageId 재전송·이미 참여 중인 방 입장은 이긴 쪽 결과와 동일) — spec 단계에서 경로별 제안 후 확인

## 성공 기준

- 4곳 각각 경합 재현 테스트에서 500/`INTERNAL_SERVER_ERROR`가 나지 않음
- 경합으로 인한 중복 행 미생성 (유니크 인덱스가 계속 보장)
- `UniqueConstraintConflictsDockerTest`가 실제 Flyway 스키마에 매핑 대상 이름 전부 존재함을 확인
- `./gradlew build`(detekt·Konsist 포함), `./gradlew test`, `./gradlew dockerTest` 통과
- 회귀 확인: 수정 되돌리면 새 테스트가 red

## 작업 크기

S — 기존 매핑 확장 + STOMP 핸들러 1개 + migration 1개 수준의 국소 변경

## 출처

- 노션 태스크: https://app.notion.com/p/40556d9a97d98161958cdc8fbcc62797 (코드 리뷰 잔여 개선 항목 (10/26), TODO 1번)
- GitHub 이슈: dogGetDrunk/meetjyou-backend#247 (#231에서 분리)
