# 결과

## 결과 요약

- 노션 작업 245 채팅 결함 3건(#293, #294, #295)과 코드 판독 중 찾은 1건(STOMP 별칭 무인증 연결) 수정
- #293은 운영과 같은 MySQL 8.0.41 기본 설정에서 재현: 옛 115자 키 → `Data too long for column 'dedup_key'`, outbox 행 0개. 7/19(`345af5a`) 이후 오프라인 수신자 채팅 푸시가 저장되지 않았을 가능성이 높음(운영 DB URL 세션 변수만 미확인)
- #295는 수정 전 코드에서 재현: 두 번째 CONNECT가 `handleConnect` 까지 와서 방 바인딩을 바꾸고, 바뀐 방 구독과 수신이 허용됨
- #294의 방 목적지 2개는 재현과 차단 확인. 세 번째(다른 유저 오류 큐)는 현재 `SimpUserRegistry` 가 비어 있어 재현되지 않지만 같은 가드로 차단

## 변경 내용

- `ChatService`: 채팅 푸시 dedup 키 `chat:{roomUuid}:{receiverUuid}:{messageUuid}`(115자) → `chat:{messageUuid}:{receiverUuid}`(78자)
- `NotificationOutbox.dedupKey`: `@Column(length = 100)` (`DEDUP_KEY_MAX_LENGTH`), H2 테스트 스키마가 운영 `VARCHAR(100)` 과 일치
- `ChatStompInterceptor`
  - SEND: destination이 `/pub/` 로 시작하지 않으면 `INVALID_INPUT_VALUE` ERROR 프레임과 연결 종료
  - CONNECT: 세션에 이미 방이 묶여 있으면 JWT, DB 검사 전에 거절
  - `STOMP` 명령(CONNECT 별칭)도 `handleConnect` 로 처리
  - `APP_DESTINATION_PREFIX` 를 `WebSocketConfig` 와 공유
- `.claude/rules/chat.md`: 연결당 CONNECT 한 번, SEND는 `/pub/**` 만, `SimpUserRegistry` 가 비어 있는 사실과 의미
- `config/detekt/baseline.xml`: 기존 `LongParameterList:NotificationOutbox` 항목의 ID 텍스트만 갱신(`@Column` 추가로 생성자 텍스트가 바뀜, 항목 수 변화 없음)
- 테스트
  - `ChatStompFrameGuardIntegrationTest`(raw STOMP): AT-003-1~3, AT-004-1, AT-005-1~3
  - `ChatStompInterceptorFrameGuardTest`(단위): AT-003-4, AT-004-2
  - `ChatPushRecipientIntegrationTest` 확장: AT-001-1(수신자 2명 x 메시지 2개 = 정확한 키 4개)
  - `NotificationOutboxDedupKeyLengthTest`: AT-002-1(101자 거절, 100자 저장)
  - `ChatPushOutboxDockerTest`(MySQL): AT-001-2
  - 지원: `RawStompClient`, `ChatTestDataHelper` 조회 메서드 2개

## 비평 결정

- 1라운드 `gpt-6.2-sol` high, 8건 전부 수용(C-r1-01~08)
- 주로 증거 강화: 운영 확인 단계 명시, 옛 키 + 길이 제한 중간 상태 검증, 커밋 후 별도 트랜잭션 조회, 키 유일성 2축, control 프레임으로 구독 준비 증명, 중복 CONNECT 전제 관측 기준, 바인딩 보존 관측 위치, STOMP 별칭 transport 수준 테스트

## 구현 후 검증

- 1라운드 `gpt-6.2-sol`, 검증 커밋 `d75b6a5`, 4건 전부 수용
  - V-r1-01: AT-003-3에서 빠졌던 구독 준비 증명과 미수신 확인 복구(세션 id 지정 control 프레임)
  - V-r1-02, V-r1-03: 운영 확인과 H2 중간 상태를 이 문서에 기록(아래)
  - V-r1-04: MySQL 테스트에 메시지 2개, 행 4개 명시 단언
- 실행 실패와 재시도 없음

## 운영 확인 (REQ-000, 읽기 전용, 2026-11-24)

- 앱 로그 범위: 2026-11-22T08:09:43 ~ 2026-11-24T14:13:23(11/22 배포로 컨테이너 교체, 이전 로그 없음, Alloy는 메트릭만 전송). 이 범위에 채팅 사용 0건, `Data too long` 0건
- 운영 MySQL: `mysql:8.0.41`, 실행 인자와 설정 파일에 `sql_mode` 지정 없음 → 서버 기본값(`STRICT_TRANS_TABLES` 포함). 레포 설정에도 덮어쓰기 없음
- 미확인: GitHub 시크릿 `SPRING_DATASOURCE_URL` 의 세션 `sql_mode` 덮어쓰기 여부(시크릿이라 열지 않음)
- 결론: 미확정. 서버 기본 설정과 dockerTest 재현을 합치면 유실 가능성이 높음
- 확정 방법: 운영 DB에서 7/19 이후 `notification_outbox` 의 `type = 'CHAT_MESSAGE'` 행 수와 `MAX(CHAR_LENGTH(dedup_key))` 조회(0행 = 유실 확정, 100자 행 = non-strict로 잘려 저장)
- 상세: `logs/ops-check.md`

## H2 중간 상태 (REQ-002)

- `@Column(length = 100)` 만 적용하고 옛 115자 키 유지 → 채팅 전송 경로에서 outbox 행 0개(`expected:<4> but was:<0>`), H2 `Value too long for column "DEDUP_KEY CHARACTER VARYING(100)"` 발생, 기존 #220 테스트도 같은 이유로 실패
- 키 축소 후 같은 테스트 통과 → 길이 가드가 채팅 경로에서 실제로 동작
- 상세: `logs/red-observations.md`

## gate 근거

- `agent-pipeline gate`(`./gradlew test build`, detekt와 Konsist 포함) exit 0, 커밋 `e7f6613` 기준, 테스트 866건 실패 0 건너뜀 0
- `./gradlew dockerTest` 전체 176건 실패 0 건너뜀 0(`d75b6a5` 기준), 이후 변경된 `ChatPushOutboxDockerTest` 단독 재실행 통과
- RED: 수정 전 코드에서 새 수용 테스트 실패 확인(`logs/red-observations.md`)

## 핵심 결정과 기각한 대안

- SEND 목적지 제한을 인터셉터 한 줄로 구현. 기각: Spring Security messaging(`@EnableWebSocketSecurity`) 도입. 목적지 권한을 선언형으로 관리하는 표준 방식이지만 의존성과 STOMP CSRF 처리가 새로 들어오고, 지금 필요한 규칙은 prefix 하나
- 키에서 방 uuid를 뺌. 메시지 uuid가 방을 이미 특정하므로 유일성 손실 없음. 기각: 컬럼을 늘리는 마이그레이션(운영 스키마 변경 비용, 키를 줄이면 불필요)
- 거절 오류 코드는 기존 `INVALID_INPUT_VALUE`, `UNAUTHENTICATED` 재사용. 기각: 새 `ErrorCode`(정상 앱은 이 경로에 닿지 않아 앱 분기 가치 없음)
- `SimpUserRegistry` 가 비어 있는 원인(인터셉터가 `wrap()` 사본에 user 설정)은 고치지 않음. 현재 기능은 세션 id 기반이라 영향이 없고, 고치면 이름 기반 `/user/{uuid}` 경로가 열려 범위가 커짐

## 남은 후속 작업

- (사람) 운영 DB에서 `CHAT_MESSAGE` outbox 행 조회로 #293 유실 확정 여부 결정. 유실이 확정되면 팀 공유 필요(재발송은 범위 밖으로 결정)
- (사람) 노션 작업 245 상태와 TODO 갱신(GitHub 이슈 #293~#295는 PR 머지로 자동 종료)
- 배포 후 운영 로그에서 채팅 푸시 outbox 저장(`Notification queued: type=CHAT_MESSAGE`) 확인
- 다른 알림 키 `join_request:` 가 정확히 100자(epoch millis 13자리 기준). 지금은 맞지만 형식을 바꿀 때 길이 가드 테스트가 잡음
- `SimpUserRegistry` 등록을 고칠 일이 생기면 `chat.md` 기록 참고(이름 기반 유저 목적지가 열림, SEND 가드는 유지)
- agent-pipeline 이슈 damiannlee/agent-pipeline#106(`designer-launch --current-effort` 추측 전달로 불필요한 질문 발생)
