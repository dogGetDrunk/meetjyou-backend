# Spec

> 근거: 승인된 intent.md (PR #255). 요구사항은 intent 성공 기준을 그대로 쪼갠 것이며 범위를 넓히지 않는다.

## 요구사항

### REQ-001 채팅방 목록 안 읽은 수 단일 집계

- 요구사항
  - `ChatReadService.buildUnreadCountMap`의 방별 `countUnreadByRoomUuidAfterLastReadMessageId` 루프 + `countUnreadByRoomUuidsWithoutLastReadMessageId`를 집계 쿼리 1개로 교체
  - 쿼리: `ChatMessage` ⋈ `ChatRoom` ⋈ 요청자 `UserParty`(같은 party, `memberStatus = JOINED`), `cm.sender.uuid <> :requesterUuid`, `up.lastReadMessageId is null or cm.id > up.lastReadMessageId`, `group by cm.room.uuid`
  - 기존 두 쿼리 메서드는 다른 호출부가 없으면 삭제
  - 조인 동등성 근거: 목록 방 = 요청자 JOINED 멤버십(`findAllWithPartyByUserUuidAndMemberStatus(JOINED)`)의 party에서 도출, `uk_user_party(user_id, party_id)`(V8)로 (요청자, party)당 row 1개 → 요청자 JOINED 조인이 방 집합을 바꾸거나 중복 집계하지 않음 (C-r1-07)
- 수용 기준
  - 방 수와 무관하게 안 읽은 수 집계 쿼리 정확히 1회 (방 0개면 0회) — 단위(Repository 호출) + 통합(실제 SQL: `chat_message` 대상 SELECT 수, fixture 저장·flush·clear 이후부터 측정) 양쪽으로 고정 (C-r1-05)
  - 결과 값이 기존 로직과 동일: 마지막 읽은 메시지 없음 = 타인 메시지 전체, 있음 = 그 id 초과 타인 메시지, 메시지 없는 방 = 0
  - 다른 방·다른 유저 멤버십이 섞여도 요청자 기준으로만 집계
  - 테스트 데이터: 방 여러 개 + 서로 다른 lastReadMessageId, 커서 없음, 자기 메시지, 빈 방, 다른 유저 멤버십·목록 밖 방의 메시지 포함
- 수용 테스트 ID: AT-001-1(단위, 호출 1회), AT-001-2(통합, 값 일치 + 실제 SQL 1회·방 0개 0회)

### REQ-002 게시글 상세 커넥션 1개

- 요구사항
  - 상세 조회의 읽기 트랜잭션이 끝난 뒤(커넥션 반납 후) 조회수 증가 실행
  - `PostService.getPostByUuid`: 트랜잭션 없는 조정 메서드 — 읽기 부분은 `readOnly` 트랜잭션(`TransactionTemplate` readOnly 또는 별도 빈, 선례 `NotificationDispatcher`), 그다음 `postViewService.incrementIfEligible`
  - `incrementIfEligible`의 `REQUIRES_NEW` → 기본 `@Transactional`(바깥 트랜잭션 없으면 새로 시작, 바깥이 생기면 합류해 2커넥션 재발 방지)
  - 읽기 경계 안에서 조회·연관 로딩·응답 DTO 생성까지 완료, 증가 전 `views`를 담은 DTO를 들고 경계를 나온 뒤 조회수 증가 (C-r1-04)
  - 계약 범위: 현재 호출 경로(`PostController` → `PostService.getPostByUuid`, 바깥 트랜잭션 없음, `PostService` 클래스 수준 `@Transactional` 없음)에 한정 — 상위 트랜잭션 안에서의 호출은 지원하지 않음
  - 조회수 증가 실패는 지금처럼 WARN 로그 후 응답 정상
- 수용 기준
  - Hikari `maximum-pool-size=1` 환경에서 상세 조회 성공 + 조회수 1 증가 (현행 코드는 안쪽 커넥션 대기 → 타임아웃 → 조회수 미증가로 실패)
  - 같은 유저 10분 내 재조회 시 미증가, 비로그인(유저 없음) 시 미증가, 응답 `views` 값 의미 동일(증가 전 값)
  - 조회수 증가 중 예외 → 상세 응답 정상
  - 테스트 조건: 테스트 본문을 트랜잭션으로 감싸지 않음, 풀 1개 테스트는 실제 상세 조회·DTO 변환·조회수 커밋까지 수행
- 수용 테스트 ID: AT-002-1(통합, 풀 1), AT-002-2(통합, 규칙 유지), AT-002-3(통합, 증가 트랜잭션 실제 롤백 후 응답 정상)

### REQ-003 브로드캐스트 수신자 일괄 필터

- 요구사항
  - `UserNotificationPreferenceRepository`에 타입별 `enabled = false` 유저 id 일괄 조회 추가
  - 공지·약관 재동의 발송이 대상 유저 목록 + 비활성 id 집합으로 메모리 필터 (`user.notified = false` 제외, 공지 `critical`은 설정 무시)
  - 유저별 `NotificationPreferenceService.isEnabled` 호출 제거 (메서드 자체는 다른 호출부 유지)
- 수용 기준
  - 대상 유저 수와 무관하게 설정 조회 1회 (`critical` 공지면 0회 허용) — 단위 + 통합(실제 SQL: `user_notification_preference` 대상 SELECT 수, 후보 1명·다수 동일) (C-r1-05)
  - 수신자 집합이 기존과 동일: NORMAL만, `notified=false` 제외, 해당 타입 끈 유저 제외(다른 타입 끈 건 무관), 설정 row 없음 = 수신
- 수용 테스트 ID: AT-003-1(단위, 조회 1회), AT-003-2(통합, 수신자 집합 + 실제 SQL 1회, 공지·약관 각각)

### REQ-004 브로드캐스트 실패 처리와 중복 방지

- 요구사항
  - 발송 본체를 별도 빈(`NoticeBroadcaster`·`TermsReconsentBroadcaster` 류)의 `REQUIRES_NEW` 메서드 하나로 분리: 원천 row 잠금 → 기발송 확인 → 수신자 조회 → outbox 저장, 전부 한 트랜잭션
    - 원천 row = 공지 `notice`·약관 `terms` row, `PESSIMISTIC_WRITE` (선례 `PostRowLock`)
    - 기발송 = dedup 접두(`notice:<uuid>:`·`terms_reconsent:<uuid>:`) outbox row 존재
  - 이벤트 핸들러: `@TransactionalEventListener(AFTER_COMMIT)` 유지, 핸들러 메서드 자체는 트랜잭션 없음 → 발송 빈 호출을 try/catch
    - 이유: 같은 트랜잭션 메서드 안에서 catch하면 rollback-only 표시 후 커밋 시 `UnexpectedRollbackException`이 발행 요청으로 다시 전파
    - 실패: ERROR 로그 + `DiscordAlertService.sendOperationalAlert`(uuid, 예외 클래스, "0건 발송", 재발송 API 경로) → 발행 API는 정상 응답
    - catch 범위 = 발송 빈 프록시 호출 전체(커밋 시점 예외 포함)
    - 알림 `source`에 대상 uuid 포함 (예: `NoticeBroadcast:<uuid>`) — `DiscordAlertService` dedup 키가 `status:exceptionClass:source`라 uuid 없으면 같은 예외의 다른 공지 실패 알림이 dedup 창 안에서 누락
    - 알림 예외 격리: `deliver`가 webhook 전송을 `CompletableFuture.runAsync` 안에서 try/catch → 전송 실패는 호출부로 전파되지 않음, 별도 격리 불필요 (C-r1-02)
    - 기발송 상태(재발송이 먼저 끝남): INFO 로그 후 건너뜀
    - 공지가 그사이 삭제됨: INFO 로그 후 건너뜀
  - 수용(변경 안 함): AFTER_COMMIT 시점엔 원 트랜잭션 커넥션이 아직 반납 전 → 발송 중 커넥션 2개 점유는 현행과 같음. 관리자 발행 빈도라 REQ-002와 달리 풀 고갈 위험 없음
  - outbox `dedup_key`에 비유니크 인덱스 추가(V43) — 기발송 확인 `LIKE 'prefix%'`가 무제한 증가 테이블 풀스캔 되지 않도록
- 수용 기준
  - 발송 중 예외 → outbox 0건, ERROR 로그, Discord 운영 알림 호출 1회, 공지 생성·약관 발행 API 성공 응답, 공지·약관 row는 커밋됨 (C-r1-03)
    - 실제 API 호출로 원 트랜잭션 커밋·AFTER_COMMIT 경유 (핸들러 직접 호출·테스트 트랜잭션 롤백 금지)
    - 수신자 2명 이상, 첫 outbox insert가 flush된 뒤 후속 저장에서 실패 주입 → 이미 저장된 row까지 전량 롤백 확인
    - 원천 row 존재·outbox 0건은 별도 새 트랜잭션에서 조회
  - 기발송 상태에서 핸들러 재실행 → outbox 추가 0건
  - 정상 경로 수신자·outbox 내용(title·body·dataJson·dedupKey)은 기존과 동일
- 수용 테스트 ID: AT-004-1(통합, 공지 실패), AT-004-2(통합, 약관 실패), AT-004-3(단위, 기발송 건너뜀)

### REQ-005 관리자 재발송 API

- 요구사항
  - `POST /api/v1/notices/{uuid}/broadcast`, 바디 `{ "critical": Boolean = false }` → REQ-004 발송 빈 재사용
  - `POST /api/v1/terms/{termsUuid}/reconsent-broadcast` → REQ-004 발송 빈 재사용
    - 허용: 대상 약관 `status = ACTIVE` + `effectiveAt <= now`(terms 규칙의 현재 유효 약관) + 같은 type의 `INACTIVE` 약관 존재(= 이전 버전 대체) (C-r1-01)
    - 적격성 판정은 발송 트랜잭션 안, 약관 row 잠금 후 수행
    - 원 발송(약관 발행 시)은 `effectiveAt`과 무관하게 지금처럼 발송 — 변경 없음
  - 둘 다 `@PreAuthorize("hasAuthority('ADMIN')")`, 서비스가 응답 DTO `.of()` 반환, 바깥 트랜잭션 없이 발송 빈 호출(커넥션 1개)
  - 수신자는 재발송 시점 기준으로 다시 계산 (원 발송 이후 가입·설정 변경 반영) — 의도된 동작
- 수용 기준
  - outbox 0건 상태 → 200 + `recipientCount`, 원 발송과 같은 규칙의 수신자에게 outbox 생성
  - 기발송 상태 → 409 `BROADCAST_ALREADY_SENT`, outbox 변화 없음
  - 약관이 재동의 대상 아님(DRAFT·INACTIVE·첫 버전·`effectiveAt` 미래) → 409 `TERMS_RECONSENT_NOT_APPLICABLE`, outbox 0건 (`effectiveAt` = now 경계는 허용)
  - 없는 공지·약관 → 기존 404 (`NOTICE_NOT_FOUND`·`TERMS_NOT_FOUND`), 두 API 각각
  - 비관리자 → 403, 두 API 각각
  - 동시 재발송 2건 → outbox 중복 없음(한쪽 409 — `BROADCAST_ALREADY_SENT` 또는 잠금 대기 초과 시 `CONCURRENT_REQUEST_CONFLICT`)
  - 원 발송 진행 중 재발송 → outbox 중복 없음
- 동시성 테스트 공통 (C-r1-06)
  - 공지·약관 각각 실행 (2 경로 × 2 시나리오)
  - 선행 트랜잭션이 원천 row 잠금 획득 후 outbox insert 직전 정지(`SqlPausePoint`), 경쟁 요청의 잠금 대기는 `performance_schema.data_lock_waits` 폴링으로 확인 — 고정 sleep 금지
  - 결과: 전체 수신자 outbox 생성은 1회만, 나머지는 지정 409, outbox 중복 0
- 수용 테스트 ID: AT-005-1(통합, 공지 재발송 200·409·404·403), AT-005-2(통합, 약관 재발송 200·적용 불가 409·404·403), AT-005-3(dockerTest, 동시 재발송 — 공지·약관), AT-005-4(dockerTest, 원 발송 중 재발송 — 공지·약관, 원 발송은 실제 AFTER_COMMIT 핸들러 경유)

## 확정 인터페이스

### API 시그니처·DTO

```kotlin
// NoticeController (기존 @RequestMapping("/notices"))
@PreAuthorize("hasAuthority('ADMIN')")
@PostMapping("/{uuid}/broadcast")
fun resendBroadcast(@PathVariable uuid: UUID, @RequestBody request: ResendNoticeBroadcastRequest): BroadcastResultResponse

// TermsController (기존 @RequestMapping("/terms"))
@PreAuthorize("hasAuthority('ADMIN')")
@PostMapping("/{termsUuid}/reconsent-broadcast")
fun resendReconsentBroadcast(@PathVariable termsUuid: UUID): BroadcastResultResponse

data class ResendNoticeBroadcastRequest(val critical: Boolean = false)
data class BroadcastResultResponse(val recipientCount: Int) { companion object { fun of(count: Int) } }

// ErrorCode 추가 (ErrorCodeStatusTest 표에 함께 추가)
BROADCAST_ALREADY_SENT(HttpStatus.CONFLICT, "Broadcast already sent")
TERMS_RECONSENT_NOT_APPLICABLE(HttpStatus.CONFLICT, "Terms has no previous version to reconsent from")

// Repository 추가
UserNotificationPreferenceRepository.findDisabledUserIdsByType(type: NotificationType): Set<Long>   // 이름은 구현 재량
NotificationOutboxRepository.existsByDedupKeyStartingWith(prefix: String): Boolean
ChatMessageRepository.countUnreadByRoomUuids(roomUuids, requesterUuid): List<RoomUnreadCountProjection>
```

### 발송 빈·핸들러·예외 (테스트가 의존하는 계약, plan 단계 확정)

```kotlin
// package com.dogGetDrunk.meetjyou.notification.broadcast
@Service
class NoticeBroadcaster(
    private val noticeRepository: NoticeRepository,
    private val userRepository: UserRepository,
    private val preferenceRepository: UserNotificationPreferenceRepository,
    private val outboxRepository: NotificationOutboxRepository,
    private val templateFactory: NotificationTemplateFactory,
    private val objectMapper: ObjectMapper,
) {
    // 순서: noticeRepository.findByUuidForUpdate(uuid) ?: throw NoticeNotFoundException
    //   → outboxRepository.existsByDedupKeyStartingWith("notice:$uuid:") 이면 throw BroadcastAlreadySentException
    //   → userRepository.findAllByStatus(NORMAL) → critical=false면 preferenceRepository.findDisabledUserIdsByType(NOTICE) 1회
    //   → outboxRepository.saveAll(rows) → rows.size 반환
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun broadcast(noticeUuid: UUID, critical: Boolean): Int
}

@Service
class TermsReconsentBroadcaster(
    private val termsRepository: TermsRepository,
    private val userTermsRepository: UserTermsRepository,
    private val preferenceRepository: UserNotificationPreferenceRepository,
    private val outboxRepository: NotificationOutboxRepository,
    private val templateFactory: NotificationTemplateFactory,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),   // 선례 AppleClientSecretFactory, 단위 테스트는 고정 Clock 주입
) {
    // 공통: termsRepository.findByUuidForUpdate(uuid) ?: throw TermsNotFoundException
    //   → existsByDedupKeyStartingWith("terms_reconsent:$uuid:") 이면 throw BroadcastAlreadySentException
    //   → userTermsRepository.findLatestByTermsType(terms.type, AGREED) → NORMAL 필터 → findDisabledUserIdsByType(TERMS_RECONSENT) 1회
    //   → saveAll → rows.size 반환. title·body의 displayText는 잠근 Terms row에서 읽음
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun broadcastOnPublish(termsUuid: UUID): Int          // T-03, 적격성 검사 없음(현행 원 발송 의미)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun resend(termsUuid: UUID): Int                      // T-04, 잠금 직후 적격성: ACTIVE && effectiveAt <= clock.instant()
                                                          //   && termsRepository.existsByTypeAndStatus(type, INACTIVE), 아니면 throw TermsReconsentNotApplicableException
}

// Repository 추가 (잠금 = @Lock(LockModeType.PESSIMISTIC_WRITE) + @Query)
NoticeRepository.findByUuidForUpdate(uuid: UUID): Notice?
TermsRepository.findByUuidForUpdate(uuid: UUID): Terms?
TermsRepository.existsByTypeAndStatus(type: TermsType, status: TermsStatus): Boolean      // T-04
UserNotificationPreferenceRepository.findDisabledUserIdsByType(type: NotificationType): Set<Long>
NotificationOutboxRepository.existsByDedupKeyStartingWith(prefix: String): Boolean

// 예외
// common/exception/business/notification/BroadcastAlreadySentException.kt   (T-03)
class BroadcastAlreadySentException(dedupPrefix: String) : BusinessException(ErrorCode.BROADCAST_ALREADY_SENT, dedupPrefix)
// common/exception/business/terms/TermsReconsentNotApplicableException.kt     (T-04)
class TermsReconsentNotApplicableException(termsUuid: UUID) : BusinessException(ErrorCode.TERMS_RECONSENT_NOT_APPLICABLE, termsUuid.toString())

// 핸들러 (기존 클래스·패키지 유지, 생성자 교체, 메서드에 @Transactional 없음)
class NoticeBroadcastEventHandler(private val noticeBroadcaster: NoticeBroadcaster, private val discordAlertService: DiscordAlertService)
class TermsReconsentEventHandler(private val termsReconsentBroadcaster: TermsReconsentBroadcaster, private val discordAlertService: DiscordAlertService)
// on(event): try { broadcaster 호출 }
//   catch BroadcastAlreadySentException / NoticeNotFoundException(TermsNotFoundException) → INFO 로그, 알림 없음
//   catch Exception → ERROR 로그 + discordAlertService.sendOperationalAlert(
//       source = "NoticeBroadcast:<uuid>" | "TermsReconsentBroadcast:<uuid>",
//       exceptionClass = e.javaClass.simpleName,
//       summary 에 "0 sent" 와 "POST /api/v1/notices/<uuid>/broadcast" | "POST /api/v1/terms/<uuid>/reconsent-broadcast" 포함)

// 재발송 서비스 (T-04) — @Transactional 없음: 바깥 트랜잭션이 있으면 REQUIRES_NEW 발송이 커넥션 2개를 잡음
NoticeService.resendBroadcast(uuid: UUID, request: ResendNoticeBroadcastRequest): BroadcastResultResponse
TermsService.resendReconsentBroadcast(termsUuid: UUID): BroadcastResultResponse
// BroadcastResultResponse 위치: notification/broadcast/dto/
```

### 테스트 지원 계약

- 실패 주입·SQL 계수 = 각 테스트 파일 안에 정의한 Hibernate `StatementInspector` 구현 (top-level class + companion `ThreadLocal`·카운터, 선례 dockerTest `SqlPausePoint`)
  - 등록: `@SpringBootTest(properties = ["spring.jpa.properties.hibernate.session_factory.statement_inspector=<FQCN>"])`, `src/test/resources` 변경 없음
  - outbox 실패 주입: 무장한 스레드에서 N번째(기본 2번째) `insert into notification_outbox` 문에서 `RuntimeException` → 첫 row는 실제 insert된 뒤 실패, 구현의 save/saveAll 선택과 무관
- 커밋 시점 예외(`UnexpectedRollbackException` 등) 격리는 핸들러 단위 테스트로 고정 (발송 빈 mock이 해당 예외 throw → 핸들러 비전파 + 알림 1회) — 통합 테스트의 beforeCommit 주입 변형은 두지 않음
- 시간 경계(`effectiveAt == now`)는 `TermsReconsentBroadcaster` 단위 테스트에서 고정 `Clock`으로, 통합 테스트는 now±1h만

### DB 스키마

- `V43__add_notification_outbox_dedup_key_index.sql`: `CREATE INDEX idx_notification_outbox_dedup_key ON notification_outbox (dedup_key);`
  - 비유니크 — 기존 데이터 중복 여부와 무관하게 적용 가능, MySQL 8 online DDL
  - 기각: 유니크 인덱스 — 운영 기존 중복 미확인 상태에서 migration 실패 시 배포 기동 실패, 행 잠금으로 이미 충분

## 검증 (C-r1-08)

- 티켓별 완료 증거: `./gradlew test build` + `./gradlew dockerTest` 실행 출력
- 로컬 Docker 미가동이면 dockerTest 미실행을 통과로 보고하지 않음 — PR CI(`./gradlew build dockerTest`, `.github/workflows/ci.yml`)가 같은 HEAD에서 통과한 것을 증거로 사용, 머지는 CI 통과 후
- V43 적용·인덱스 존재는 MySQL(Testcontainers) 경로에서 확인 — H2·mock은 migration 증거로 불인정

## 마일스톤

1. T-01 채팅방 목록 단일 집계 (REQ-001) — 독립
2. T-02 게시글 상세 커넥션 1개 (REQ-002) — 독립
3. T-03 브로드캐스트 일괄 필터·실패 처리·중복 방지 (REQ-003, REQ-004) — 독립
4. T-04 관리자 재발송 API (REQ-005) — T-03 의존
