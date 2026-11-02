# Spec 비평 결정

| 라운드 (최대 2) | 항목 | 수용/기각 | 근거 | 메인 설계자 |
|---|---|---|---|---|
| 0 (사람 결정) | D1 이름 없는 V1 제약 2개 → Flyway `RENAME INDEX` 후 매핑 (A안) | 수용 | `violatedConstraintName()`이 테이블명 제거 → 범용 자동 이름(`token`·`user_id`)은 향후 오매핑 위험. RENAME INDEX는 메타데이터만 변경. 기각: B안(자동 이름 그대로 매핑) | 사람 승인 2026-11-02 "승인. A안으로 진행" |
| 0 (사람 결정) | D2 push_token → 409 `CONCURRENT_REQUEST_CONFLICT` | 수용 | 재시도 시 이긴 행 재조회 후 소유자 갱신으로 성공 | 사람 승인 2026-11-02 "작성하고 진행해" |
| 0 (사람 결정) | D2 chat_participant → 전역 409 + connect 리스너는 "이미 입장"으로 정상 진행 | 수용 | 리스너는 비트랜잭션, `enterRoom` 트랜잭션만 롤백 → 호출부 catch 안전. 리스너 rethrow 일반 문제(#231 별도 항목)는 범위 밖 | 위와 동일 |
| 0 (사람 결정) | D2 uq_comp_preference → delete를 insert 전에 실행되게 수정 + 남는 동시 수정 경합은 409 | 수용 | 파생 delete는 flush 시점 DELETE, IDENTITY insert는 즉시 → 단일 요청에서도 위반 추정(H2 create-drop은 제약 없어 미검출). dockerTest로 RED 먼저 | 위와 동일 |
| 0 (사람 결정) | D2 chat message clientMessageId 경합 → 중복 재전송으로 무시(에러 프레임 없음, info 로그) | 수용 | 기존 사전 중복 체크와 동일 동작, 이긴 메시지는 이미 broadcast. 다른 제약 위반은 기존대로 INTERNAL_SERVER_ERROR | 위와 동일 |
| 0 | native upsert(`INSERT IGNORE`·`ON DUPLICATE KEY`) | 기각 | FK 오류까지 삼킬 위험, H2 비호환, S 규모 대비 과설계 | 메인 설계 |
