# Spec 비평 r1

- 대상: `spec.md` (intent PR #272 기준), 입력 = intent.md·spec.md·decisions.md·spec-changelog.md + 공급된 프로젝트 규칙
- 저장소 탐색 없이 작성 — 코드 확인이 필요한 항목은 "확인 필요"로 표시하고 근거를 적음
- 우선순위: 높음 = 그대로 두면 AT가 결함을 못 잡거나 구현자가 막힘 / 중간 = 모호해서 구현마다 결과가 갈림 / 낮음 = 범위·유지비

### C-r1-01 REQ-001 우회 대상 예외를 클래스명(`BusinessException`)이 아니라 "필터가 지금 오류 응답을 쓰는 모든 경우"로 정의

- 우선순위: 높음
- 문제
  - 요구사항은 "토큰 인증 중 `BusinessException`이 나면" 공개 요청을 통과시킴. 그런데 REQ-002는 같은 경로를 "`CustomJwtException`/`BusinessException` 경로"로 따로 부름 → 두 타입의 상속 관계가 spec에 없음. `CustomJwtException`이 `BusinessException` 하위가 아니면 만료·서명 불일치 토큰이 우회 대상에서 빠져 REQ-001 핵심 케이스가 그대로 401
  - 탈퇴 유저 토큰(AT 케이스)은 `JwtAuthFilter`의 `User.isActive()` 검사(`.claude/rules/user.md`) 경로라 JWT 예외가 아닐 수 있음 — 어떤 예외로 거절되는지 spec에 없음
  - `Authorization: Bearer`에 refresh 토큰을 붙인 경우(`token_type` 불일치, `.claude/rules/auth.md` "the filter and STOMP CONNECT reject anything else")가 AT 목록에 없음 — 앱이 토큰을 잘못 붙이는 실제 패턴
- 제안
  - 요구사항을 "지금 `doFilterInternal`이 오류 응답을 쓰고 체인을 끊는 모든 분기 → 공개 GET이면 익명으로 진행"으로 바꾸고, 해당 예외 타입을 spec에 명시(설계자가 코드 확인 후 기재)
  - AT-001-1·AT-001-2에 "공개 GET + Bearer refresh 토큰 → 토큰 없는 요청과 같은 결과" 케이스 추가

### C-r1-02 REQ-001 공개 경로 공유 목록은 문자열이 아니라 "메서드 + 패턴" 매처로, 두 곳이 같은 매칭 규칙을 쓰게 고정

- 우선순위: 중간
- 문제
  - "목록을 한 곳에 두고 두 곳이 읽음"만으로는 매칭 알고리즘 차이를 못 막음. `SecurityConfig`는 Spring Security 패턴 매칭(`*` = 한 세그먼트, `**` = 0개 이상)을 쓰는데, 필터가 같은 문자열을 `startsWith`·정규식 등 다른 방식으로 비교하면 목록은 같아도 판정이 어긋남(예: `/api/v1/*/versions/check`, `/api/v1/notices`(하위 경로 없음))
  - GET 한정 조건도 목록 쪽에 같이 있어야 필터·`permitAll`이 메서드 조건까지 일치
- 제안
  - 공유 객체를 `RequestMatcher`(HTTP 메서드 + 패턴) 목록으로 정의하고 `SecurityConfig`·`JwtAuthFilter` 둘 다 그 객체의 `matches(request)`만 사용하도록 요구사항에 명시
  - AT-001-2에 `*` 세그먼트(플랫폼 값 2종)와 `/api/v1/notices`(하위 경로 없음) 케이스 포함

### C-r1-03 `JwtProvider.getExpiration` 삭제(13번째 메서드)에 AT가 없음 — T-01 소유 AT에 넣어야 함

- 우선순위: 중간
- 문제
  - intent 성공 기준 7은 "대상 메서드 13개 제거". AT-007-1(리플렉션)은 12개만 확인하고, `getExpiration`은 REQ-002에서 삭제하지만 AT-002-1·AT-002-2 수용 기준에 없음 → 13번째 삭제를 검증하는 AT 부재
  - AT-007-1에 추가하면 T-05(병렬)가 T-01 결과에 의존하게 돼 "티켓 간 의존 없음"이 깨짐
- 제안
  - AT-002-1 수용 기준에 "`JwtProvider`에 `getExpiration` 없음(리플렉션)" 추가

### C-r1-04 REQ-003 대상 작업이 예외를 안에서 잡으면 `ErrorHandler`가 못 봄 — "대상 작업 수정 없음"과 충돌 가능

- 우선순위: 높음
- 문제
  - `.claude/rules/user.md`: `WithdrawnUserPurgeScheduler`는 이미 자체 실패 처리가 있음(프로필 이미지 삭제 실패 → 해당 유저 제외 + 실행당 운영 알림 1건). 같은 메서드가 `purge()` 예외도 `try/catch` + 로그로 처리하고 있다면 예외가 스케줄러까지 안 올라와 `ErrorHandler`가 호출되지 않음
  - intent의 "오류 로그 파일에만 남음"은 Spring 기본 LOG_AND_SUPPRESS와 작업 내부 catch+log 둘 다로 설명됨 → 어느 쪽인지 spec 근거 없음
  - 그 경우 AT-003-2 세 번째 케이스(purge 예외 → 알림 1회)가 실패하는데, 구현자는 대상 작업 수정 금지 + AT 수정 금지라 진행 불가
- 제안
  - 설계자가 대상 4개 작업이 예외를 밖으로 전파하는지 코드 확인 후 spec에 기재
  - 안에서 잡는 작업이 있으면 그 작업만 "잡은 뒤 다시 던지기" 또는 "같은 `sendOperationalAlert` 호출"로 최소 수정을 허용하도록 요구사항에 명시

### C-r1-05 REQ-003 `source` 상수 하나 + 예외 클래스 dedup이면 서로 다른 작업의 실패가 한 건으로 합쳐질 수 있음

- 우선순위: 중간
- 문제
  - 모든 작업이 `source = "ScheduledTask"`를 쓰므로, `DiscordAlertService` dedup 키가 `(source, exceptionClass)`류라면 2초 주기 발송 작업이 같은 예외 클래스(예: DB 연결 계열)로 계속 실패하는 동안 04:00 탈퇴 정리 실패 알림이 창 안에서 억제됨 — intent가 없애려는 "아무도 모르는 실패"가 재발
  - `ErrorHandler.handleError(Throwable)`는 작업 정보를 받지 않아 지금 설계로는 어떤 작업이 실패했는지 알림에서 구분 불가
  - dedup 키 구성은 spec에 없음(확인 필요)
- 제안
  - 실패 작업 식별자(스택 트레이스에서 앱 패키지 첫 프레임의 `클래스.메서드`)를 `summary` 또는 dedup 키에 포함하도록 요구사항에 명시. 또는 합쳐짐을 허용한다면 근거와 함께 "허용"으로 기록

### C-r1-06 REQ-003 AT-003-2가 실제 `@Scheduled` 등록 경로를 검증하지 않음 + 다른 예약 작업 호출로 횟수 검증이 흔들림

- 우선순위: 중간
- 문제
  - "`taskScheduler`로 예외 작업 실행"은 `ThreadPoolTaskScheduler.execute/schedule` 경로만 확인. `@Scheduled` 메서드가 실제로 같은(커스터마이즈된) 스케줄러에 등록되는지는 검증 안 됨 — 다른 `TaskScheduler`나 `SchedulingConfigurer`가 생기면 테스트는 통과하고 운영 알림은 안 감
  - 테스트 컨텍스트에서 스케줄링이 켜져 있으면 실제 `NotificationDispatcher`(2초 주기)가 돌며 같은 mock에 알림을 보낼 수 있어 "운영 알림 1회"가 비결정적
  - "`MockBean`" 표기 — Spring Boot 3.4에서 `@MockBean`은 deprecated, 프로젝트 테스트는 mockk 사용(`.claude/rules/auth.md`, `party.md`)
- 제안
  - AT-003-2에 "`@Scheduled` 처리기가 쓰는 스케줄러 = `ErrorHandler`가 설정된 그 빈" 단언 추가(예: `ScheduledTaskHolder`/등록된 작업의 스케줄러 확인)
  - 알림 검증은 총 횟수 대신 주입한 예외의 클래스·메시지로 인자 매칭
  - mock 방식은 "기존 테스트의 빈 mock 패턴을 따름"으로 표기

### C-r1-07 REQ-004 catch로 기록 실패를 넘기는 방식은 각 기록이 독립 트랜잭션일 때만 성립 — 전제 조건 명시 필요

- 우선순위: 높음
- 문제
  - `dispatchBatch`(또는 루프 전체)가 하나의 트랜잭션 안이면, `@Transactional` 리포지토리 프록시를 통과한 `RuntimeException`은 잡아도 바깥 트랜잭션을 rollback-only로 표시 → 2·3번 항목의 기록과 무효 토큰 비활성화가 커밋 시 `UnexpectedRollbackException`으로 전부 사라짐
  - AT-004-2는 mock 단위 테스트라 이 경우를 잡지 못하고 통과
  - 트랜잭션 구조는 spec·`notification.md`에 없음(확인 필요)
- 제안
  - 요구사항에 "`recordRetry`·DEAD 기록·`deactivateTokens`는 각각 독립 트랜잭션에서 실행됨(배치 루프는 트랜잭션 밖)"을 전제로 기재하고 설계자가 코드로 확인
  - 루프가 트랜잭션 안이라면 통합 AT(실제 트랜잭션, 기록 1건 실패 후 나머지 커밋 확인) 추가

### C-r1-08 REQ-005 AT-005-1이 "잠금 뒤 읽기가 최신 커밋을 본다"를 증명하지 못함

- 우선순위: 높음
- 문제
  - 요구사항의 핵심 제약은 "잠금 조회 전에 일반 조회 금지"(그 전에 REPEATABLE READ 스냅샷이 생기면 잠금 대기 후 파생 delete의 SELECT가 이전 마커를 읽어 중복). 그런데 AT-005-1의 잠금 보유 트랜잭션은 plan 행만 잡고 마커를 바꾸지 않음 → 잠금 앞에 일반 조회가 끼어 있어도 최종 마커 = 요청 집합이 되어 통과
  - "겹친 요청 2개" 케이스는 타이밍 의존이라 base에서도 자주 통과 — 위 결함의 결정적 증명이 안 됨
- 제안
  - AT-005-1 첫 케이스를 "보유 트랜잭션이 plan 행을 `FOR UPDATE`로 잡고 마커를 다른 집합으로 교체(삭제 + 삽입) → 요청이 잠금 대기(`performance_schema.data_lock_waits`) → 보유 트랜잭션 커밋 → 요청 성공, 최종 마커 = 요청 집합과 정확히 같음(보유 트랜잭션 마커 잔존 0)"으로 강화
  - 이 케이스가 "잠금 전 일반 조회" 회귀를 잡는다는 점을 회귀 증명 목록에 기재

### C-r1-09 엔진 gate(`./gradlew test build`)에 dockerTest가 없어 AT-005-1이 검증 경로에서 실행되지 않음

- 우선순위: 높음
- 문제
  - Project context의 gate = `["./gradlew", "test", "build"]`. AT-005-1은 `src/dockerTest`라 gate에서 돌지 않음
  - intent 성공 기준: "전체 `./gradlew test`, `./gradlew dockerTest` 통과"와 REQ-005 "dockerTest로 검증" → spec에 dockerTest 실행 의무가 없으면 T-03은 핵심 AT 미실행으로 완료 가능
- 제안
  - spec에 "T-03 검증 명령: `./gradlew dockerTest`(Docker 데몬 필요) 실제 실행 출력 첨부", "최종 통합 검증: `./gradlew test build dockerTest`" 명시
  - Docker 데몬이 없는 환경이면 완료가 아니라 blocking 항목으로 보고하도록 기재

### C-r1-10 REQ-006 구현 방식 두 개 중 하나로 고정 + 스냅샷 전제 조건 추가

- 우선순위: 중간
- 문제
  - "구현 예: `computeIfAbsent` 또는 세대 번호" — 구현자에게 설계 선택을 남김. 두 방식은 실패 모드가 다름(세대 번호는 DB 읽기 시작 전에 세대를 읽어야 하고, `computeIfAbsent`는 계산 중 같은 bin 잠금 유지)
  - `computeIfAbsent`는 "계산이 `remove`와 겹친 경우"만 막음. `checkVersion`이 트랜잭션 안에서 계산 전에 이미 일반 조회를 했다면(스냅샷 확정) → 쓰기 커밋·`remove` 완료 후 시작한 계산이 이전 스냅샷 값을 캐시에 넣어 stale 재발. AT-006-1은 mock이라 이 경우를 못 잡음
- 제안
  - `computeIfAbsent`로 고정(캐시 키 = 플랫폼 2개라 bin 잠금 비용 무시 가능, 근거 기재)
  - 제약 추가: "요약 계산에 쓰는 DB 읽기는 전부 매핑 함수 안에서 실행, `checkVersion`은 그 전에 같은 트랜잭션에서 DB를 읽지 않음"(설계자가 현재 `checkVersion`의 `@Transactional` 여부 확인 후 기재)
  - 매핑 함수 안에서 맵을 다시 수정하지 않음(`computeIfAbsent` 재귀 갱신 금지) 명시

### C-r1-11 REQ-007 AT-007-1(부재 확인 리플렉션 테스트)은 상시 유지 가치가 낮음

- 우선순위: 낮음
- 문제
  - 삭제된 메서드를 누가 다시 추가하는 것은 결함이 아님 → "12개 이름이 없어야 함"을 영구 테스트로 고정하면 이후 같은 이름의 정당한 메서드 추가 때 테스트만 깨짐(유지비)
  - 실제 결함 방지 효과는 컴파일(테스트 사용처 교체) + `./gradlew build`가 이미 제공
- 제안
  - 엔진이 AT ID를 요구해 유지한다면, 테스트 이름·주석에 "#231 정리 확인용, 메서드 재추가가 필요하면 이 테스트 삭제" 명시
  - 또는 AT 대신 T-05 검증 명령(`grep`으로 `src/` 전체 0건 확인 + `./gradlew build`)으로 대체 — 설계자 판단
