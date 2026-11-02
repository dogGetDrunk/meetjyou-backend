# 결과

> 태스크 종료 시 메인 설계 에이전트가 작성. 포트폴리오·회고 재료.

## 결과 요약

- 동시 요청 유니크 제약 경합 시 500 나던 4곳 → 409 또는 멱등 처리 (#247, #231에서 분리)
- 부수 발견: `comp_preference`는 경합이 아닌 결정적 버그 — 선호 항목을 유지한 동행 조건 수정이 MySQL에서 매번 500. H2 `create-drop`에 제약이 없어 기존 테스트가 못 잡음
- 파이프라인 워커 미사용, 메인 세션 직접 구현 (아래 "진행 경위")

## 머지된 PR

| 티켓 | PR | 등급 | 구현 / 리뷰 모델 | 라운드 |
|---|---|---|---|---|
| T-20261102-unique-conflict-409-01 | #249 | high | claude-opus-5-5 (메인 세션 직접) / 교차 리뷰 없음 | 1 |
| (엔진 수정, 태스크 외) | #248 | - | claude-opus-5-5 직접 | 1 |

## 핵심 결정과 기각한 대안

- D1 V1 이름 없는 인덱스 → V42 `RENAME INDEX` 후 매핑. 기각: 자동 이름 `token`·`user_id` 그대로 매핑 (`violatedConstraintName()`이 테이블명 제거 → 향후 오매핑)
- comp_preference → `@Modifying` JPQL bulk delete. 기각: delete 후 `flush()` (DELETE N건 유지, 호출부마다 flush 필요)
- chat_participant connect 경합 → 리스너 호출부에서 해당 제약만 catch해 "이미 입장" 처리 (리스너 비트랜잭션, `enterRoom` 트랜잭션만 롤백). 승인 API 경로는 전역 409
- clientMessageId 경합 → 에러 프레임 없이 무시 (기존 사전 중복 체크와 동일 동작)
- 엔티티에 제약 이름 명시 → H2 테스트도 운영 스키마와 같은 제약으로 검증
- 기각: native upsert (`INSERT IGNORE`·`ON DUPLICATE KEY`) — FK 오류 은폐 위험, H2 비호환

## spec 대비 검증

- AT-1-1 dockerTest: 매핑 대상 제약 전부 존재, 옛 자동 이름 인덱스 없음
- AT-2-1 dockerTest: push_token 실제 MySQL 위반 → 409 매핑
- AT-3-1·AT-4-2: 단위 테스트(`UniqueConstraintConflictsTest`)로 대체 — 실제 MySQL insert는 Party·ChatRoom·Post 시드 비용 대비 이득 작음, 이름 존재는 AT-1-1·이름 형태는 AT-2-1이 보장
- AT-3-2 `WebSocketEventListenerTest`, AT-5-1 `ChatControllerExceptionTest`
- AT-4-1: dockerTest 대신 H2 통합 테스트(`UpdatePostCompPreferenceIntegrationTest`) — 엔티티 제약 명시로 H2에서도 재현
- RED: 수정 되돌리면 새 테스트 5건 실패 (comp_preference 단일 요청 `Unique index or primary key violation` 재현)
- `./gradlew test build` 606/0, `./gradlew dockerTest` 49/0

## 진행 경위 (파이프라인)

- coordinator 1차 `--once` → `KeyError: 'worker'`로 티켓 blocked, 워커 미시작 (Run `run_ee0e5225ced4` worker-list 비어 있음)
- 원인: Orca `worker-show` 간략 뷰에 중첩 `worker` 레코드 없음 → #248로 `--full` 부착, 전역 재설치
- 재실행 보류: 공유 `coordinator.json`에 이 티켓 `blocked`(종료 상태) 기록 잔존, #223 T-01 claim이 `PostService.kt` 점유 → 겹침 시 대기가 아닌 blocked 처리
- 사람 결정으로 메인 세션 직접 구현 전환, 공유 상태는 수정하지 않음

## 남은 후속 작업

- 운영 배포 시 V42 적용 (RENAME INDEX, 테이블 재작성 없음)
- 엔진: touches 겹침을 blocked 대신 대기로 처리, blocked 기록 수동 해제 절차 부재
- 공유 `coordinator.json`의 `T-20261102-unique-conflict-409-01: blocked` 기록 정리 여부 결정
- #231 나머지 항목
