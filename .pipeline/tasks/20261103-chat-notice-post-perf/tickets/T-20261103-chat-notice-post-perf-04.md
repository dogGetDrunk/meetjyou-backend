---
id: T-20261103-chat-notice-post-perf-04
title: 관리자 공지·약관 재발송 API와 MySQL 경쟁 검증
tier: high
effort_override: null
depends_on: [T-20261103-chat-notice-post-perf-03]
requirements: [REQ-005]
acceptance_tests: [AT-005-1, AT-005-2, AT-005-3, AT-005-4]
touches:
  - 'src/main/java/com/dogGetDrunk/meetjyou/notice/'
  - 'src/main/java/com/dogGetDrunk/meetjyou/terms/'
  - 'src/main/java/com/dogGetDrunk/meetjyou/notification/'
  - 'src/main/java/com/dogGetDrunk/meetjyou/common/exception/'
  - 'src/test/java/com/dogGetDrunk/meetjyou/common/'
  - 'src/test/java/com/dogGetDrunk/meetjyou/notice/NoticeBroadcastApiIntegrationTest.kt'
  - 'src/test/java/com/dogGetDrunk/meetjyou/terms/TermsReconsentBroadcastApiIntegrationTest.kt'
  - 'src/dockerTest/java/com/dogGetDrunk/meetjyou/notification/BroadcastConcurrencyDockerTest.kt'
consumes:
  - 'T-20261103-chat-notice-post-perf-03의 NoticeBroadcaster.broadcast(noticeUuid: UUID, critical: Boolean): Int'
  - 'T-20261103-chat-notice-post-perf-03의 TermsReconsentBroadcaster.broadcast(termsUuid: UUID): Int'
  - 'T-03 원천 잠금·prefix 검사·BROADCAST_ALREADY_SENT(409)'
  - '기존 NOTICE_NOT_FOUND·TERMS_NOT_FOUND·CONCURRENT_REQUEST_CONFLICT'
  - 'SqlPausePoint·performance_schema.data_lock_waits'
produces:
  - 'POST /api/v1/notices/{uuid}/broadcast → BroadcastResultResponse'
  - 'POST /api/v1/terms/{termsUuid}/reconsent-broadcast → BroadcastResultResponse'
  - 'ResendNoticeBroadcastRequest(val critical: Boolean = false)'
  - 'BroadcastResultResponse(val recipientCount: Int), companion of(count: Int)'
  - 'TermsReconsentBroadcaster.broadcast(termsUuid: UUID, validateApplicability: Boolean = false): Int'
  - 'TERMS_RECONSENT_NOT_APPLICABLE(409)'
status: pending
---

## 목표

원천 row의 기존 전량 발송 트랜잭션을 재사용하는 관리자 복구 API 제공, 두 경로의 동시 요청에서 중복 outbox 차단.

## 분류·결합도·DAG

- high: 관리자 권한·공유 DTO/API·원천 row 잠금·멱등성·경쟁 조건(상 조건 다수)
- T-03과 동일 모듈·발송 메서드·오류 코드 결합, T-03 이후에만 실행; T-01·T-02에는 비의존

## 구현 범위

- 포함: NoticeController 기존 /notices 아래 POST /{uuid}/broadcast, body의 critical 기본 false; TermsController 기존 /terms 아래 POST /{termsUuid}/reconsent-broadcast
- 두 endpoint @PreAuthorize("hasAuthority('ADMIN')"), 기존 API prefix /api/v1 유지; 서비스가 BroadcastResultResponse.of(count) 반환, controller .of() 호출 금지
- API 조정 서비스 바깥 트랜잭션 없음 → T-03 발송 빈 프록시 호출, 여기서 HTTP 200 recipientCount 반환
- NoticeBroadcaster는 T-03 시그니처 그대로 재사용; TermsReconsentBroadcaster는 validateApplicability=false 기본 인자만 확장, 원 핸들러 호출은 false 유지, 재발송은 true
- true일 때 원천 terms row 잠금 후 ACTIVE·effectiveAt<=now·동일 type의 INACTIVE 존재를 같은 tx 안에서 검사; 부적격 TERMS_RECONSENT_NOT_APPLICABLE(409), 기존 미래 ACTIVE 원 발송 동작 불변
- 기발송 409 BROADCAST_ALREADY_SENT, 원천 미존재 기존 404, 비관리자 403; ErrorCodeStatusTest에 새 코드 표 반영
- T-03의 잠금→dedup 검사→수신자 계산→outbox 저장 공통 경로 재사용; API 앞단의 비잠금 검사만으로 판정하거나 별도 outbox 저장 루프 작성 금지
- 재발송 수신자 = 호출 시점의 NORMAL/notified/해당 타입 preference 규칙, 가입·설정 변경 반영
- dockerTest: 공지/약관 × 재발송/재발송·원 발송/재발송 = 4개; 원 발송은 실제 발행 API AFTER_COMMIT, SqlPausePoint로 잠금 취득 후 insert 직전 정지, data_lock_waits 폴링으로 실제 대기 확인
- 제외: failed original 뒤 대기 재발송 복구의 추가 경쟁 시나리오(C-r1-06 기각), 자동 재시도·푸시 직접 전송·원 발행 정책 수정·운영 부하 테스트

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/notice/`·`src/main/java/com/dogGetDrunk/meetjyou/terms/` 기존 관리자 endpoint 권한·404 패턴
- T-03 발송 빈과 동일 원천 row 잠금·outbox tx 사용, 별도 동시성 제어 도입 금지
- `.claude/rules/party.md`·`user.md`의 MySQL lock-wait 폴링 선례, SqlPausePoint 테스트 helper

## 참조 파일 (읽기 허용)

- `AGENTS.md`
- `CLAUDE.md`
- `.pipeline/README.md`
- `.pipeline/project.yaml`
- `src/test/resources/` — 기존 test profile 설정만 읽기, 시크릿 접근·설정 변경 금지
- `src/main/java/com/dogGetDrunk/meetjyou/notice/` — T-03 NoticeBroadcaster, NoticeController·API service·DTO, Notice/Repository
- `src/main/java/com/dogGetDrunk/meetjyou/terms/` — T-03 TermsReconsentBroadcaster, TermsController·service·DTO, Terms/Repository·status/type/effectiveAt
- `src/main/java/com/dogGetDrunk/meetjyou/notification/` — T-03 원 핸들러·outbox/Repository·preference, BroadcastResultResponse 배치 위치는 기존 DTO 구조 준수
- `src/main/java/com/dogGetDrunk/meetjyou/user/` — NORMAL/notified·재발송 후보 fixture
- `src/main/java/com/dogGetDrunk/meetjyou/common/` — ErrorCode/BusinessException·GlobalExceptionHandler 및 관련 파일만
- `src/main/java/com/dogGetDrunk/meetjyou/config/SecurityConfig.kt` — ADMIN/GET permitAll 경계 확인만, whitelist 확대 금지
- `src/test/java/com/dogGetDrunk/meetjyou/` — ErrorCodeStatusTest 및 MockMvc 인증 fixture만, 오류 표는 touches의 common 테스트 모듈 안에서만 변경, 범위 밖이면 escalation
- `src/dockerTest/` — SqlPausePoint·data_lock_waits helper와 기존 Testcontainers 설정만, fixture 직접 의존만
- `.claude/rules/auth.md` — ADMIN·사용자 active 인증 불변식
- `.claude/rules/terms.md` — 현재 유효 약관 조건·원 발송 경계
- `.claude/rules/notification.md` — outbox 단일 tx·발행 이벤트 경계

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

- REQ-005 API 계약: 공지 body {"critical": false} 기본값, 약관 body 없음; 성공 200 recipientCount; 기발송 409; API별 404/403
- 약관 재발송은 ACTIVE && effectiveAt<=now && 같은 type INACTIVE 존재, 잠금 후 판정; effectiveAt=now 허용(C-r1-01)
- 수신자는 재발송 시점 재계산, 정상 title/body/dataJson/dedupKey 유지
- AT-005-3·4는 각 공지·약관 모두 실행; 선행 lock 취득+insert 직전 pause → 경쟁 lock wait 관측 → release → outbox 전원 1세트·중복0, 나머지 409(C-r1-06)
- 원 발송 중 재발송은 원 발행 API 성공 + 재발송 지정409, 원 핸들러 직접 호출 불허

## 실행 순서

- [ ] T-03 완료·증거 확인 후 AT-005 보호 파일 복사, endpoint/부적격·경쟁 RED 확인
- [ ] 관리자 요청·응답 DTO와 비트랜잭션 service/controller 조정, 기존 발송 빈 재사용
- [ ] terms 발송 메서드 기본 false 적격성 flag 확장·재발송 true·잠금 안 판정·오류 표 매핑
- [ ] 두 API 성공·중복·404·403·critical·유효시점·재계산 회귀 확인
- [ ] MySQL 4개 경쟁에서 실제 lock wait와 outbox 유일성 확인, executor/latch·pause finally 해제
- [ ] 전체 test/build → dockerTest, 동일 HEAD CI 증거와 함께 보고

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
