# 결과

## 결과 요약

- A: 문서 전용 PR은 Gradle 단계 생략, 필수 체크 `build-and-test`는 job 실행·성공 보고 유지 (PR #257)
- B: dockerTest MySQL 컨테이너를 테스트 JVM당 1개로 공유, 스펙마다 새 database로 격리 (PR #258)
- 효과 (표본 3건 중앙값)
  - dockerTest 183s → **73s** (목표 90s 이하 달성)
  - 전체 job 405s → **318s** (목표 300s 이하 **미달**, 18s 초과)
- 브랜치 보호·필수 체크 이름 변경 없음

## 머지된 PR

| 티켓 | PR | 등급 | 구현 / 리뷰 모델 | 라운드 |
|---|---|---|---|---|
| T-20261103-ci-speedup-01 | #257 (merge `eb14083`) | high | claude-opus-5-5(medium) / gpt-6.1-sol(high), 머지 판단 claude-opus-5-5 | 구현 1회, 리뷰 1회 |
| T-20261103-ci-speedup-02 | #258 (merge `2ce9edd`) | high | claude-opus-5-5(medium) / gpt-6.1-sol(high), 머지 판단 claude-opus-5-5 | 구현 1회, 리뷰 1회 |

- 설계: spec 비평 gpt-6.1-sol 1라운드 (C-r1-01~05 전부 수용, C-r1-03 일부 기각), r2 생략
- 수용 테스트: gpt-6.1-terra, AT-002 1회 재작성 (첫 결과가 JUnit → 프로젝트 관례 Kotest BehaviorSpec로 AT-002 보완 후 재실행)
- T-01 머지 경로: coordinator 머지 직전 CI 실패(run 41661892311)로 blocked
  - 실패 = `ChatAccessRevocationIntegrationTest` 간헐 실패, PR 변경 파일(`ci.yml`·`scripts/` 2개)과 무관
  - update-branch 재실행(run 41732228113) 성공 후 메인 설계자 머지, `unblock --merged-pr 257` 기록

## 핵심 결정과 기각한 대안

- 범위 A+B (사용자 결정) / 기각: A만(코드 PR 대기 그대로), B만(문서 PR 대기 그대로)
- 문서 판정 = job 안 판정 step + Gradle step 조건부 생략 / 기각: `paths-ignore`(필수 체크가 보고되지 않아 머지 불가), `dorny/paths-filter`(외부 의존·로컬 테스트 불가)
- 허용 목록 = `docs/**`·`.pipeline/tasks/**`·루트 `*.md`·`.claude/rules/**/*.md`만 / 기각: `src/` 밖 모든 `*.md`(승인 intent보다 넓음, C-r1-01)
- rename은 `--no-renames`로 이전·이후 경로 모두 검사 / 기각: 새 경로만 검사(코드→문서 이동을 문서 전용으로 오판, C-r1-02)
- 판정 실패 = 전체 빌드 (fail-safe, `continue-on-error`) / 기각: 워크플로 실패 주입 테스트(일회용 PR 비용, C-r1-03 일부)
- 컨테이너 공유 + 스펙별 새 database / 기각: Testcontainers reuse(CI 러너가 매번 새로 떠 이득 없음), `@ServiceConnection`(비 Spring 스펙 적용 불가), `TRUNCATE` 재사용(마이그레이션 버전 테스트가 빈 스키마 필요)
- 락 대기 관찰을 자기 database로 한정 (`data_lock_waits` ⨝ `data_locks.OBJECT_SCHEMA`), digest 조회에 `SCHEMA_NAME` 조건 / 기각: 단언 완화·초기화 추가(검증 의미 훼손, C-r1-04)
- 제외 유지: job 분리, setup-gradle 캐시, configuration cache, 테스트 병렬, 컨텍스트 통합, 브랜치 보호 변경

## spec 대비 검증

### REQ-001

- AT-001 판정 스크립트 15개 사례: `Verify pipeline policy` step에서 매 실행 통과
- 코드 PR 전체 빌드: PR #257 run 41732228113 — `Docs-only classification: false`, JDK·Gradle·리포트 step 실행
- 문서 전용 PR: 이 결과 PR — 아래 "문서 전용 PR 실행" 참조

### REQ-002

- AT-002 `SharedMySqlDockerTest` 포함 dockerTest 전부 통과 (PR #258 gate·CI)
- `MySQLContainer(` 생성·서버 전역 락 대기 카운트가 `SharedMySql.kt` 외에 없음 (리뷰 확인)

### REQ-003 측정

- 정의: job = `build-and-test` startedAt→completedAt, dockerTest = `> Task :dockerTest` 줄 → 다음 `> Task` 줄
- 기준: run 41470111845 (PR, `5ce62b9`) — job 405s, dockerTest 183s

| run ID | attempt | head SHA | 이벤트 | job | dockerTest |
|---|---|---|---|---|---|
| 41664786475 | 1 | `2ce9edd` | push main (#258 머지) | 318s | 72s |
| 41732228113 | 1 | `0c9d7e6` | pull_request (#257) | 333s | 134s |
| 41797314058 | 1 | `eb14083` | push main (#257 머지) | 307s | 73s |
| **중앙값** | | | | **318s** | **73s** |

- dockerTest: 중앙값 73s ≤ 90s → **달성** (−60%)
- 전체 job: 중앙값 318s > 300s → **미달** (−21%, 목표까지 18s)
- 참고: PR 실행의 dockerTest 134s는 같은 코드 기준 main 실행(72·73s) 대비 이상치, 러너 편차로 추정(미검증)
- spec대로 목표 달성용 추가 최적화는 하지 않음 → 남은 병목은 후속 작업으로

### 남은 병목 (run 41797314058 기준)

- 체크아웃·Python·정책 검사·JDK 설정: 약 21s
- Gradle 시작 → `dockerTest` 시작 전(detekt·컴파일): 약 118s
- `dockerTest` 이후 단위 `test` 등: 약 56s

### 문서 전용 PR 실행

- (결과 PR CI 완료 후 기록)

## 남은 후속 작업

- 전체 CI 5분 목표: 남은 시간의 대부분이 컴파일·detekt(약 2분), job 분리(`test`·`dockerTest` 병렬)나 Gradle 캐시를 별도 태스크로 판단
- `ChatAccessRevocationIntegrationTest` 간헐 실패: `afterEach`의 `chatTestDataHelper.cleanup()`이 FK 제약 위반(H2), 다음 테스트 `beforeEach`까지 연쇄 실패 — 세션 종료 비동기 처리와 정리 순서 경합으로 추정, 미조사
- 엔진: 티켓이 `pending`으로 남으면 coordinator가 경고 없이 idle — damiannlee/agent-pipeline#21
