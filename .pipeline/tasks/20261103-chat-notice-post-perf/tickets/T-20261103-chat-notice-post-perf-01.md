---
id: T-20261103-chat-notice-post-perf-01
title: 채팅방 목록 안 읽은 수 단일 집계
tier: high
effort_override: null
depends_on: []
requirements: [REQ-001]
acceptance_tests: [AT-001-1, AT-001-2]
touches:
  - 'src/main/java/com/dogGetDrunk/meetjyou/chat/'
  - 'src/test/java/com/dogGetDrunk/meetjyou/chat/ChatUnreadAggregationTest.kt'
  - 'src/test/java/com/dogGetDrunk/meetjyou/chat/ChatUnreadAggregationIntegrationTest.kt'
consumes:
  - 'ChatReadService.buildUnreadCountMap'
  - 'UserPartyRepository.findAllWithPartyByUserUuidAndMemberStatus(JOINED)'
  - 'ChatRoomRepository.findAllWithPartyByPartyUuidIn'
  - 'UserParty.lastReadMessageId'
produces:
  - 'ChatMessageRepository.countUnreadByRoomUuids(roomUuids, requesterUuid): List<RoomUnreadCountProjection>'
  - 'ChatReadService: 방 0개면 집계 0회, 방 1개·10개면 집계 1회'
status: blocked
---

## 목표

방별 Repository 호출을 요청자 JOINED 멤버십 기준 집계 한 번으로 교체, 기존 unread 값·목록 접근 범위 유지.

## 분류·결합도·DAG

- high: 성능 목표를 실제 SQL로 입증해야 하는 작업(검증 가능성의 상 조건), Repository와 서비스 연결 변경
- T-02·T-03과 변경 모듈·보호 파일 비중첩, 선행 티켓 없음; 독립 실행 가능

## 구현 범위

- 포함: ChatReadService 집계 경로·ChatMessageRepository 집계 쿼리·RoomUnreadCountProjection 필요한 조정, AT-001 보호 테스트 복사
- 집계 조건: ChatMessage → ChatRoom → 같은 party의 요청자 UserParty, JOINED, 자기 sender 제외, cursor null 또는 message id 초과, room uuid별 group by
- 전달 roomUuids로 제한, 빈 입력은 Repository 호출 전 반환; 집계 row 없는 방은 응답에서 0
- 기존 두 count 메서드는 허용 chat 모듈 안의 호출부 확인 후 다른 사용이 없을 때만 삭제; 범위 밖 호출 가능성이 남으면 유지·보고
- 제외: ChatParticipant·멤버십 정책·읽음 갱신·STOMP/알림 계약 변경, 다른 목록 API 최적화, 인덱스·캐시 도입

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/chat/`의 ChatReadService·ChatMessageRepository·RoomUnreadCountProjection: 기존 unread map과 projection 패턴
- `src/main/java/com/dogGetDrunk/meetjyou/userparty/`의 UserParty: 요청자 JOINED·lastReadMessageId
- `src/main/java/com/dogGetDrunk/meetjyou/chat/room/`의 ChatRoom: party 연결

## 참조 파일 (읽기 허용)

- `AGENTS.md`
- `CLAUDE.md`
- `.pipeline/README.md`
- `.pipeline/project.yaml`
- `src/test/resources/` — 기존 test profile 설정만 읽기, 시크릿 접근·설정 변경 금지
- `src/main/java/com/dogGetDrunk/meetjyou/chat/` — ChatReadService, ChatRoom/Repository, ChatMessage/Repository, unread projection·목록 DTO, 목록 호출부
- `src/main/java/com/dogGetDrunk/meetjyou/userparty/` — UserParty/Repository, MemberStatus, JOINED 목록 선택·읽음 커서
- `src/main/java/com/dogGetDrunk/meetjyou/party/` — fixture의 Party 및 요청자 party 연결
- `src/main/java/com/dogGetDrunk/meetjyou/user/` — fixture의 User/Repository·NORMAL 상태
- `src/main/java/com/dogGetDrunk/meetjyou/common/util/CurrentUserProvider.kt` — 요청자 uuid 접근
- `src/test/java/com/dogGetDrunk/meetjyou/chat/` — 기존 chat fixture·통합 테스트 패턴; 보호 파일 외 수정 금지
- `.claude/rules/chat.md` — 채팅 접근·이벤트 불변식
- `.claude/rules/party.md` — UserParty 상태·DynamicUpdate 불변식

## 관련 spec 발췌

- REQ-001: 안 읽은 수 집계 쿼리 정확히 1회(방 0개면 0회), Repository 호출과 실제 chat_message SELECT 양쪽 검증
- 기존 결과: 타인 메시지 중 cursor 없음=전체, cursor 있음=id 초과; 빈 방=0
- 목록 방의 요청자 JOINED 멤버십과 uk_user_party(user_id, party_id)로 조인 동등성 성립(C-r1-07); 다른 사용자·목록 밖 방 집계 제외
- 테스트 fixture 저장·flush·clear 이후 측정 시작, 전체 API SQL 수 고정 금지(C-r1-05)

## 실행 순서

- [ ] AT-001-1·AT-001-2 보호 파일 복사 및 현재 구현 실패 확인
- [ ] 단일 집계 쿼리 추가, buildUnreadCountMap 호출 교체·빈 입력 처리
- [ ] 요청자·방 필터와 누락 row의 0 처리 자체 리뷰, 구 메서드의 허용 범위 내 참조 확인
- [ ] 대상 테스트 → 전체 test/build → dockerTest 실행 및 같은 HEAD 증거 기록

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
