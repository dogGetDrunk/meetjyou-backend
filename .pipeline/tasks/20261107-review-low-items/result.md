# 결과

> 태스크 종료 시 메인 설계 에이전트가 작성. 포트폴리오·회고 재료.

## 결과 요약

- 2026-10-26 코드 리뷰 Low 지적(#231) 잔존 15건 중 7건 처리, 티켓 5개 전부 머지 (2026-11-07)
- 공개 GET 실패 토큰 401 → 익명 통과, JWT `userUuid` 형식 오류 500 → 401, 예약 작업 실패 Discord 알림, FCM 타임아웃 + 발송 기록 실패 격리, 마커 교체 plan 행 잠금, 버전 캐시 stale 방지, 미사용 메서드 13개 삭제
- 스키마 변경 없음, 프론트 변경 없음

## 머지된 PR

| 티켓 | PR | 등급 | 구현 / 리뷰 모델 | 라운드 | 토큰 (output / 전체, `logs/tokens.md`) |
|---|---|---|---|---|---|
| T-01 공개 GET 실패 토큰·`userUuid` 클레임 | #274 | high | opus-5-5 / opus-5-5 | 1 | 22,689 / 3,817,126 |
| T-02 예약 작업 알림·FCM 타임아웃 | #275 | high | opus-5-5 / opus-5-5 | 2 (엔진 기록 결함 재시도) | 52,203 / 8,288,805 |
| T-03 마커 교체 plan 행 잠금 | #277 | high | opus-5-5 / opus-5-5 | 1 | 16,356 / 2,757,550 |
| T-04 버전 캐시 `computeIfAbsent` | #276 | high | opus-5-5 / opus-5-5 | 1 (CI flaky 재실행) | 26,856 / 4,974,155 |
| T-05 미사용 메서드 삭제 | #273 | medium | sonnet-5-5 / sonnet-5-5 | 1 | 13,073 / 2,778,900 |

- 설계 단계(비평·plan·brief = opus-5-5, test-writer 11개 = sonnet-5-5): output 256,717 / 전체 77,376,147
- intent PR #272

## 핵심 결정과 기각한 대안

- 범위: 결정적 발생·지속되는 실패·작은 수정 비용 기준으로 7건만 → 제외 8건(알림센터 읽음 범위 기획 확인, 조회수 +2, plan 동시 삭제 FK 500, WS connect rethrow, 채팅 읽음 커서, 내 파티 채팅방 누락 — 불변식상 불가, 약관 동시 발행, purge 유저별 트랜잭션)
- 재확인: `removeParticipant` 세션 끊기는 이미 AFTER_COMMIT 리스너로 해소돼 있었음
- 실측으로 뒤집힌 전제: `@EnableWebSocketMessageBroker`의 `messageBrokerTaskScheduler`가 유일한 `TaskScheduler` 빈 → Boot `taskScheduler` 미생성, `@Scheduled` 4개가 `MessageBroker-*`(CPU 수, 운영 2) 스레드에서 실행
  - intent의 "스레드 1개 → 예약 작업 전부 정지" 정정: 실제 영향 = 알림 발송 정지 + 스레드 1개 점유 (범위·성공 기준 불변)
  - 채택: `SchedulingConfigurer` + 전용 `scheduledTaskScheduler`(스레드 2) / 기각: `ThreadPoolTaskSchedulerCustomizer`(대상 빈 없음), 브로커 스케줄러 직접 수정(웹소켓 결합, 하트비트와 공유 지속), `taskScheduler` 이름 빈 암묵 선택
- 알림 `source` = `ScheduledTask:<클래스>.<메서드>` — dedup 키가 `status:exceptionClass:source`라 단일 상수면 2초 주기 발송 실패가 같은 예외의 탈퇴 정리 실패 알림을 삼킴
- FCM 타임아웃 10초 3종 — SDK 기본 0(무한, firebase-admin 9.5.0 바이트코드 확인)
- 공개 경로 판정: 메서드 + 패턴 `RequestMatcher` 한 목록을 `SecurityConfig`·필터가 공유, `requestURI` 기준 `AntPathRequestMatcher` / 기각: 필터 생략(유효 토큰 인증 소실), 예외 시 그냥 통과(비공개 API의 `EXPIRED_TOKEN` 신호 소실)
- 마커: plan 행 `PESSIMISTIC_WRITE`를 첫 DB 읽기로 / 허용: plan 삭제와 마커 저장 동시 시 데드락 → 기존 409 사후 처리
- 버전 캐시: `computeIfAbsent` 고정 / 기각: 세대 번호 비교
- 비평 r1 11건(수용 9, 기각 2 — 코드 확인으로 전제 성립), brief r1 3건 전부 수용(보호 테스트 package-private 호출 컴파일 실패, `antMatcher`가 `MockHttpServletRequest` 경로 미인식, 참조 파일 누락) → `decisions.md`

## spec 대비 검증

- 각 워커 PR: 엔진 gate `./gradlew test build` + CI 필수 체크 `build-and-test`(`./gradlew build dockerTest`) 통과 후 머지
- AT-005-1(마커 잠금 dockerTest)은 CI dockerTest에서 실행
- 엔진 `check-tests` 통과, 단 project.yaml에 `test_red` 미설정이라 base RED 자동 실행은 없었음
- main 최종 머지(#276, `0d63869`) CI `build-and-test` success(run 42369782831, build + dockerTest 포함), 운영 자동 배포 success

## 남은 후속 작업

- `BroadcastConcurrencyDockerTest` AT-005-3 간헐 실패(잠금 대기 10초 내 미관측, PR #276 CI 1회) — 이번 변경 무관, 재발 시 별도 이슈
- agent-pipeline: 병렬 PR이 연달아 머지돼 base가 두 번 움직이면 `merge_target`이 갱신된 head를 거부(`base_update_from` 미설정) → 불필요한 재구현 1회(T-02)
- agent-pipeline: 엔진 자신의 티켓 `status:` 갱신을 triage가 "설계 문서 변경"으로 오탐
- 제외 8건은 노션 보드(작업 ID 240) TODO에 유지, 알림센터 읽음 범위는 기획 확인 대기
