# Intent

> 메인 설계 에이전트가 사람과 대화로 작성. 작성을 마치면 첫 줄의 template 표시 삭제.
> 승인 기록은 `approval.json`(`agent-pipeline approve-intent`), 승인 후 이 파일 수정 금지.

## 해결할 문제

- CI(`build-and-test`) 1회 약 5.3~7.7분(최근 20회), 필수 체크 + strict(up-to-date) 설정이라 PR마다 이 시간만큼 머지 대기
- 파이프라인 태스크 1건당 CI가 최소 intent PR·구현 PR·결과 PR + 각 main push로 여러 번 돎 — 대기 시간이 태스크 처리량을 직접 깎음
- 측정(run 41470111845, Gradle 6m21s): 컴파일·detekt 약 1m50s → `dockerTest` 약 3m03s → `test` 약 1m27s, 한 Gradle 호출 안에서 순차 실행
- `dockerTest` 병목 원인: 스펙 17개 중 16개가 각자 `MySQLContainer`를 새로 띄우고(대부분 `companion object`/`beforeSpec`), Spring 스펙은 컨텍스트도 따로 생성
  - 로컬 측정(2026-11-03): 테스트 본문 실행 합계 14.1초, 스펙 간 준비 시간이 나머지 약 2분 → 시간의 약 90%가 컨테이너·컨텍스트·마이그레이션 준비
- 문서 전용 PR(intent·result PR 등)도 코드 PR과 똑같이 전체 Gradle 빌드 실행 — 최근 머지 PR 40건 중 7건이 `docs/`·`.pipeline/tasks/`·`.md`만 변경

## 의도

- 검증 범위를 줄이지 않고 대기 시간만 줄임: 같은 테스트가 같은 조건으로 돌고, 줄어드는 건 반복 준비 비용과 불필요한 실행뿐
- 측정으로 확인된 병목 2곳만 손댐, 추측성 최적화는 하지 않음

## 포함 범위

- A. 문서 전용 변경 PR은 Gradle 단계 생략
  - `src/`·빌드 설정(`build.gradle.kts`·`gradle/`·`gradle.properties`·`settings.gradle.kts`)·워크플로·`tools/`·`scripts/`를 하나도 건드리지 않은 PR만 해당
  - 필수 체크 `build-and-test`는 그대로 성공으로 보고(job 자체는 실행, Gradle step만 건너뜀) — 브랜치 보호 설정 변경 없음
  - `push: main` 실행은 항상 전체 빌드 유지 (머지 후 안전망)
- B. `dockerTest`의 MySQL 컨테이너를 테스트 JVM당 1개로 공유
  - 스펙 간 격리는 스펙마다 새 database(빈 스키마)로 유지 — 마이그레이션 대상 버전 테스트·락 대기 검증이 지금과 같은 초기 상태에서 시작
  - 기존 테스트의 단언·시나리오는 변경 금지, 컨테이너 획득 방식만 교체

## 제외 범위

- job 분리(`test`·`dockerTest` 병렬 job): 필수 체크 이름 변경 또는 집계 job 필요, B 이후 이득이 줄어듦 — B 결과를 보고 별도 판단
- `gradle/actions/setup-gradle`·원격 빌드 캐시: 코드 PR은 컴파일부터 다시 돌아 이득 불확실, 문서 PR은 A로 해결
- Gradle configuration cache·테스트 병렬 실행(`maxParallelForks`): 플러그인·리스너 호환성 위험 대비 근거 부족
- Spring 컨텍스트 통합(`@MockBean` 조합 정리 등): 범위 큼, B 이후 남은 병목이면 후속 태스크
- 브랜치 보호 설정(strict 등) 변경: 저장소 설정, 사람 결정 사항
- 단위 `test` 태스크 속도 개선

## 성공 기준

- A: 문서 전용 PR의 CI가 1분 이내 완료, `build-and-test` 체크 성공 표시 — 실제 문서 전용 PR 1건으로 확인
- A: `src/` 파일을 1개라도 바꾼 PR은 지금처럼 전체 빌드 실행 — 워크플로 조건 검증(코드 PR 실행 로그)
- B: CI의 `dockerTest` 태스크 3m03s → 1m30s 이하, 코드 PR 전체 CI 6분대 → 5분 이하 (머지 후 main push 실행 3회 중앙값)
- B: `dockerTest` 테스트 수 변화 없음(현재 17개 스펙·49개 테스트), skip 0, 전부 통과
- `./gradlew build dockerTest` 로컬·CI 통과

## 작업 크기

M — 독립된 변경 2개(워크플로 조건 / 테스트 지원 코드 + dockerTest 16개 파일), 티켓 2개 예상. checkout 시 S로 생성, 승인 시 M으로 정정

## 사람 결정

- 범위: A+B 둘 다 (2026-11-03 사용자 결정). 기각: A만(코드 PR 대기 그대로), B만(문서 PR 대기 그대로)
- 크기: M

## 출처

- 노션 태스크: 없음 (사용자 직접 요청, 2026-11-03)
- GitHub 이슈: 없음
