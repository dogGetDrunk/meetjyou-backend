# Spec 비평 r1

- 역할·범위: sub_designer / critique, REQ-001·REQ-002·REQ-003 검토
- 입력: `intent.md`, `spec.md`, `decisions.md`, `spec-changelog.md` 및 dispatch에 제공된 프로젝트 규칙
- 브랜치: `task/20261103-ci-speedup` (`git branch --show-current` 실제 출력)
- 판단: 구현 전 해소 권고 C-r1-01~04, 결과 판정 전 구체화 권고 C-r1-05
- 한계: 구현 파일·GitHub 실행 로그 탐색 없음, 아래 구현 반례는 명세의 허용 해석에 대한 반례이며 현 코드의 결함 확정 아님
- 변경 범위: 이 비평 파일만 작성, 승인 intent·spec·decisions 변경 없음

### C-r1-01 문서 허용 목록의 tools·scripts 예외가 승인 intent와 충돌

- 관련 요구사항·수용 테스트: REQ-001 / AT-001
- 중요도: high, 구현 전 명세 정합성 해소 필요
- 근거: `intent.md:23`의 `tools/`·`scripts/`를 하나도 건드리지 않은 PR만 생략 조건, `spec.md:9`의 `src/` 밖 `*.md` 허용 및 `spec.md:19`의 `tools/agent-pipeline/GUIDE.md` → `true` 조건과 충돌
- 추가 모순: `spec.md:10`은 `tools/**`·`scripts/**`·`.github/**`를 전체 빌드 예시로 제시하지만 상위 허용 규칙에 따르면 해당 경로의 `.md`는 생략 가능, `spec-changelog.md:5`의 생략 집합이 intent보다 좁다는 설명도 이 반례에서 불성립
- 영향: 구현자가 제외 경로 우선 또는 확장자 우선 중 임의 선택, 승인 범위를 넓힌 구현도 AT-001을 통과할 가능성
- 권고: 승인 intent를 유지하는 방향으로 강제 빌드 경로 우선순위와 허용 목록의 차집합 명시, `tools/agent-pipeline/GUIDE.md`·`scripts/notes.md`·워크플로 경로의 `.md`에 대한 `false` 사례 추가
- 트레이드오프: 일부 문서 PR의 전체 빌드 유지 비용, 승인된 생략 범위를 보존하는 이점; 생략 범위 확대가 필요하면 기존 intent 직접 수정 대신 사람 결정 게이트 대상

### C-r1-02 rename의 이전 경로 누락 시 코드 삭제를 문서 전용으로 판정 가능

- 관련 요구사항·수용 테스트: REQ-001 / AT-001
- 중요도: high, 검증 생략 오판 방지 필요
- 근거: `spec.md:12,16`은 비교 ref와 줄 단위 경로 입력만 규정, rename의 이전·이후 경로 포함 여부 미정; `spec.md:18-26`은 정적 경로 목록만 검증
- 반례: 코드 파일을 `src/main/kotlin/A.kt`에서 `docs/A.md`로 이동한 변경을 rename으로 인식하고 새 경로만 전달하는 diff 수집 방식의 경우, 실제 코드 삭제에도 `docs/A.md`만 보고 `true` 판정 가능
- 권고: rename 전후 양쪽 경로를 검사하거나 rename 검출을 끄고 삭제·추가 경로를 모두 수집하도록 계약 확정, 어느 쪽이든 코드 경로가 포함되면 전체 빌드
- 수용 보강: 임시 Git 저장소에서 코드→문서 rename, 문서→코드 rename, 문서→문서 rename, 코드 삭제에 대한 실제 diff 수집과 판정 연결 검증
- 트레이드오프: rename 검출 비활성화는 이전·이후 경로 파싱보다 단순, 문서 rename도 두 경로를 모두 검사하지만 승인 범위 안의 문서끼리 이동에는 생략 유지 가능

### C-r1-03 경로 함수 테스트만으로 워크플로의 fail-safe와 필수 검사 보존 입증 불가

- 관련 요구사항·수용 테스트: REQ-001 / AT-001
- 중요도: medium, 구현 전 검증 계약 보강 필요
- 근거: `spec.md:11-15`는 diff 실패 시 전체 빌드·정책 검사 상시 실행·main push 전체 빌드를 요구하나, `spec.md:18-29`의 테스트 목록은 경로 판정 함수와 실제 PR의 정상 경로 위주
- 공백: `HEAD^1` 접근에 필요한 checkout 이력 확보 조건, diff 명령 실패를 출력 내용과 별도로 보존하는 방식, 스크립트 실행 실패·비정상 출력·출력 부재의 후속 처리 미정
- 반례: merge parent가 없는 shallow checkout에서 diff 단계가 job을 중단하면 전체 빌드 fallback 미실행; 반대로 일부 출력 뒤 diff 실패를 놓치면 일부 문서 경로만으로 생략 가능
- 권고: 생략은 PR 이벤트·diff 성공·비어 있지 않은 전체 목록·정확한 `true` 출력이 모두 충족될 때만 허용, 나머지는 전체 빌드; diff 실패 fallback과 정책 검사 실패에 따른 job 실패를 별개 계약으로 명시
- 수용 보강: 정상 문서 PR, 코드 PR, 문서 변경 main push, merge parent 부재, diff 비정상 종료, 판정 출력 부재에 대한 step 실행표 추가; 문서 PR에서도 `Verify pipeline policy` 실패 시 체크 실패 확인
- 입력 한계: 현 workflow 파일 미검토, 현재 checkout 깊이·조건식의 결함 주장 아님

### C-r1-04 서버 전역 관찰의 격리를 검증할 수용 사례와 허용 변경 경계 부족

- 관련 요구사항·수용 테스트: REQ-002 / AT-002
- 중요도: high, 기존 동시성 검증의 의미 보존 근거 필요
- 근거: `spec.md:45-46`은 남은 커넥션에 흔들리지 않을 것을 요구하면서 허용 변경을 자기 database로 관찰 쿼리를 좁히는 데 한정, `spec.md:49-52`의 수용 기준은 빈 스키마·테스트 총량·컨테이너 수만 직접 확인
- 공백: statement digest·lock wait·deadlock 각각의 관찰 대상, database 귀속 가능 여부, 다른 database의 잡음이 있어도 자기 database의 실제 사건을 검출하는 검증 미정
- 영향: database 격리 테스트가 통과해도 서버 전역 잡음의 오검출 또는 과도한 필터의 미검출 가능성 잔존, 기존 49개 테스트 1회 성공만으로 두 방향의 회귀 판별 곤란
- 권고: 주 설계자가 각 관찰 쿼리의 귀속 기준을 확인해 spec 또는 티켓에 명시, 다른 database의 사건은 배제하고 자기 database의 실제 사건은 검출하는 AT-002 사례 추가
- 변경 경계: 관찰 자료가 database로 귀속되지 않는 경우 지원 코드가 임의로 단언 완화·초기화·테스트 시나리오 변경을 선택하지 않도록 escalation 조건 명시, 입력에 없는 대체 설계 확정 금지
- 필요한 추가 입력: `JdbcSessionStateRoundTripDockerTest` 및 `LockWaitAssertions`·`DeadlockAssertions`의 관찰 쿼리·정리 동작과 해당 호출부의 정확한 경로/발췌; 현재 입력만으로 구현 가능성 확정 불가
- 트레이드오프: 공유 컨테이너·스펙별 database 설계 유지 가능, 단 전역 상태에 대한 별도 검증 비용 발생; 컨텍스트 통합·병렬 실행 도입은 불필요

### C-r1-05 성능 측정의 실행 식별·시간 정의·증거 미확보 상태 미정

- 관련 요구사항·수용 테스트: REQ-003, REQ-001의 실제 문서 PR 확인 / AT-001 및 수동 측정
- 중요도: medium, 결과 완료 판정 전에 확정 필요
- 근거: `intent.md:10,43`의 단일 기준 run 및 3회 중앙값, `spec.md:28,59-62`의 결과 PR·main push 실행 3회 측정만 제시; 실행 선택·시간 추출·표본 부족 처리 기준 부재
- 공백: 동일 구현 SHA의 반복 실행인지 후속 변경이 포함된 main push 3건인지, 전체 CI가 job 시간인지 workflow 시간인지, queue 포함 여부와 Gradle 내부 `dockerTest` 시간 추출 방식 미정
- 권고: 메인 설계자의 측정 책임 유지, run ID·attempt·head SHA·이벤트·성공 여부·전체 CI 시간 정의·dockerTest 시간 원자료와 산출 방법을 result에 기록하도록 명시; 동일 구현 상태의 비교 가능한 표본 3개 선택 기준 확정
- 판정 구분: 유효한 표본으로 목표 미달이면 기존 spec대로 실패, 실행 3회나 문서 PR 증거 미확보는 미검증으로 남기고 성공 주장 금지; 구현 gate 통과와 효과 달성 구분
- 트레이드오프: 측정 기록만 구체화, 목표 달성을 위한 추가 최적화나 승인 없는 GitHub 쓰기·임의 실행 유발은 이번 비평의 제안 범위 밖

## 검토 결론·후속 처리

- REQ-001·REQ-002·REQ-003 모두 근거 위치·반례 또는 검증 공백·권고를 연결한 비평 완료, 구현 요구사항 충족 판정 아님
- 메인 설계자의 C-r1-01~05 각각 `수용` 또는 `기각` 및 근거 기록 필요
- `spec.md`·`decisions.md` 변경, 구현·gate 실행·성능 측정은 미수행
- 보고서 형식·원본 무변경 검증 후 coordinator에 worker_done 전달 예정
