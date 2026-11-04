# Spec

## 요구사항

### REQ-001 문서 전용 PR은 Gradle 단계 생략

- 요구사항:
  - `pull_request` 실행에서 PR이 바꾼 파일이 **전부** 허용 목록에 속할 때만 "문서 전용"으로 판정
    - 허용 목록 (이것만): `docs/**`, `.pipeline/tasks/**`, 저장소 루트의 `*.md`, `.claude/rules/**/*.md`
    - 그 외 경로가 1개라도 있으면 전체 빌드 — 확장자와 무관 (예: `src/**`, `tools/**/*.md`, `scripts/*.md`, `.github/**`, `config/**`, `build.gradle.kts`, `.pipeline/project.yaml`)
  - 판정 스크립트 `scripts/ci-docs-only.sh <base> <head>`
    - 변경 경로 수집: `git diff --no-renames --name-only <base> <head>` (rename은 삭제 경로 + 추가 경로로 둘 다 검사)
    - stdout: 정확히 한 줄 `true` 또는 `false`, exit 0
    - `false`로 처리: git 명령 실패(잘못된 ref, 이력 없음), 변경 경로 0개, 허용 목록 밖 경로 1개 이상
  - 워크플로 (`.github/workflows/ci.yml`)
    - `pull_request`: checkout 이력은 merge commit과 그 첫 부모를 비교할 수 있을 만큼 확보(예: `fetch-depth: 2`), 판정 step이 `scripts/ci-docs-only.sh HEAD^1 HEAD` 실행
    - 판정 step은 `continue-on-error: true` — 스크립트 부재·실패 시에도 job은 전체 빌드로 진행
    - Gradle 관련 step(JDK 설정·`./gradlew build dockerTest`·테스트 리포트 업로드) 생략 조건 = `github.event_name == 'pull_request'` **그리고** 판정 step 출력이 정확히 `true`. 그 외 모든 경우 실행
    - `push: main`은 판정 step을 돌리지 않고 항상 전체 빌드
    - `Verify pipeline policy` step은 조건 없이 항상 실행, 실패하면 문서 전용 PR에서도 job 실패 (현행 유지)
    - 판정 스크립트 테스트(`scripts/ci-docs-only.test.sh`)를 `Verify pipeline policy` step에서 매 실행 돌림
    - job 이름 `build-and-test` 유지 (필수 체크)
- 수용 기준:
  - 판정 스크립트: 임시 Git 저장소에서 커밋을 만들어 아래 결과 확인
    - `docs/a.md`·`.pipeline/tasks/x/intent.md`·`README.md`·`.claude/rules/party.md` 수정만 → `true`
    - 위 + `src/main/kotlin/A.kt` → `false`
    - `src/main/resources/notes.md` 단독 → `false`
    - `tools/agent-pipeline/GUIDE.md` 단독 → `false`
    - `scripts/notes.md` 단독 → `false`
    - `.github/workflows/README.md` 단독 → `false`
    - `config/detekt/baseline.xml` 단독 → `false`
    - `build.gradle.kts` 단독 → `false`
    - `.pipeline/project.yaml` 단독 → `false`
    - rename `src/main/kotlin/A.kt` → `docs/A.md` → `false`
    - rename `docs/A.md` → `src/main/kotlin/A.kt` → `false`
    - rename `docs/a.md` → `docs/b.md` → `true`
    - `src/main/kotlin/A.kt` 삭제 단독 → `false`
    - base와 head가 같음(변경 0개) → `false`
    - 존재하지 않는 ref → `false`, exit 0
  - 실제 실행 확인 (메인 설계자, 머지 후 result에 run ID 기록)
    - 문서 전용 PR 1건(이 태스크의 결과 PR): CI 1분 이내, `build-and-test` 성공, Gradle step 생략 표시
    - 코드 PR 1건(T-02 구현 PR): Gradle step 실행
- 수용 테스트 ID: AT-001

### REQ-002 dockerTest MySQL 컨테이너 공유

- 요구사항:
  - 공유 지원 코드 `src/dockerTest/java/com/dogGetDrunk/meetjyou/support/SharedMySql.kt`
    - 테스트 JVM당 `mysql:8.0.41` 컨테이너 1개 지연 기동, 이미지 태그 상수는 이 파일 한 곳에만
    - `max_connections`를 기본 151보다 넉넉하게(예: 500) — 캐시된 Spring 컨텍스트 풀이 JVM 종료까지 열려 있음
    - `newDatabase()`: 호출마다 이름이 겹치지 않는 빈 database 생성 후 접속 정보 반환
      - jdbcUrl(그 database 지정)·username·password, performance_schema용 root 접속
      - `countLockWaits()`: 이 database의 객체에 걸린 락 대기만 셈 — `performance_schema.data_lock_waits`를 `data_locks`(`REQUESTING_ENGINE_LOCK_ID` = `ENGINE_LOCK_ID`)와 조인해 `OBJECT_SCHEMA` = 자기 database로 한정
    - 컨테이너 종료는 Testcontainers(Ryuk)에 맡김, 스펙이 공유 컨테이너를 `stop()`하지 않음
  - 기존 dockerTest 16개 스펙 교체
    - `MySQLContainer(...)` 생성·`start()`·`stop()` → `SharedMySql.newDatabase()`
    - Spring 스펙: `@DynamicPropertySource`가 새 database 접속 정보 등록
    - 비 Spring 스펙: `Flyway`·`DriverManager`가 새 database 접속 정보 사용
    - `MySQLContainer<*>`를 받던 private 헬퍼는 접속 정보 타입을 받도록 시그니처만 변경
    - `SELECT COUNT(*) FROM performance_schema.data_lock_waits`를 쓰는 6개 스펙(`PostLockFreshRead`·`PostUpdatePartyCompleteDeadlock`·`PartyAggregateDeadlock`·`SocialAccountConcurrency`·`RefreshTokenRotationConcurrency`·`UserUniqueRace`)은 `countLockWaits()`로 교체
    - `JdbcSessionStateRoundTripDockerTest`의 digest 조회(`SELECT_DIGESTS`)에 `SCHEMA_NAME` = 자기 database 조건 추가
    - `SHOW ENGINE INNODB STATUS`(`PartyAggregateDeadlockDockerTest`)는 로그 진단용이라 변경 없음 — 다른 스펙의 교착이 로그에 보일 수 있음, 허용
  - 변경 금지: 각 테스트의 시나리오·단언·상수 값, 스펙 수·테스트 수
  - 관찰을 자기 database로 좁힐 수 없는 경우가 나오면 단언 완화·초기화 추가·시나리오 변경으로 우회하지 말고 escalation
  - 스펙 실행은 지금처럼 순차 (Kotest 스펙 병렬 실행 설정 추가 금지)
- 수용 기준:
  - 공유 지원 코드 검증 (`SharedMySqlDockerTest`)
    - `newDatabase()` 두 번 → 같은 호스트·포트, 서로 다른 database 이름, 둘 다 테이블 0개
    - 한쪽에 Flyway 마이그레이션 적용 후 다른 쪽은 여전히 테이블 0개
    - database X에서 행 락을 잡고 다른 트랜잭션이 대기 중일 때: X의 `countLockWaits()` ≥ 1, database Y의 `countLockWaits()` = 0
  - `./gradlew dockerTest`: 기존 17개 스펙·49개 테스트 + `SharedMySqlDockerTest` 전부 통과, skip 0
  - `src/dockerTest` 안에 `MySQLContainer(` 생성 코드가 `SharedMySql.kt` 외에 없음
  - `src/dockerTest` 안에 서버 전역 `FROM performance_schema.data_lock_waits` 카운트가 `SharedMySql.kt` 외에 없음
- 수용 테스트 ID: AT-002

### REQ-003 개선 효과 측정

- 요구사항: 머지 후 효과를 숫자로 확인해 result.md에 기록 (메인 설계자)
- 측정 정의:
  - 전체 CI 시간 = `build-and-test` job의 startedAt→completedAt (큐 대기 제외)
  - dockerTest 시간 = Gradle 로그의 `> Task :dockerTest` 줄 타임스탬프부터 다음 `> Task` 줄까지
  - 표본 = T-02가 포함된 base 위에서 성공한 코드 변경 실행(코드 PR 또는 main push) 3건, run ID·attempt·head SHA·이벤트 기록
  - 기준값 = run 41470111845 (job 405s, dockerTest 3m03s)
- 수용 기준:
  - dockerTest 중앙값 1m30s 이하, 전체 CI 중앙값 5분 이하
  - 유효 표본으로 미달이면 실패로 기록 + 남은 병목을 후속 과제로 기록, 목표 달성용 추가 최적화는 이 태스크에서 하지 않음
  - 태스크 종료 시점까지 표본 3건 미확보면 "미검증"으로 기록, 성공 주장 금지
- 수용 테스트 ID: 없음

## 확정 인터페이스

### API 시그니처·DTO

- `scripts/ci-docs-only.sh <base> <head>` → stdout `true`|`false` 한 줄, exit 0
- `scripts/ci-docs-only.test.sh` → 모든 사례 통과 시 exit 0, 실패 시 사례 이름 출력 후 exit 1
- `SharedMySql.newDatabase()` → 접속 정보 객체 (jdbcUrl·username·password·root 접속·`countLockWaits()`), 타입·필드 이름은 구현 재량

### DB 스키마

- 변경 없음 (테스트용 database 이름만 런타임 생성)

## 기각한 대안

- 워크플로 `paths-ignore`: 문서 PR에서 필수 체크 `build-and-test`가 보고되지 않아 머지 불가
- `dorny/paths-filter` 등 서드파티 action: 외부 의존 추가, 로컬·단위 테스트 불가
- 판정 스크립트가 파일 목록만 stdin으로 받는 방식: rename·diff 실패 처리를 테스트로 검증할 수 없음 (C-r1-02·03)
- Testcontainers reuse(`withReuse`)·`@ServiceConnection`: reuse는 CI 매 실행이 새 러너라 이득 없음, `@ServiceConnection`은 비 Spring 스펙에 적용 불가
- 스펙마다 `TRUNCATE`로 같은 database 재사용: 마이그레이션 대상 버전 테스트가 빈 스키마에서 시작해야 해서 부적합
- 워크플로 실패 주입 테스트(일회용 PR로 diff 실패·출력 부재 재현): 비용 대비 이득 낮음, 스크립트 단위 테스트 + 실제 실행 2건으로 대체

## 마일스톤

- T-01: REQ-001 (판정 스크립트 + 스크립트 테스트 + 워크플로)
- T-02: REQ-002 (공유 지원 코드 + 16개 스펙 교체)
- T-01·T-02는 서로 독립, 병렬 진행 (touches 겹침 없음: T-01은 `.github/`·`scripts/`, T-02는 `src/dockerTest/`)
- REQ-003: 두 티켓 머지 후 메인 설계자가 측정
