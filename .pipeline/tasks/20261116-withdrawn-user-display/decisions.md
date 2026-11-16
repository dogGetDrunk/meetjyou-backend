# Spec 비평 결정

> 결정 값: `수용`, `기각-오판`(비평 전제가 틀림), `기각-범위`(intent 제외 범위), `기각-감수`(알려진 위험이나 결함을 남김, 고치는 대신 문서화 포함).
> `기각-범위`, `기각-감수`는 사람에게 묻고 `사람 승인` 칸에 승인자와 발언 원문을 적은 뒤에만 기록. 분류 없는 `기각`과 빈 승인 칸은 엔진이 거부.

| 라운드 (최대 2) | 항목 | 수용/기각 | 근거 | 메인 설계자 | 사람 승인 |
|---|---|---|---|---|---|
| 1 | C-r1-01 | 수용 | intent 성공 기준에 NORMAL 회귀가 있는데 HTTP 테스트 대상이 DELETED, PURGED뿐이었음 → 활동 중 호스트(멤버 목록, 채팅 발신자)와 NORMAL 신청자를 HTTP 검증 대상에 추가 | Claude (opus-5-5) | |
| 1 | C-r1-02 | 수용 | 브로드캐스트는 `ChatService.sendMessage` 가 같은 `ChatMessageResponse.of` 로 만들지만 이를 지키는 검증이 없음 → 브로드캐스트 이벤트 페이로드의 `senderNickname`, `isWithdrawn=false` 단위 검증 추가. 비활성 회원은 STOMP 연결에서 거부되므로 브로드캐스트는 NORMAL 발신자만 대상 | Claude (opus-5-5) | |
| 1 | C-r1-03 | 기각-오판 | `.pipeline/project.yaml` 의 gate가 `[./gradlew, test, build]` 이므로 `agent-pipeline gate` 가 곧 `./gradlew test build`(detekt, Konsist, 건너뛴 테스트 검사 포함) 실행 | Claude (opus-5-5) | |
