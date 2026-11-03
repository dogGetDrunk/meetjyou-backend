# Intent

> 메인 설계 에이전트가 사람과 대화로 작성. 작성을 마치면 첫 줄의 template 표시 삭제.
> 승인 기록은 `approval.json`(`agent-pipeline approve-intent`), 승인 후 이 파일 수정 금지.

## 해결할 문제

- 채팅방 목록(`GET /api/v1/chat/rooms`): `ChatReadService.buildUnreadCountMap`이 마지막 읽은 메시지가 있는 방마다 `countUnreadByRoomUuidAfterLastReadMessageId` 호출 → 방 K개면 쿼리 K회, 크로스클라우드 DB 왕복이 방 수만큼 누적
- 공지·약관 재동의 브로드캐스트(`NoticeBroadcastEventHandler`·`TermsReconsentEventHandler`)
  - 대상 유저마다 `NotificationPreferenceService.isEnabled` → 유저 N명이면 설정 조회 N회, AFTER_COMMIT 리스너라 발행 요청 스레드에서 실행 → 발행 API 응답·트랜잭션 시간이 N에 비례 (DB RTT p50 4.25ms 기준 유저당 왕복 2회)
  - 실패 시 처리 부재: 핸들러 예외가 이미 커밋된 발행 요청으로 전파(Spring `afterCommit` 규약, 미검증) → 공지는 저장됐는데 관리자는 500을 받음 → 재등록 시 공지 중복
  - 재발송 경로 부재: 공지는 새 공지 재등록(목록 중복)뿐, 약관 재동의는 새 약관 버전 발행 외 방법 없음
- 게시글 상세(`GET /api/v1/posts/{uuid}`): `PostService.getPostByUuid`(`readOnly` 트랜잭션)가 커넥션을 쥔 채 `PostViewService.incrementIfEligible`(`REQUIRES_NEW`) 호출 → 요청당 커넥션 2개 동시 점유
  - Hikari `maximum-pool-size: 50`, `connection-timeout: 5000` → 동시 상세 조회 50건 이상이면 전원이 안쪽 커넥션 대기 → 5초 타임아웃
  - `runCatching`이 예외를 삼켜 응답 지연 + 조회수 유실로만 드러남

## 의도

- 세 경로의 DB 호출 수·커넥션 점유를 요청 크기(방 수·유저 수)와 무관하게 낮춤
- 브로드캐스트는 "전원 또는 0건" 의미 유지, 0건으로 끝나면 운영자가 알림을 보고 재발송 API로 복구
- 응답 형식·알림 내용·수신자 규칙·조회수 규칙(같은 유저 10분 내 재조회 미집계)은 그대로

## 포함 범위

- 채팅방 목록 안 읽은 수: 방 수와 무관한 고정 횟수(목표 1회) 집계 쿼리로 교체, 마지막 읽은 메시지 유무 두 경우 모두 포함
- 브로드캐스트 쿼리: 해당 타입 알림을 끈 유저(`enabled=false`)를 일괄 조회 후 메모리 필터, `user.notified=false` 제외 규칙 유지, 단일 `REQUIRES_NEW` 트랜잭션 유지(D1)
- 브로드캐스트 실패 처리: 핸들러가 예외를 잡아 ERROR 로그 + Discord 알림(공지·약관 uuid, 실패 원인, 재발송 방법 포함), 발행 API는 정상 응답
- 재발송 API (관리자 전용 `@PreAuthorize("hasAuthority('ADMIN')")`)
  - 공지: 지정 공지의 푸시 재발송, `critical`은 요청에서 받음 (공지 엔티티에 `critical`·`notify` 미저장)
  - 약관 재동의: 지정 약관의 재동의 알림 재발송, 이전 버전을 대체한 현재 유효 약관만 허용
  - 이미 발송된 대상(해당 공지·약관 dedup 접두 outbox row 존재)이면 409로 거부 — 단일 트랜잭션이라 존재 = 전원 발송 완료
  - 동시 재발송·원 발송 진행 중 재발송에서 중복 발송 방지 (방식은 spec에서 결정: 행 잠금 / `dedup_key` 유니크 인덱스 등)
- 게시글 상세 조회수 증가: 바깥 읽기 트랜잭션 밖으로 이동 → 요청당 동시 점유 커넥션 1개
- 각 변경의 쿼리 수·커넥션 점유·실패·재발송 동작을 고정하는 회귀 테스트 (단위 + 통합)

## 제외 범위

- 브로드캐스트 청크 분할 — 단일 트랜잭션 유지로 실익 없음(D1)
- 브로드캐스트 비동기화(`@Async`·별도 스레드풀) — ADR-0002 트랜잭션 경계 재검토가 필요한 별도 결정
- outbox 대량 insert 방식 변경(JDBC batch·`INSERT … SELECT`) — `IDENTITY` 키라 건별 insert는 남음, 측정 후 필요 시 별도 이슈
- 일부만 발송된 상태의 "빠진 사람만" 재발송 — 단일 트랜잭션이라 해당 상태 없음
- 공지 `notify`·`critical` 값 저장(스키마 변경)
- 운영 부하 테스트 실행 — 배포 후 사람이 수행, 결과는 result.md에 기록(D2)
- 다른 목록 API N+1(#194 등)

## 성공 기준

- 채팅방 목록: 방 1개·10개일 때 안 읽은 수 집계 쿼리 횟수 동일 (테스트로 고정), 안 읽은 수 값 기존과 일치
- 브로드캐스트: 대상 유저 수와 무관하게 알림 설정 조회 1회 (테스트로 고정), 수신자 집합 기존과 동일(알림 끈 유저·`notified=false`·비 NORMAL 제외, `critical` 공지는 설정 무시)
- 브로드캐스트 실패: outbox 0건 + ERROR 로그 + Discord 알림 발생, 공지·약관 발행 API는 성공 응답 (테스트로 고정)
- 재발송: 0건 상태에서 호출 시 원 발송과 같은 수신자에게 outbox 생성, 발송 완료 상태에서 호출 시 409, 비관리자 403, 동시 호출 2건에서 outbox 중복 없음 (dockerTest로 고정)
- 게시글 상세: 한 요청이 동시에 쥐는 커넥션 1개 (테스트로 고정), 조회수 증가·10분 중복 제외 규칙 기존과 동일, 조회수 증가 실패가 상세 응답을 깨지 않음
- `./gradlew build`(detekt·Konsist 포함) + `./gradlew test` + `./gradlew dockerTest` 통과

## 결정 기록

- **D1. 브로드캐스트 실패 의미 = 단일 트랜잭션 유지(B)** — 2026-11-03 사용자 확정
  - 근거: 전체 유실 확률 낮음(유저 2,000명 이하면 배포 중 강제 종료로도 사실상 0%, 10,000명에서 발송 1회당 약 0.06% 추정), 영향은 푸시뿐(알림센터에 공지 노출), "0건 또는 전원"이라 통째 재발송으로 복구 단순
  - 기각: 청크별 독립 트랜잭션(A) — 부분 발송 상태가 생겨 "빠진 사람만" 재발송 로직 필요, `dedup_key` 유니크 인덱스 부재로 유저별 존재 확인 필요
- **D2. 운영 부하 테스트 = 이 태스크 완료 기준에서 제외, 배포 후 사람 작업** — 2026-11-03 사용자 확정 (파이프라인 가드레일상 워커 운영 배포 금지)
- **D3. 재발송 API = 이번 태스크에서 구현** — 2026-11-03 사용자 확정, 운영자가 로그·알림 확인 후 수동 호출

## 작업 크기

M — 성능 수정 3건 + 브로드캐스트 실패 처리·재발송 API(공지·약관), 스키마 변경은 spec에서 중복 방지 방식 결정 시에만

## 출처

- 노션 태스크: [채팅방 목록·공지 발송·게시글 상세 서버 속도 개선](https://app.notion.com/p/40556d9a97d981d2be12cf9996734dfc) (작업 ID 232, 마감 2026-11-08)
- GitHub 이슈: dogGetDrunk/meetjyou-backend#225, dogGetDrunk/meetjyou-backend#228
