---
id: T-20261107-review-low-items-02
title: 예약 작업 예외 Discord 알림 + FCM 타임아웃·발송 기록 실패 격리
tier: high
effort_override: null
depends_on: []
requirements: [REQ-003, REQ-004]
acceptance_tests: [AT-003-1, AT-003-2, AT-004-1, AT-004-2]
touches:
  - src/main/java/com/dogGetDrunk/meetjyou/config/ScheduleConfig.kt
  - src/main/java/com/dogGetDrunk/meetjyou/config/ScheduledTaskErrorHandler.kt
  - src/main/java/com/dogGetDrunk/meetjyou/notification/sender/FirebaseConfig.kt
  - src/main/java/com/dogGetDrunk/meetjyou/notification/dispatcher/NotificationDispatcher.kt
consumes:
  - DiscordAlertService.sendOperationalAlert(source, exceptionClass, summary, detail)
  - org.springframework.scheduling.annotation.SchedulingConfigurer
  - org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
  - org.springframework.util.ErrorHandler
  - NotificationOutboxRepository.updateResult
  - PushTokenRepository.deactivateAllByIdIn
  - com.google.firebase.FirebaseOptions.Builder.setConnectTimeout/setReadTimeout/setWriteTimeout
produces:
  - 'ScheduledTaskErrorHandler(discordAlertService: DiscordAlertService) : org.springframework.util.ErrorHandler (config 패키지, @Component 또는 ScheduleConfig @Bean)'
  - 'ScheduleConfig : SchedulingConfigurer — @Bean scheduledTaskScheduler(): ThreadPoolTaskScheduler (poolSize 상수 2, threadNamePrefix "scheduled-", errorHandler), configureTasks에서 registrar.setTaskScheduler(그 빈)'
  - 'FirebaseConfig.buildFirebaseOptions(credentials: GoogleCredentials): FirebaseOptions (public, 타임아웃 상수 10,000ms 3종)'
  - 'NotificationDispatcher.dispatchBatch: 항목별 기록 실패 격리, deactivateTokens 항상 실행'
status: merged
---

## 목표

- 어떤 `@Scheduled` 작업이든 예외 시 Discord 운영 알림 1건(작업별 dedup)
- FCM 호출에 유한 타임아웃, 결과 기록 실패 한 건이 같은 배치의 나머지·무효 토큰 비활성화를 끊지 않음

## 구현 범위

- 할 것
  - `ScheduledTaskErrorHandler.handleError(t)`: ERROR 로그(스택 포함) → `sendOperationalAlert(source, exceptionClass = t::class.java.simpleName, summary = 메시지 요약)`, 다시 던지지 않음
    - `source` = `"ScheduledTask:<simpleName>.<method>"` — `t.stackTrace`에서 `com.dogGetDrunk.meetjyou` 로 시작하는 첫 프레임(클래스명은 마지막 `.` 뒤), 없으면 `"ScheduledTask"`. 접두 패키지·`"ScheduledTask"`는 상수
    - summary: 메시지 null이면 클래스명 등 non-null 대체, 길이 상한은 상수
  - `ScheduleConfig`: `SchedulingConfigurer` 구현 + `@Bean` `scheduledTaskScheduler`(`ThreadPoolTaskScheduler`, 스레드 수 상수 2, 접두 `scheduled-`, `setErrorHandler(scheduledTaskErrorHandler)`), `configureTasks`에서 `registrar.setTaskScheduler(scheduledTaskScheduler)`
    - 근거: 실측상 Boot `taskScheduler`는 `messageBrokerTaskScheduler` 때문에 생성되지 않고 `@Scheduled`가 브로커 스케줄러에서 돎 → Customizer는 효과 없음 (spec REQ-003)
  - `FirebaseConfig`: `buildFirebaseOptions(credentials)` 분리, 연결·읽기·쓰기 타임아웃 상수 `10_000`ms. `GoogleCredentials.getApplicationDefault()`는 `firebaseApp()` 빈 메서드에 남김
  - `NotificationDispatcher.dispatchBatch`
    - `exhausted` DEAD 기록을 항목별 try/catch — 실패 시 ERROR 로그(outboxId) 후 다음 항목
    - 루프 catch 안 `recordRetry` 실패도 ERROR 로그(outboxId) 후 다음 항목
    - `deactivateTokens(invalidTokenIds)`는 루프 후 항상 실행(`items.isEmpty()` 조기 반환 경로 포함 여부는 기존과 동일 — 모인 토큰 없으면 호출 없음)
    - 30줄 상한 지키도록 `recordSafely` 류 private 헬퍼 추출
- 하지 말 것
  - `scheduledTaskScheduler` 외 새 `TaskScheduler`/`ScheduledExecutorService` 빈 생성 금지, `messageBrokerTaskScheduler`·`WebSocketConfig` 수정 금지, 스레드 수 2 외 값 금지
  - 대상 예약 작업 4개(`NotificationDispatcher.scheduledDispatch`·`reportDeadItems`, `PostViewService.cleanupOldViewLogs`, `WithdrawnUserPurgeScheduler.purgeExpiredWithdrawals`) 수정 금지
  - `dispatchBatch`·`scheduledDispatch`에 트랜잭션 추가 금지 (C-r1-07 전제)
  - 기존 `NotificationDispatcherTest`·`FcmNotificationSenderTest` 수정 금지

## 확인된 전제 (메인 설계, 2026-11-07 컨텍스트 실측)

- `TaskScheduler` 빈은 `messageBrokerTaskScheduler` 하나뿐, Boot `taskScheduler` 없음, `@Scheduled` 4개가 `MessageBroker-*` 스레드에서 실행 → 위 `SchedulingConfigurer` 방식으로 확정
- 두 `TaskScheduler` 빈이 생겨도 타입 주입하는 곳 없음(src/main 검색) — 모호성 오류가 나면 escalation

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/user/WithdrawnUserPurgeScheduler.kt` — `sendOperationalAlert` 호출 형태·상수
- `src/main/java/com/dogGetDrunk/meetjyou/config/ScheduleConfig.kt` — `@EnableScheduling`
- `src/test/java/com/dogGetDrunk/meetjyou/notification/dispatcher/NotificationDispatcherTest.kt` — 디스패처 mockk 구성
- `src/test/java/com/dogGetDrunk/meetjyou/auth/AuthSecurityReproIntegrationTest.kt` — `@SpringBootTest` + `@MockBean`

## 참조 파일 (읽기 허용)

- `src/main/java/com/dogGetDrunk/meetjyou/common/discord/DiscordAlertService.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/config/WebSocketConfig.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/user/WithdrawnUserPurgeScheduler.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/user/WithdrawnUserPurgeService.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/post/view/PostViewService.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/outbox/NotificationOutbox.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/outbox/NotificationOutboxRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/push/PushTokenRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/sender/PushNotificationSender.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/target/NotificationTargetResolver.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/target/PushTarget.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/notification/dispatcher/NotificationDispatcherTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/notification/sender/FcmNotificationSenderTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/common/discord/DiscordAlertServiceTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/user/WithdrawnUserPurgeSchedulerTest.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/auth/AuthSecurityReproIntegrationTest.kt`
- `src/test/resources/application-test.yml`
- `.claude/rules/notification.md`
- `.claude/rules/user.md`

## 관련 spec 발췌

- REQ-003: `SchedulingConfigurer` + 전용 `scheduledTaskScheduler`(스레드 2, `ErrorHandler`) — Boot `taskScheduler`는 브로커 스케줄러 때문에 없음(실측). ERROR 로그 + `sendOperationalAlert`, 미전파(LOG_AND_SUPPRESS + 알림). `source` = `"ScheduledTask:<클래스 simpleName>.<메서드>"`(앱 패키지 첫 프레임, 없으면 `"ScheduledTask"`) — dedup 키 `status:exceptionClass:location(=source)`(C-r1-05). 대상 4개 작업 수정 없음(C-r1-04), 스레드 수 운영 실효값 2 유지
- REQ-004: 연결·읽기·쓰기 10,000ms 상수, 옵션 생성부를 자격 증명 인자 함수로 분리. `recordRetry`·`exhausted` DEAD 기록 실패 → ERROR 로그(outboxId) 후 다음 항목, 루프 후 `deactivateTokens` 항상. 기록 실패 항목은 SENDING + 리스로 600초 뒤 재선점(추가 처리 없음). `updateResult`·`deactivateAllByIdIn`은 리포지토리 `@Transactional` 건별 커밋 — 배치 루프 트랜잭션 추가 금지(C-r1-07)

## 분류 근거

- 스케줄러 공통 오류 처리는 모든 예약 작업에 영향, 디스패처는 outbox 리스·재시도 상태와 트랜잭션 경계 전제에 의존 → State/concurrency high → high
- REQ-003·004는 코드상 독립이나 spec 마일스톤이 묶음(T-02), 같은 "예약 작업이 멈추거나 조용히 실패" 주제·touches 4개로 작음
- effort_override 없음

## 실행 계획·DAG

- 선행 없음, 다른 티켓과 병렬. touches 교집합 없음

## 검증·증거 담당

- AT-003-1: `./gradlew test --tests "*ScheduledTaskErrorHandlerTest"`
- AT-003-2: `./gradlew test --tests "*ScheduledTaskAlertIntegrationTest"`
- AT-004-1: `./gradlew test --tests "*FirebaseConfigTimeoutTest"`
- AT-004-2: `./gradlew test --tests "*NotificationDispatcherRecordFailureTest"` + 기존 `*NotificationDispatcherTest`
- 구현 워커: 보호 테스트 불변, 최종 SHA에서 `./gradlew test build` 실제 출력 제출
