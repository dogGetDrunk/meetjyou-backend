---
id: T-20261103-chat-notice-post-perf-03
title: 브로드캐스트 일괄 필터·전량 롤백·중복 차단
tier: high
effort_override: null
depends_on: []
requirements: [REQ-003, REQ-004]
acceptance_tests: [AT-003-1, AT-003-2, AT-004-1, AT-004-2, AT-004-3]
touches:
  - 'src/main/java/com/dogGetDrunk/meetjyou/notification/'
  - 'src/main/java/com/dogGetDrunk/meetjyou/notice/'
  - 'src/main/java/com/dogGetDrunk/meetjyou/terms/'
  - 'src/main/java/com/dogGetDrunk/meetjyou/common/exception/'
  - 'src/test/java/com/dogGetDrunk/meetjyou/common/'
  - 'src/main/resources/db/migration/V43__add_notification_outbox_dedup_key_index.sql'
  - 'src/test/java/com/dogGetDrunk/meetjyou/notification/BroadcastPreferenceBatchTest.kt'
  - 'src/test/java/com/dogGetDrunk/meetjyou/notification/BroadcastPreferenceBatchIntegrationTest.kt'
  - 'src/test/java/com/dogGetDrunk/meetjyou/notification/NoticeBroadcastFailureIntegrationTest.kt'
  - 'src/test/java/com/dogGetDrunk/meetjyou/notification/TermsReconsentFailureIntegrationTest.kt'
  - 'src/test/java/com/dogGetDrunk/meetjyou/notification/BroadcastAlreadySentTest.kt'
  - 'src/dockerTest/java/com/dogGetDrunk/meetjyou/notification/NotificationOutboxDedupIndexDockerTest.kt'
consumes:
  - 'NoticeBroadcastEventHandler·TermsReconsentEventHandler: AFTER_COMMIT'
  - 'NotificationPreferenceService.isEnabled: 다른 호출부 유지'
  - 'UserNotificationPreferenceRepository'
  - 'NotificationOutboxRepository'
  - 'DiscordAlertService.sendOperationalAlert'
  - 'PostRowLock의 잠금+refresh 선례'
produces:
  - 'UserNotificationPreferenceRepository.findDisabledUserIdsByType(type: NotificationType): Set<Long>'
  - 'NotificationOutboxRepository.existsByDedupKeyStartingWith(prefix: String): Boolean'
  - 'NoticeBroadcaster.broadcast(noticeUuid: UUID, critical: Boolean): Int'
  - 'TermsReconsentBroadcaster.broadcast(termsUuid: UUID): Int'
  - '기발송 예외 → BROADCAST_ALREADY_SENT(409), 원천 없음 → 기존 404'
  - 'V43 비유니크 idx_notification_outbox_dedup_key'
status: in_progress
---

## 목표

공지·약관 발송의 설정 조회를 1회로 고정하고, 원천 row 잠금부터 outbox까지 단일 REQUIRES_NEW로 묶어 실패와 중복을 격리.

## 분류·결합도·DAG

- high: 다중 레이어·스키마·행 잠금·멱등성·트랜잭션 경계·실패 롤백(상 조건 다수)
- REQ-003과 REQ-004는 동일 핸들러·수신자/outbox 경로라 한 티켓으로 결합, 공지/약관 분할 시 공유 Repository 충돌 방지
- T-01·T-02와 독립, T-04의 단일 선행 티켓; ErrorCodeStatusTest는 common 테스트 모듈에서 해당 표만 변경; 실제 위치가 touches 밖이면 변경 전 메인 설계자에게 경로 escalation

## 구현 범위

- 포함: 타입별 disabled id 일괄 조회, NORMAL 후보에서 notified=false·동일 타입 disabled 제외, row 없음은 수신; critical 공지는 설정 무시하되 NORMAL/notified 규칙 유지
- 별도 NoticeBroadcaster·TermsReconsentBroadcaster의 public 프록시 메서드 1회 호출에 원천 row PESSIMISTIC_WRITE 잠금 → dedup 접두 존재 확인 → 수신자 재계산 → outbox 전량 저장·커밋 포함
- 반환 Int = 이번 호출에서 생성·커밋된 outbox 수; prefix는 notice:<uuid>: 또는 terms_reconsent:<uuid>:
- 기발송은 구별 가능한 BusinessException으로 BROADCAST_ALREADY_SENT(409); T-04의 API에서도 동일 의미 재사용, ErrorCodeStatusTest에 새 코드 표 반영
- 원천 없으면 기존 NOTICE_NOT_FOUND/TERMS_NOT_FOUND 예외; 핸들러는 기발송·원천 없음 INFO skip, 다른 발송 예외는 ERROR 및 운영 알림 1회
- AFTER_COMMIT 핸들러 메서드 자체는 트랜잭션 없음, broadcaster 프록시의 커밋 완료까지 try/catch; 같은 tx 내부 catch로 rollback-only 누출 금지
- 운영 알림: uuid·예외 클래스·0건 발송·재발송 API 경로, source에 uuid 포함(NoticeBroadcast:<uuid> 등); DiscordAlertService 자체 수정·추가 재시도·별도 전송 격리 제외
- 원 발송 약관은 effectiveAt 미래여도 기존처럼 발송; 재발송 적격성·컨트롤러는 T-04 범위
- V43: CREATE INDEX idx_notification_outbox_dedup_key ON notification_outbox (dedup_key); 유니크 금지, Testcontainers MySQL로 migration 적용·인덱스 확인
- 정상 title·body·dataJson·dedupKey·수신자 기존과 동일; 기존 IDENTITY 건별 outbox insert는 유지, Firebase push 직접 호출 금지
- 제외: 청크·비동기화·batch insert 변경, notify/critical 영속화, 발송 완료 플래그·부분 재발송 도입, 0명일 때 sentinel outbox 추가
- notification 규칙의 구형 핸들러 REQUIRES_NEW 위치 설명은 본 spec의 핸들러 비트랜잭션+별도 발송 빈 구조가 우선; 규칙 문서 수정은 본 카드 범위 아님

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/post/`의 PostRowLock: 원천 row 잠금·이미 로드된 엔티티 refresh 선례(읽기만)
- `src/main/java/com/dogGetDrunk/meetjyou/notification/`의 기존 두 핸들러: title/body/dataJson/dedupKey·NORMAL/notified 조건 원문 보존
- DiscordAlertService: uuid가 포함된 source로 기존 운영 알림 호출; 전송 예외 격리는 기존 서비스 책임

## 참조 파일 (읽기 허용)

- `AGENTS.md`
- `CLAUDE.md`
- `.pipeline/README.md`
- `.pipeline/project.yaml`
- `src/test/resources/` — 기존 test profile 설정만 읽기, 시크릿 접근·설정 변경 금지
- `src/main/java/com/dogGetDrunk/meetjyou/notification/` — 기존 핸들러·이벤트·outbox 엔티티/Repository·NotificationType·preference Service/Repository
- `src/main/java/com/dogGetDrunk/meetjyou/notice/` — Notice/Repository·생성 API·이벤트 발행 및 직접 DTO
- `src/main/java/com/dogGetDrunk/meetjyou/terms/` — Terms/Repository·발행 API·이벤트 발행 및 직접 DTO
- `src/main/java/com/dogGetDrunk/meetjyou/user/` — User/Repository의 NORMAL·notified 후보 선택
- `src/main/java/com/dogGetDrunk/meetjyou/post/` — PostRowLock 패턴만 읽기, 변경 금지
- `src/main/java/com/dogGetDrunk/meetjyou/common/` — BusinessException·ErrorCode·GlobalExceptionHandler·DiscordAlertService의 직접 관련 파일만, 나머지 탐색 금지
- `src/test/java/com/dogGetDrunk/meetjyou/` — ErrorCodeStatusTest 및 직접 사용 fixture만 경로 확인·읽기; ErrorCodeStatusTest 표에 BROADCAST_ALREADY_SENT 추가만 허용, 쓰기는 touches의 common 테스트 모듈 안으로 한정
- `src/dockerTest/java/com/dogGetDrunk/meetjyou/notification/` — outbox MySQL·Flyway 테스트 선례
- `src/main/resources/db/migration/` — notification_outbox 기존 DDL·V43 적용 순서만
- `.claude/rules/notification.md` — outbox 상태·AFTER_COMMIT·동기 push 금지
- `.claude/rules/terms.md` — 원 발송·유효 약관 정의
- `.claude/rules/user.md` — NORMAL·withdrawal fixture 불변식

## 확정 인터페이스 (spec 발췌, 테스트·구현 공통 계약)

#### 발송 빈·핸들러·예외 (테스트가 의존하는 계약, plan 단계 확정)

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

#### 테스트 지원 계약

- 실패 주입·SQL 계수 = 각 테스트 파일 안에 정의한 Hibernate `StatementInspector` 구현 (top-level class + companion `ThreadLocal`·카운터, 선례 dockerTest `SqlPausePoint`)
  - 등록: `@SpringBootTest(properties = ["spring.jpa.properties.hibernate.session_factory.statement_inspector=<FQCN>"])`, `src/test/resources` 변경 없음
  - outbox 실패 주입: 무장한 스레드에서 N번째(기본 2번째) `insert into notification_outbox` 문에서 `RuntimeException` → 첫 row는 실제 insert된 뒤 실패, 구현의 save/saveAll 선택과 무관
- 커밋 시점 예외(`UnexpectedRollbackException` 등) 격리는 핸들러 단위 테스트로 고정 (발송 빈 mock이 해당 예외 throw → 핸들러 비전파 + 알림 1회) — 통합 테스트의 beforeCommit 주입 변형은 두지 않음
- 시간 경계(`effectiveAt == now`)는 `TermsReconsentBroadcaster` 단위 테스트에서 고정 `Clock`으로, 통합 테스트는 now±1h만

## 관련 spec 발췌

- REQ-003: 일반 공지·약관 각각 후보 1명/다수에서 preference SELECT 1회, critical 공지는 0회 허용; Repository 호출·실제 SQL 양쪽 고정
- REQ-004: 단일 REQUIRES_NEW 발송 빈, 원천 row 잠금 후 dedup prefix 검사; AFTER_COMMIT 핸들러가 프록시 호출 전체 catch
- 실제 발행 API로 원천 tx 커밋·AFTER_COMMIT 진입; 수신자 2명 이상, 첫 insert flush 뒤 후속 실패 → 새 tx에서 원천 존재·outbox 0건·성공 HTTP·ERROR·운영 알림 1회 확인(C-r1-03)
- 정상 내용 불변, 기발송/공지 삭제 INFO skip; source에 uuid 포함(C-r1-02), 비동기 webhook 전송 자체 예외 주입은 기각된 추가 범위
- AFTER_COMMIT 원 tx 커넥션과 발송 tx의 2커넥션 점유는 수용; 이 경로 pool=1 요구 금지
- V43 비유니크 인덱스 MySQL 증거 필수(C-r1-08)

## 실행 순서

- [ ] AT-003·AT-004 보호 파일 복사, N회 설정 조회·발행 예외 누출 RED 확인
- [ ] disabled id bulk 조회 및 메모리 필터, 기존 outbox payload 유지
- [ ] 두 원천 잠금·prefix 검사·단일 REQUIRES_NEW 발송 빈 구현; 공유 BROADCAST_ALREADY_SENT 오류 추가
- [ ] 두 비트랜잭션 AFTER_COMMIT 핸들러에서 INFO skip·ERROR/uuid 운영 알림·프록시 commit 예외 catch 구현
- [ ] V43 비유니크 인덱스·MySQL 스키마 검증, ErrorCodeStatusTest 표 변경(common 테스트 모듈 안에서 위치 확인, 범위 밖이면 메인 설계자에게 경로·touches escalation)
- [ ] API 실제 commit·첫 insert flush 후 실패·새 tx 상태·정상 payload 검증 → 전체 test/build → dockerTest 증거 기록

## 실행·보호·완료 조건

- base_ref: `origin/main`; 이 카드의 범위만 변경, 승인된 intent 수정 금지
- test-writer가 선작성한 보호 파일을 태스크 `tests/code/<저장소 상대 경로>`에서 엔진 지시대로 복사; 보호 파일 수정 금지, 오류 의심 시 근거와 함께 escalation
- 구현 전 해당 AT 실패(RED), 구현 후 통과(GREEN) 확인; 애플리케이션 코드·테스트 코드 작성은 별도 역할
- 필수 gate: `./gradlew test build`; 추가 필수 증거: `./gradlew dockerTest`
- 로컬 Docker 미가동 시 dockerTest 미실행을 통과로 보고하지 않음; `.github/workflows/ci.yml`의 `./gradlew build dockerTest`가 동일한 불변 HEAD에서 통과한 증거로만 대체, 머지는 CI 통과 후
- 최종 HEAD·실행 명령·exit code·테스트 개수·실패/미실행 사유 기록; 코드 리뷰 의견으로 gate 결과 대체 금지
- 운영 배포·운영/공유 DB 접근·시크릿 접근 금지; 로컬 Testcontainers의 Flyway 적용만 허용
- 주석·로그 영어, 서비스 DTO `.of()`, CurrentUserProvider, `?: throw`, 명시적 트랜잭션, 반복문 Repository 조회 금지 등 CLAUDE.md 준수; 기존 IDENTITY outbox 건별 저장은 승인 spec 범위로 유지

## 입력·경로 한계

- 이 계획은 지정된 문서만으로 작성; 기존 애플리케이션 소스·테스트·CI 파일 미열람
- 아래 소스 디렉터리는 CLAUDE.md의 패키지 지도에 따른 모듈 읽기 범위; 세부 하위 파일 경로·기존 메서드의 전체 시그니처는 확인하지 않은 상태
- 다음 워커의 탐색 범위는 touches 및 참조 파일에 명시된 디렉터리 안의 열거 클래스·직접 의존 파일로 한정, 새 테스트 경로는 이 카드에서 지정
- 열거 클래스·직접 의존이 허용 범위에 없으면 추정 경로로 확장하지 않고 메인 설계자에게 누락 경로 escalation; 시크릿·자격증명 파일은 어떤 디렉터리 안에서도 제외
