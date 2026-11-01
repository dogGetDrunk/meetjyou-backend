---
name: task
description: >-
  agent-pipeline 개발 태스크를 시작하거나 이어갈 때 사용. 태스크 폴더(.pipeline/tasks/)와
  intent 승인을 거쳐 구현 워커까지 연결한다. 트리거: "/task", "$task", "태스크 시작",
  "다음 태스크 하자", "이 이슈 작업 시작", "intent 쓰자", 인자로 이슈 번호·작업 설명을 붙인 호출.
  질문·조사만 하는 요청에는 쓰지 않는다.
argument-hint: "[비움 | 설명 텍스트 | 이슈 번호]"
---
<!-- agent-pipeline:managed -->
# task

파이프라인 태스크 시작 신호. 인자 = 호출 시 스킬 이름 뒤에 붙은 텍스트(없으면 비움).

## 0. 전제

- `~/.local/bin/agent-pipeline guide` 확인, 프로젝트 `.pipeline/project.yaml` 없으면 전역 지침대로 init 먼저
- `git branch --show-current` 확인·보고
- 태스크 후보 출처 = 프로젝트 규칙(`CLAUDE.md`·`AGENTS.md`)이 정한 곳. 정의가 없으면 열린 이슈 + `agent-pipeline tasks`

## 1. 후보 고르기

- 인자 없음: 출처에서 내 미완료 태스크 + `agent-pipeline tasks`의 진행 중 태스크를 마감·우선순위 순으로 최대 5개 제시 → 1개 선택받기
- 텍스트: 출처·이슈·기존 태스크 폴더에서 유사 항목 검색 → 일치 후보 확인받기, 없으면 새 태스크로 확인
- 숫자: 해당 이슈 직접 조회
- 이미 `.pipeline/tasks/`에 같은 작업 폴더가 있으면 새로 만들지 말고 그 폴더에서 이어가기
- 질문은 이 단계 1회. 이후 단계 진행 중에는 아래 승인 지점만 질문

## 2. 태스크 폴더

- 키: `YYYYMMDD-<영문 slug>` (오늘 날짜, 소문자·숫자·대시)
- 크기 S/M/L 판단 + 근거 1줄 (명세 §3: S=버그·작은 변경, M=기능 하나, L=새 기능군)
- `agent-pipeline new-task <키> --title "<제목>" --size <S|M|L> [--notion <URL>] [--issue <owner/repo#N>]`
- 이슈·노션 링크는 선택. 없어도 진행

## 3. intent 작성 (메인 설계 역할)

- `.pipeline/tasks/<키>/intent.md`를 사람과 대화로 작성, 첫 줄 template 표시 삭제
- 출처 태스크·이슈 본문, 관련 코드 확인으로 문제·범위·성공 기준 채우기
- 열린 팀 결정이 있으면 intent에 그대로 기록하고 사람에게 결정 요청 — 에이전트가 대신 정하지 않음
- intent 전문을 보여주고 **승인 요청** (승인 지점 1)

## 4. 승인 기록과 base 반영

- 사람의 명시적 승인 이후에만: `agent-pipeline approve-intent <키> --approver "<이름>" --evidence "<승인 발언 원문 또는 위치>"`
- M·L: `origin/<기본 브랜치>`에서 `docs/intent-<키>` 브랜치 → 태스크 폴더 커밋 → PR → CI 통과 후 머지 (프로젝트 머지 규칙 준수)
- S: `origin/<기본 브랜치>`에서 `task/<키>` 브랜치 → 태스크 폴더 커밋 → push만 (PR 없음, 워커 PR이 이 커밋을 포함)
- `agent-pipeline tasks`에서 해당 태스크 `intent: approved` 확인 후 다음 단계

## 5. 설계·티켓

- M·L: spec.md → 보조 설계 비평(decisions.md, 최대 2라운드) → 티켓·수용 테스트, 명세 §5 순서
- S: 티켓 1개 + 수용 테스트 케이스
- 티켓 ID = `T-<키>-NN`, 파일 = `.pipeline/tasks/<키>/tickets/T-<키>-NN.md`, 같은 태스크 안에서만 depends_on
- 워커는 티켓 카드만 받으므로 spec·티켓이 base에 있을 필요 없음 — 승인 검사 대상은 intent·approval뿐
- S: 티켓을 `task/<키>`에 push한 뒤, 태스크 폴더가 보이는 `task/<키>` checkout(worktree)에서 coordinator 실행 (워커 PR 하나에 문서·코드 포함)
- M·L: coordinator 실행, spec·티켓·logs는 종료 시 결과 PR로 반영

## 6. 외부 상태·종료

- 팀 보드 상태 변경(예: 진행 중)은 쓰기 작업이라 실행 직전 확인 (승인 지점 2)
- 태스크 종료 시 `result.md` 작성: 머지 PR·모델·라운드·핵심 결정과 기각 대안
- M·L: `docs/result-<키>` PR로 spec·decisions·티켓·logs·result를 base에 반영 (태스크당 PR = intent 1 + 구현 N + 결과 1)
- S: result.md를 마지막 워커 PR 머지 후 짧은 docs 커밋으로 반영하거나, 다음 태스크의 intent PR에 포함
