# Spec

> 근거: 승인된 intent.md (PR #272). 요구사항은 intent 포함 범위 1~7을 그대로 쪼갠 것이며 범위를 넓히지 않는다.
> 기준 코드: `origin/main` `d500baa` 이후.

## 요구사항

### REQ-001 공개 GET에 실패한 토큰이 붙어도 인증 없이 통과

- 요구사항
  - 대상 = SecurityConfig "Public read-only" 5개: `GET /api/v1/notices/**`, `GET /api/v1/terms/**`, `GET /api/v1/*/versions/check`, `GET /api/v1/*/versions/latest`, `GET /api/v1/users/is-duplicate-nickname`
  - 이 목록은 한 곳(`config`의 공개 엔드포인트 객체)에 **HTTP 메서드 + 패턴 `RequestMatcher` 목록**으로 두고, `SecurityConfig.permitAll`과 `JwtAuthFilter` 모두 그 객체의 매처(`matches(request)`)만 사용 — 문자열만 공유하면 매칭 방식(`*` 한 세그먼트, `**` 0개 이상)이 갈릴 수 있음 (C-r1-02)
  - `JwtAuthFilter.doFilterInternal`: 지금 오류 응답을 쓰고 체인을 끊는 분기는 `catch (e: BusinessException)` 하나뿐 — 만료·서명·형식 오류(`CustomJwtException` 하위), 토큰 타입 불일치(refresh 토큰을 Bearer로), 탈퇴·미존재 유저(`UserWithdrawnException`)가 모두 `BusinessException` 하위로 이 분기에 옴. 이 분기에서 (C-r1-01)
    - 대상 공개 요청 → 응답을 쓰지 않고 `SecurityContext`를 비운 채 다음 필터로 진행 (INFO 로그: 경로·`errorCode`, 토큰 값 금지)
    - 그 외 요청 → 지금처럼 `ErrorCode` 상태로 응답 (401 `EXPIRED_TOKEN` 등)
  - 유효 토큰이면 공개 요청에서도 지금처럼 인증 정보 설정
  - 제외: `/auth/**`(이미 필터 생략), 인프라 경로(`/ws-chat` 등), 대상 경로의 GET 외 메서드
- 수용 기준
  - 대상 5개 각각: 만료 토큰 / 서명 불일치 토큰 / 형식 오류 문자열 / 탈퇴 유저의 유효 토큰 / Bearer에 refresh 토큰 → 토큰 없이 보낸 요청과 같은 상태 코드(200, 닉네임 중복은 쿼리 파라미터 필요)
  - 매칭 경계: `*` 세그먼트는 플랫폼 값 2종(`ios`·`android`, `Platform` enum `IOS`·`ANDROID`) 모두, `/api/v1/notices`(하위 경로 없음)와 `/api/v1/notices/{uuid}` 모두 포함
  - 비공개 API(예: `GET /api/v1/users/me` 또는 기존 테스트가 쓰는 인증 필요 GET)에 만료 토큰 → 401 `EXPIRED_TOKEN`, 형식 오류 → 401 `INVALID_JWT` (기존 유지)
  - 대상 경로라도 POST 등 GET 외 메서드는 기존 동작 유지
  - 공개 GET + 유효 토큰 → 200, 필터가 인증 정보를 설정함 (단위 테스트에서 `SecurityContext` 확인)
- 수용 테스트 ID: AT-001-1(단위, `JwtAuthFilter`), AT-001-2(통합, MockMvc 실제 필터 체인)

### REQ-002 `userUuid` 클레임 형식 오류 → 401

- 요구사항
  - `JwtProvider.getUserUuid`: 클레임이 없거나 UUID로 파싱되지 않으면 `InvalidJwtException` (401 `INVALID_JWT`), WARN 로그(클레임 값 금지)
  - 호출부 3곳(`JwtAuthFilter`, `ChatStompInterceptor`, `UserAuthService.refreshToken`)은 수정 불필요 — 모두 `CustomJwtException`/`BusinessException` 경로로 처리됨
  - 같은 티켓에서 `JwtProvider.getExpiration` 삭제 (REQ-007 대상이지만 같은 파일 인접 줄이라 충돌 방지)
- 수용 기준
  - 같은 서명 키로 서명한 access 토큰에 `userUuid`가 `"not-a-uuid"` / 누락 → `getUserUuid`가 `InvalidJwtException` (`IllegalArgumentException` 아님)
  - 그 토큰으로 비공개 API 호출 → 401 `INVALID_JWT` (500 아님)
  - 정상 토큰 → 기존과 같은 UUID
  - `JwtProvider`에 `getExpiration` 메서드 없음 (리플렉션, C-r1-03)
- 수용 테스트 ID: AT-002-1(단위, `JwtProvider`), AT-002-2(통합, 비공개 API 401)

### REQ-003 예약 작업 예외 → Discord 운영 알림

- 요구사항
  - 확인된 현재 상태 (2026-11-07 컨텍스트 실측): `@EnableWebSocketMessageBroker`가 만드는 `messageBrokerTaskScheduler`가 유일한 `TaskScheduler` 빈 → Boot 자동 구성 `taskScheduler`는 생성되지 않음(`@ConditionalOnMissingBean`), `@Scheduled` 4개는 브로커 스케줄러(스레드 수 = CPU 수, 운영 t4g.small 2개, STOMP 하트비트와 공유)에서 실행. 따라서 `ThreadPoolTaskSchedulerCustomizer`는 효과 없음
  - `ScheduleConfig`가 `SchedulingConfigurer`를 구현해 `@Scheduled` 전용 `ThreadPoolTaskScheduler` 빈(이름 `scheduledTaskScheduler`)을 등록 스케줄러로 지정
    - 스레드 수 상수 2 (운영 현재 실효값 유지), 스레드 이름 접두 `scheduled-`, `ErrorHandler` = 아래 핸들러
    - 빈으로 등록해 컨텍스트 종료 시 정리되게 함. 브로커 스케줄러는 건드리지 않음
    - 기각: 브로커 스케줄러에 `ErrorHandler` 직접 지정(웹소켓 설정에 결합, 예약 작업이 계속 STOMP 하트비트와 스레드 공유), `taskScheduler` 이름 빈 추가로 암묵 선택(선택 규칙이 빈 이름 관례에 숨음)
  - `ErrorHandler`: ERROR 로그(스택 포함) + `DiscordAlertService.sendOperationalAlert(source, exceptionClass = 예외 클래스 simpleName, summary = 예외 메시지 요약)`, 예외를 다시 던지지 않음 — 반복 작업이 다음 주기에도 계속 실행돼야 함 (Spring 기본 LOG_AND_SUPPRESS와 같은 동작 + 알림)
  - `source` = `"ScheduledTask:<클래스 simpleName>.<메서드>"` — 스택 트레이스에서 `com.dogGetDrunk.meetjyou` 패키지 첫 프레임 기준, 없으면 `"ScheduledTask"`. 근거: dedup 키가 `status:exceptionClass:location(=source)`이라 source가 작업마다 다르지 않으면 2초 주기 발송 작업의 반복 실패가 같은 예외 클래스의 04:00 탈퇴 정리 실패 알림을 dedup 창 안에서 삼킴 (C-r1-05)
  - 알림 중복 억제는 기존 `DiscordAlertService` dedup 창에 맡김 (같은 작업·같은 예외는 창 안에서 1건)
  - 대상 작업 4개(`NotificationDispatcher.scheduledDispatch`·`reportDeadItems`, `PostViewService.cleanupOldViewLogs`, `WithdrawnUserPurgeScheduler.purgeExpiredWithdrawals`)는 수정 없음 — 4개 모두 예외를 안에서 잡지 않고 스케줄러로 전파함을 확인 (`purge()` 호출은 `WithdrawnUserPurgeScheduler` 50행, catch 없음, C-r1-04)
  - 예약 작업 스레드 수: 운영 실효값 2 유지 (로컬 개발 머신은 CPU 수 → 2로 줄어듦, 영향 없음)
- 수용 기준
  - `ErrorHandler` 단위: 예외 전달 시 `sendOperationalAlert` 1회(위 인자), 예외 미전파
  - `ErrorHandler` 단위: 앱 패키지 프레임이 있는 예외 → `source`에 그 클래스·메서드, 없는 예외 → `"ScheduledTask"`
  - 통합: `@Scheduled` 처리기(`ScheduledAnnotationBeanPostProcessor`/`ScheduledTaskHolder`)가 등록한 작업이 쓰는 스케줄러 = `scheduledTaskScheduler` 빈(동일 인스턴스), `messageBrokerTaskScheduler` 아님 (C-r1-06)
  - 통합: 앱 컨텍스트의 `scheduledTaskScheduler`로 예외를 던지는 작업을 실행 → `DiscordAlertService` 운영 알림 호출, 같은 스케줄러로 다음 작업 정상 실행
  - 통합: `WithdrawnUserPurgeService.purge`가 예외를 던지게 한 뒤 `WithdrawnUserPurgeScheduler.purgeExpiredWithdrawals`를 스케줄러 경로로 실행 → 운영 알림 호출
  - 테스트 컨텍스트에서도 스케줄링이 켜져 있어 다른 예약 작업이 같은 mock을 부를 수 있음 → 알림 검증은 총 횟수가 아니라 주입한 예외의 클래스·메시지 인자 매칭. 빈 mock은 기존 테스트 패턴(`@MockBean`/`@MockitoBean`)을 따름
- 수용 테스트 ID: AT-003-1(단위), AT-003-2(통합)

### REQ-004 FCM 타임아웃 + 결과 기록 실패가 배치를 끊지 않음

- 요구사항
  - `FirebaseConfig`: `FirebaseOptions`에 연결·읽기·쓰기 타임아웃 설정, 상수(밀리초) — 연결 10,000 / 읽기 10,000 / 쓰기 10,000
    - 근거: SDK 기본값 0 = 무한(firebase-admin 9.5.0 `TimeoutInitializer`가 그대로 적용). 응답 없는 호출 하나가 발송 작업(`fixedDelay`)을 무기한 멈추고 예약 작업 스레드 2개 중 1개를 계속 점유
    - intent 정정: intent의 "스케줄러 스레드 1개 → 예약 작업 전부 정지"는 실측과 다름(실제 2개, 위 REQ-003 참고). 실제 영향 = 알림 발송 전체 정지 + 스레드 1개 점유. 범위·성공 기준은 그대로
    - 테스트 가능하게 옵션 생성부를 자격 증명을 인자로 받는 함수로 분리 (`GoogleCredentials.getApplicationDefault()` 호출은 빈 메서드에 남김)
  - `NotificationDispatcher.dispatchBatch`
    - catch 안 `recordRetry`가 예외를 던지면 ERROR 로그(outboxId)만 남기고 다음 항목 진행
    - 시도 한도 초과 항목(`exhausted`)의 DEAD 기록도 항목별로 같은 방식
    - 루프가 끝나면 `deactivateTokens`는 항상 실행
    - 기록 실패 항목은 SENDING + 리스로 남아 600초 뒤 재선점됨 (기존 경로, 추가 처리 없음)
    - 전제(확인됨, C-r1-07): `dispatchBatch`·`scheduledDispatch`에 트랜잭션 없음, `updateResult`·`deactivateAllByIdIn`은 각 리포지토리 메서드의 `@Transactional`로 건별 독립 커밋 → 한 건의 기록 예외가 다른 건을 rollback-only로 만들지 않음. 이 전제를 바꾸는 변경(배치 루프에 트랜잭션 추가) 금지
- 수용 기준
  - 옵션 생성 함수 결과의 `connectTimeout`·`readTimeout`·`writeTimeout`이 각 10,000
  - 배치 3건: 1번 처리 예외 + 1번 `recordRetry`의 `updateResult` 예외 → 2·3번 정상 처리·기록, 처리 중 모인 무효 토큰 비활성화(`deactivateAllByIdIn`) 호출
  - `exhausted` 항목 DEAD 기록 예외 → 나머지 항목 정상 처리
  - 기존 `NotificationDispatcherTest` 전부 통과
- 수용 테스트 ID: AT-004-1(단위, `FirebaseConfig` 옵션), AT-004-2(단위, `NotificationDispatcher`)

### REQ-005 마커 교체 직렬화 (plan 행 잠금)

- 요구사항
  - `PlanRepository`에 `PESSIMISTIC_WRITE` 잠금 조회 추가 (`@Lock` + uuid 조건)
  - `MarkerService.replaceMarkers`: 잠금 조회를 트랜잭션의 첫 DB 읽기로 사용 (그 전에 같은 트랜잭션에서 일반 조회 금지 — REPEATABLE READ 스냅샷이 잠금 대기 전에 잡히면 이전 마커를 읽음), 이후 소유자 검사·삭제·삽입은 그대로
  - `deleteAllByPlan`은 파생 delete 유지 (`MarkerRepository` 주석: bulk delete는 같은 plan insert와 갭 락 데드락)
  - 허용하는 경합: 같은 소유자가 plan 삭제와 마커 저장을 동시에 하면 잠금 순서가 반대라(삭제 = 마커 → plan, 교체 = plan → 마커) 데드락 가능 → 기존 409 `CONCURRENT_REQUEST_CONFLICT` 사후 처리. `.claude/rules/post-plan.md`에 한 줄 기록
- 수용 기준 (dockerTest, 실제 MySQL)
  - 보유 트랜잭션이 plan 행을 `SELECT ... FOR UPDATE`로 잡고 그 plan의 마커를 다른 집합 X로 교체(삭제 + 삽입, 미커밋)한 동안 `replaceMarkers`(집합 Y) 호출이 잠금 대기에 들어감 — `performance_schema.data_lock_waits` 폴링으로 확인 (`information_schema.innodb_trx.trx_state` 금지)
  - 보유 트랜잭션 커밋 후 요청 성공, 최종 마커 = Y와 정확히 같음(개수·내용), X 잔존 0 — 잠금 전에 일반 조회가 끼어 이전 스냅샷을 읽는 회귀도 이 케이스가 잡음 (C-r1-08)
  - 검증 실행: T-03 구현자는 `./gradlew dockerTest`(Docker 데몬 필요)를 직접 실행해 출력 첨부, 데몬이 없으면 완료가 아니라 blocking 보고. 엔진 gate(`./gradlew test build`)에는 dockerTest가 없지만 머지 전 CI 필수 체크 `build-and-test`가 `./gradlew build dockerTest`를 실행 (C-r1-09)
- 수용 테스트 ID: AT-005-1(dockerTest)

### REQ-006 앱 버전 캐시: 쓰기 전에 시작한 조회가 이전 값을 남기지 않음

- 요구사항
  - `AppVersionService.checkVersion`의 캐시 채우기: 쓰기(등록·수정·출시 확인 등 `summaryCache.remove`를 부르는 모든 메서드)가 끝난 뒤 캐시에 쓰기 이전 DB 값으로 만든 요약이 남지 않아야 함
    - 구현: `getOrPut` → `ConcurrentHashMap.computeIfAbsent`로 고정 (진행 중인 계산이 끝날 때까지 `remove`가 기다렸다가 지움). 캐시 키가 플랫폼 2개라 계산 중 bin 잠금 비용은 무시 가능. 기각: 세대 번호 비교(읽기 시작 전 세대 캡처 순서 실수 여지, 코드 증가) (C-r1-10)
    - 요약 계산의 DB 읽기는 전부 매핑 함수 안에서 실행, 매핑 함수 안에서 `summaryCache`를 다시 수정하지 않음
    - `checkVersion`은 `@Transactional` 없음(확인됨) → 계산 전에 스냅샷이 고정되지 않음. 이 메서드에 트랜잭션 추가 금지
  - 쓰기 메서드의 트랜잭션 구조는 바꾸지 않음 (지금은 `@Transactional` 없음 → `save` 커밋 후 `remove`). 바꾸면 무효화가 커밋 전으로 당겨져 같은 문제 재발
  - `/check` 응답 형식·캐시 키(플랫폼) 변경 없음
- 수용 기준 (단위, 리포지토리 mock, 스레드 2개)
  - 조회 스레드의 요약 계산이 리포지토리 mock 안에서 latch로 멈춘 상태(이전 버전 목록 반환 예정)에서, 쓰기 스레드가 쓰기 메서드를 호출하고 (끝나거나 `remove`에서 막힌 것을 스레드 상태로 확인한 뒤) latch 해제 → 두 스레드 종료 후 다음 `checkVersion`이 새 버전 목록 기준 결과를 반환
  - 순차 호출(쓰기 → 조회) 결과는 기존과 같음, 기존 `AppVersionServiceTest` 통과
- 수용 테스트 ID: AT-006-1(단위)

### REQ-007 미사용 public 메서드 삭제

- 요구사항 — 아래 12개 삭제 (`JwtProvider.getExpiration`은 REQ-002에서 삭제)
  - `PartyRepository`: `findAllByPlan_Uuid`, `existsByPlan_Uuid`
  - `UserPartyRepository`: `findAllByUser_Uuid`, `findAllByParty_Uuid`, `existsByParty_UuidAndUser_Uuid`, `existsByParty_UuidAndUser_UuidAndMemberStatus`
  - `UserPreferenceRepository`: `findPreferenceByUserIdAndType`, `findPreferencesByUserIdAndType`
  - `PreferenceRepository`: `findByName`
  - `ChatParticipantRepository`: `findAllByRoom_UuidAndUser_UuidIn`
  - `PushTokenRepository`: `findAllByUserIdAndActiveTrue`, `existsByUserIdAndToken`
  - 테스트에서만 쓰던 `findAllByPlan_Uuid`·`findAllByUser_Uuid`의 테스트 사용처(`PartyQueryOptimizationTest`, `PartyControllerTest` 등)는 남은 리포지토리 메서드나 `findAll()` 필터로 바꿈, 테스트가 검증하던 내용은 유지
  - 삭제 전 `src/main`·`src/test`·`src/dockerTest`·`src/gatling` 전체 재검색, 호출이 새로 생겼으면 그 메서드는 남기고 PR 본문에 기록
- 수용 기준
  - 위 12개 메서드가 각 인터페이스에 없음 (리플렉션으로 확인). 테스트 이름·주석에 "#231 정리 확인용, 같은 이름 메서드가 다시 필요하면 이 테스트 삭제" 명시 (C-r1-11)
  - `./gradlew build` 통과 (detekt·Konsist 포함)
- 수용 테스트 ID: AT-007-1(단위, 리플렉션)

## 확정 인터페이스

### API 시그니처·DTO

- 변경 없음. REQ-001은 에러 응답 대신 정상 응답이 나가는 동작 변경만

### DB 스키마

- 변경 없음 (migration 없음)

## 회귀 증명

- 결함 재현 AT — base에서 실패해야 함: AT-001-1·AT-001-2의 공개 GET + 실패 토큰 케이스, AT-002-1·AT-002-2의 형식 오류 클레임과 `getExpiration` 부재, AT-003-1·AT-003-2, AT-004-1, AT-004-2의 기록 예외 케이스, AT-005-1(잠금 대기 + 보유 트랜잭션 마커 잔존 0 — 잠금 누락과 잠금 전 일반 조회 회귀 모두), AT-006-1, AT-007-1
- 동작 유지 AT — base에서도 통과: 비공개 API 401 코드, 공개 GET 유효 토큰, 정상 `userUuid`, 기존 디스패처 경로, 순차 버전 조회

## 마일스톤

| 티켓 | 요구사항 | 모듈 |
|---|---|---|
| T-01 | REQ-001, REQ-002 (+ `getExpiration` 삭제) | auth, config |
| T-02 | REQ-003, REQ-004 | config, notification |
| T-03 | REQ-005 | plan |
| T-04 | REQ-006 | version |
| T-05 | REQ-007 (`JwtProvider` 제외) | party, userparty, preference, chat, notification |

- 티켓 간 의존 없음, 병렬 가능. 같은 파일을 건드리는 티켓 없음
