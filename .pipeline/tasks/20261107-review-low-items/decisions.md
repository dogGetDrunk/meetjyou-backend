# Spec 비평 결정

| 라운드 (최대 2) | 항목 | 수용/기각 | 근거 | 메인 설계자 |
|---|---|---|---|---|
| r1 | C-r1-01 | 수용 | `CustomJwtException`·`UserWithdrawnException` 모두 `BusinessException` 하위 확인 → 필터의 `catch (BusinessException)`이 오류 응답을 쓰는 유일한 분기. 정의를 그 분기 전체로 명시, Bearer refresh 토큰 케이스 추가 | main |
| r1 | C-r1-02 | 수용 | 공유 객체를 메서드 + 패턴 `RequestMatcher` 목록으로 고정, `*` 세그먼트·하위 경로 없는 notices 케이스 추가 | main |
| r1 | C-r1-03 | 수용 | AT-002-1에 `getExpiration` 부재 확인 추가 (T-05 의존 회피) | main |
| r1 | C-r1-04 | 기각 | 4개 예약 작업 모두 예외를 안에서 잡지 않음 확인 (`WithdrawnUserPurgeScheduler` 50행 `purge()` 호출에 catch 없음). 확인 사실을 spec에 기재 | main |
| r1 | C-r1-05 | 수용 | dedup 키 `status:exceptionClass:location` 확인 → `source`에 실패 작업 `클래스.메서드` 포함 | main |
| r1 | C-r1-06 | 수용 | `@Scheduled` 등록 스케줄러 = 커스터마이즈된 빈 단언 추가, 알림 검증은 인자 매칭, mock은 기존 `@MockBean`/`@MockitoBean` 패턴 | main |
| r1 | C-r1-07 | 기각 | 전제 확인: `dispatchBatch` 트랜잭션 없음, `updateResult`·`deactivateAllByIdIn`은 리포지토리 `@Transactional`로 건별 독립 커밋 → rollback-only 전파 없음. 통합 AT 추가 대신 전제와 "배치 루프 트랜잭션 추가 금지"를 spec에 기재 | main |
| r1 | C-r1-08 | 수용 | AT-005-1을 보유 트랜잭션이 마커를 교체한 뒤 대기 → 커밋 후 최종 = 요청 집합, 잔존 0으로 강화, 타이밍 의존 케이스 제거 | main |
| r1 | C-r1-09 | 수용(부분) | T-03 구현자 dockerTest 실행·출력 첨부, 데몬 없으면 blocking 명시. CI 필수 체크가 `./gradlew build dockerTest` 실행해 머지 전 강제됨 확인. 기각: project gate에 dockerTest 추가(전 티켓 검증 시간 증가, CI가 이미 강제) | main |
| r1 | C-r1-10 | 수용 | `computeIfAbsent`로 고정, 매핑 함수 내 DB 읽기·맵 재수정 금지, `checkVersion` 트랜잭션 없음 확인·추가 금지. 기각: 세대 번호 방식 | main |
| r1 | C-r1-11 | 수용(제안 1) | 엔진이 티켓별 AT를 요구해 AT 유지, 테스트에 "#231 정리 확인용, 재추가 시 삭제" 명시. 기각: AT 대신 grep 명령(엔진 수용 테스트 체계 밖) | main |
| plan 후 | T-02 위험(plan 워커 지적) | 수용 | 컨텍스트 실측: `TaskScheduler` 빈 = `messageBrokerTaskScheduler`뿐, Boot `taskScheduler` 미생성, `@Scheduled`가 `MessageBroker-*`(스레드 = CPU 수, 운영 2)에서 실행 → `SchedulingConfigurer` + 전용 `scheduledTaskScheduler`(스레드 2)로 변경. 기각: 브로커 스케줄러 직접 수정, `taskScheduler` 이름 빈 암묵 선택. intent의 "스레드 1개" 서술 정정(범위·성공 기준 불변) | main |
| brief r1 | B-r1-01 | 수용 | `FirebaseOptions.getCredentials()` package-private → 보호 테스트의 자격 증명 유지 케이스 삭제 (spec 수용 기준은 타임아웃 3종) | main |
| brief r1 | B-r1-02 | 수용 | T-01 카드의 `antMatcher` 예시 삭제, `requestURI` 기준 `AntPathRequestMatcher(pattern, GET, true, UrlPathHelper.defaultInstance)` 명시. 테스트 수정 불필요 | main |
| brief r1 | B-r1-03 | 수용 | T-01(preference·notice·version·principal·User), T-03(`LockWaitAssertions.kt`) 참조 파일 추가 | main |
