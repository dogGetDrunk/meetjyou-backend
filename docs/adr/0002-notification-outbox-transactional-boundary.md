# ADR-0002: 알림 outbox 트랜잭션 경계 — Critical 버그는 수정, 원자성 개선은 보류

**Status:** Accepted — Part 2 (outbox 원자성 개선)는 유실 창을 위험 수용하기로 결정 (2026-10-11, 이슈 #150, 하단 Update 섹션 참고)
**Date:** 2026-07-13
**Updated:** 2026-07-19 — `NotificationEventHandler`의 REQUIRES_NEW 부재에 대한 위험도 분석 정정 (하단 Update 섹션 참고)
**Updated:** 2026-10-11 — Part 2 결론: Option B 미적용, 유실 창 위험 수용
**Deciders:** damiannlee

## Context

전체 코드 리뷰 중 `ChatService.kt`의 `handleChatMessage()`(chat 메시지 저장 + 오프라인 유저 알림 발행)에 `@Transactional`이 아예 없다는 Critical 버그를 발견함.

### 버그의 실제 흐름

1. `ChatService.kt:42` `handleChatMessage()` — 트랜잭션 없음
2. `ChatService.kt:59` `chatMessageRepository.save(...)` — Spring Data JPA의 `save()`가 자체 트랜잭션으로 즉시 커밋
3. `ChatService.kt:111-125` — 오프라인 수신자마다 `publisher.publishEvent(NotificationEvent(...))` 호출
4. `NotificationEventHandler.kt:25` `on(event: NotificationEvent)` — `@TransactionalEventListener(phase = AFTER_COMMIT)`. "지금 실행 중인 트랜잭션이 커밋되면" 실행되는 리스너인데, 2번 단계에서 트랜잭션 자체가 없었으므로 커밋을 기다릴 대상이 없음
5. `@TransactionalEventListener`의 기본값 `fallbackExecution = false` 때문에 리스너가 **아예 호출되지 않고 조용히 이벤트가 버려짐**
6. 결과: `NotificationEventHandler.kt:49` `outboxRepository.save(outbox)`가 한 번도 실행되지 않아 `notification_outbox`에 row가 안 생기고, 오프라인 유저는 채팅 푸시를 100% 못 받음. 에러 로그도 없음

### 리뷰 중 추가로 발견한 구조적 이슈

`NotificationEventHandler.kt`, `NoticeBroadcastEventHandler.kt:30-31`, `TermsReconsentEventHandler.kt:31-32` 세 핸들러 모두 `@TransactionalEventListener(phase = AFTER_COMMIT)` 패턴을 쓰는데, 이 중 두 곳(`NoticeBroadcastEventHandler`, `TermsReconsentEventHandler`)은 `@Transactional(propagation = Propagation.REQUIRES_NEW)`까지 명시되어 있음. 즉 **outbox 기록이 원 비즈니스 트랜잭션과 별개의 새 트랜잭션에서, 원 트랜잭션이 커밋된 "이후"에** 실행됨.

`notification.md`는 "이벤트는 원 트랜잭션 내에서 outbox에 기록되어야 한다"고 규정하는데, 현재 구조는 이 규정을 문자 그대로 지키지 않음. 원 트랜잭션 커밋과 outbox insert 사이의 그 짧은 창(process crash, 재기동 등)에 놓이면 알림이 유실될 수 있음 — 단, 이건 "100% 항상 발생"이 아니라 "그 좁은 시간대에 크래시가 나야만" 발생하는 훨씬 드문 케이스.

> ⚠️ **2026-07-19 정정:** 위 문단은 "REQUIRES_NEW가 *있는* 핸들러"에 대해서만 맞는 분석이다. REQUIRES_NEW가 *없던* `NotificationEventHandler`는 드문 크래시 케이스가 아니라 100% 유실 조건이었다. 하단 Update 섹션 참고.

## Decision

**두 문제를 분리해서 다루기로 함:**

1. **Critical 버그(100% 재현)**: `ChatService.handleChatMessage()`에 `@Transactional` 추가 → **이번 세션에서 완료**
2. **구조적 개선(드문 크래시 케이스)**: AFTER_COMMIT + REQUIRES_NEW 패턴 자체를 원 트랜잭션 내 기록으로 리팩토링 → **범위가 크고(3개 핸들러 + 호출부 전부) 팀 차원의 아키텍처 결정이 필요해 이번엔 보류**, 별도 세션에서 진행

## Options Considered (Part 2, 보류된 쪽)

### Option A: Critical만 수정, AFTER_COMMIT+REQUIRES_NEW 구조 유지 (채택)

**Pros:**
- 변경 범위가 작음 (`ChatService.kt` 한 줄)
- 3개 핸들러에 이미 자리잡은 기존 패턴을 건드리지 않아 회귀 위험이 낮음
- AFTER_COMMIT은 "롤백된 트랜잭션에 대해 알림을 잘못 발행하지 않는다"는 장점을 그대로 유지

**Cons:**
- 커밋 직후~REQUIRES_NEW insert 사이의 좁은 창에서 프로세스가 죽으면 그 알림만 유실 가능. `dedup_key`로 재처리하려 해도, 애초에 outbox row 자체가 없으니 재처리 근거가 없음

### Option B: outbox 기록을 원 트랜잭션 내로 리팩토링 (보류)

`@TransactionalEventListener` + 별도 핸들러 클래스 구조를 버리고, `ChatService`/`NoticeService`/`TermsService`의 비즈니스 메서드 안에서 직접 `outboxRepository.save(...)`를 호출하도록 변경.

**Pros:**
- notification.md 규정을 문자 그대로 만족
- 크래시로 인한 유실 창 자체가 사라짐

**Cons:**
- `ChatService`, `NoticeService`, `TermsService` + `NotificationEventHandler`/`NoticeBroadcastEventHandler`/`TermsReconsentEventHandler` 전부를 건드리는 큰 리팩토링
- 이 이벤트 리스너 구조가 팀이 의도적으로 선택한 "비즈니스 로직과 알림 발행을 디커플링"하는 아키텍처일 가능성이 있어, 되돌리는 건 버그 수정보다 큰 아키텍처 결정
- `NotificationPayload` → `NotificationTemplateFactory`로 title/body를 만드는 로직이 지금 핸들러 안에 있는데, 이걸 비즈니스 서비스 쪽으로 옮기면 `ChatService` 등이 `NotificationTemplateFactory`/`NotificationPreferenceService`에 직접 의존하게 됨 — 레이어 경계가 흐려짐

**선택 이유:** Critical 버그는 "오프라인 유저는 항상 알림을 못 받는다"는 100% 재현 문제였던 반면, 이 구조적 이슈는 "크래시 타이밍"이라는 훨씬 드문 조건에서만 발생. 이번엔 급한 불부터 끄고, 구조 변경은 범위와 위험을 고려해 별도로 계획하기로 함.

## Update (2026-07-19) — REQUIRES_NEW 부재는 "드문 케이스"가 아니라 100% 유실 버그였음

이 ADR 작성 당시 `NotificationEventHandler`에 REQUIRES_NEW가 없는 것을 "커밋 직후 크래시라는 드문 창에서만 문제되는 구조적 이슈"로 분류했는데, 이는 잘못된 분석이었다.

Spring 공식 문서에 따르면 `AFTER_COMMIT` 리스너 안에서 실행되는 데이터 접근 코드는, 별도 트랜잭션을 명시하지 않는 한 **이미 커밋된 원 트랜잭션에 "참여"하며 이후 flush/commit이 다시는 일어나지 않는다.** `SimpleJpaRepository.save()`의 기본 전파(REQUIRED)도 이미 커밋된 트랜잭션에 join할 뿐이다. 즉:

- `bd7097e`에서 `ChatService.handleChatMessage()`에 `@Transactional`이 추가되어 리스너가 실제로 발화하기 시작한 순간부터,
- REQUIRES_NEW가 없던 `NotificationEventHandler.on()`의 `outboxRepository.save(outbox)`는 **조용히 유실되는 상태**였다 (에러 로그 없음, mock 기반 단위 테스트로는 검출 불가).
- `NoticeBroadcastEventHandler`/`TermsReconsentEventHandler`가 REQUIRES_NEW를 명시한 것은 바로 이 때문이며, `NotificationEventHandler`만 누락되어 있었다.

**조치:** PR #113에서 `NotificationEventHandler.on()`에 `@Transactional(propagation = Propagation.REQUIRES_NEW)`를 추가해 다른 두 핸들러와 동일한 패턴으로 정렬함.

이 정정은 Option A/B 선택 자체를 뒤집지 않는다 — "커밋 직후~REQUIRES_NEW insert 사이의 좁은 크래시 창" 이슈(Option A의 Cons)는 REQUIRES_NEW가 추가된 지금도 그대로 남아 있으며, Option B(원 트랜잭션 내 기록) 논의는 여전히 유효한 후속 과제다.

## Update (2026-10-11) — Part 2 결론: 유실 창은 위험 수용, Option B 미적용

이슈 #150에서 Part 2를 다시 검토한 결과, 커밋 직후~outbox insert 사이의 유실 창은 **고치지 않고 수용**하기로 함. 같은 이슈의 디스패처 결함(`SENDING` 고착, 멀티토큰 비멱등)은 수정함.

### 결정 기준

- 알림 실패가 비즈니스 트랜잭션(채팅 저장 등)을 롤백시키면 안 됨 — "채팅과 함께 유실"보다 "알림만 유실"이 나음
- 푸시는 유일한 전달 수단이 아님 — 대부분 앱 내 조회가 보완

### 타입별 앱 내 보완

- `CHAT_MESSAGE`: 채팅방 목록의 방별 `unreadCount` (`ChatReadService`)
- `NOTICE`: 알림센터 공지 목록 + `lastNoticesViewedAt` 기준 안 읽은 수
- `PARTY_JOIN_REQUEST`: 알림센터 받은 신청 + `hostRead`
- `PARTY_JOIN_ACCEPTED` / `PARTY_JOIN_REJECTED`: 알림센터 보낸 신청 + `applicantRead`
- `PARTY_MEMBER_JOINED`: 보완 없음 — 정보성 알림이라 유실 피해 작음
- `TERMS_RECONSENT`: 푸시가 유일 — 올바른 해법은 푸시 보장 강화가 아니라 앱 진입 시 재동의 확인(별개 과제)
- `PARTY_CREATED`: 발행처 없음(미사용 enum)

### 기각한 대안

- **원 트랜잭션 편입** (`BEFORE_COMMIT` 리스너 또는 `@EventListener` + `MANDATORY`): 유실 창은 사라지나 핸들러 예외(템플릿·선호도 조회 등)가 비즈니스 트랜잭션을 롤백시킴 → 결정 기준과 정반대
- **Option B (서비스 직접 저장)**: 위와 같은 롤백 전파 + 서비스 4곳이 알림 모듈에 결합
- **2단계 outbox** (원 트랜잭션에는 이벤트 원본 1행만 기록, 커밋 후 핸들러가 펼치고 스케줄러가 미처리분 재시도): 두 기준을 모두 만족하는 유일한 안이나, 테이블·스케줄러를 추가해 지키는 대상이 사실상 `TERMS_RECONSENT`(연 수회 관리자 동작)뿐 → 현 규모에선 과설계

### 수용하는 비용

- 유실 창에 걸린 푸시는 복구되지 않음. 사용자는 앱을 열 때까지 해당 사건을 모름

### 재검토 조건

- 앱 내 보완이 없는 중요 알림 타입이 추가됨
- 유실이 실제로 관측됨
- 재검토 시 1순위 후보는 2단계 outbox

## 다음 세션에서 할 일 (Action Items)

- [x] `ChatService.kt:42` `handleChatMessage()`에 `@Transactional` 추가 (Critical, 완료)
- [x] `NotificationEventHandler.on()`에 `@Transactional(REQUIRES_NEW)` 추가 — outbox save가 이미 커밋된 트랜잭션에 join되어 flush되지 않던 버그 (PR #113, 완료)
- [x] Option B(원 트랜잭션 내 outbox 기록) 적용 여부 결정 — 미적용, 유실 창 위험 수용 (2026-10-11 Update 참고). 아래 두 하위 항목은 당시 검토안으로 남겨 둠
  - 적용하기로 하면: `ChatService`/`NoticeService`/`TermsService`가 각자 `outboxRepository`를 직접 호출하도록 변경, `NotificationEventHandler`/`NoticeBroadcastEventHandler`/`TermsReconsentEventHandler`의 title/body 생성 로직(`NotificationTemplateFactory` 호출부)을 비즈니스 서비스가 쓸 수 있는 공용 헬퍼로 추출
  - 적용 안 하기로 하면: 이 ADR을 "Rejected"로 갱신하고, 대신 dedup_key 기반 재처리 배치(크래시로 유실된 outbox를 감지/보정)를 별도로 설계
- [ ] (참고) `NotificationEventHandler.kt`에 `dedupKey` 기반 중복 삽입 방지 체크는 이번 세션에서 추가함(`existsByDedupKey`) — 재처리 설계 시 이 체크를 재사용 가능

## 관련 커밋/파일

- `src/main/java/com/dogGetDrunk/meetjyou/chat/ChatService.kt` — `handleChatMessage()`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/event/NotificationEventHandler.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/event/NoticeBroadcastEventHandler.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/notification/event/TermsReconsentEventHandler.kt`
- `.claude/rules/notification.md` — "원 트랜잭션 내 기록" 규정 출처
