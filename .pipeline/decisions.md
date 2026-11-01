# Spec 비평 결정

| 라운드 (최대 2) | 항목 | 수용/기각 | 근거 | 메인 설계자 |
|---|---|---|---|---|

## 구축 결정

- D-001: 2026-11-01 사용자 승인 — timeout·heartbeat 부재 시 상태 조사, 종료 확인 후 재분배; 부재만으로 중복 워커 실행하는 원문 §11 조건 대체
- D-002: 설치 Orca 1.4.245의 Run은 namespace·inbox이며 scheduler 미제공; run-create·worker-start와 별도 결정적 실행 루프 조합, 존재하지 않는 orchestration run 호출 기각
- D-003: git-common-dir 아래 티켓 ID 기준 공유 claim·원자적 ledger·flock 사용; Run/checkout별 ledger의 중복 실행 허점 때문에 기각
- D-004: 실행 응답 유실 시 unknown 상태 유지·자동 재실행 금지; 진행성보다 중복 편집 방지 우선, receipt 조사 후 복구
- D-005: YAML 원문 모델 배정 유지 및 PyYAML 버전 고정; shell 정규식 기반 YAML 해석의 형식 의존성 회피
- D-006: gate 통과·교차 리뷰·판단·CI를 동일 commit SHA에 연결; HEAD 조회만으로 미커밋 코드의 검증을 대체하는 방식 기각

- D-007: 모든 프로젝트 공통 적용 요구 — 엔진·정책·역할 프롬프트를 전역 CLI로 분리, 프로젝트에는 project.yaml·작업 상태만 보관; 복사 배포의 정책 drift 기각
- D-008: 설치본은 checkout 독립 release, 공통 소스 보관처는 tools/agent-pipeline; 별도 원격 저장소 생성은 이번 범위 제외
- D-009: gate argv·remote tracking base_ref·merge_method는 프로젝트 명시 설정, Gradle·origin/main 추정 fallback 금지

- D-010: 2026-11-02 사용자 승인 — intent를 태스크당 1개로, `.pipeline/tasks/<YYYYMMDD-slug>/`에 문서 일체 보관·git 버전 관리; 프로젝트당 단일 intent.md는 두 번째 태스크부터 "승인 intent 수정 금지"와 충돌해 기각, 노션·별도 문서 레포는 코드 SHA와 이력 분리로 기각
- D-011: 승인 = approval 해시 일치 + intent·approval이 태스크 base ref에 바이트 동일 존재(엔진 검사); M·L은 intent PR 머지, S는 `task/<키>` 브랜치 push 후 워커가 그 브랜치에서 분기해 PR 1개; 통합 브랜치 stacked PR은 이 레포의 하위 PR 닫힘·base≠main CI 미실행 이력으로 기각, 문서를 첫 구현 PR에 섞는 방식은 승인~머지 사이 승인본이 로컬에만 있어 기각
- D-012: 티켓 ID `T-<태스크 키>-NN` 강제; claims·coordinator 상태가 티켓 ID 키라 태스크마다 `T-001`이면 과거 merged claim과 충돌, ledger 스키마 변경보다 ID 규칙이 변경 범위 작음
- D-013: 시작 신호 `/task [비움|텍스트|번호]` + 명령 없는 구현 요청 시 1회 확인(스펙 카드 대체); 이슈 번호 필수안은 사용자 부담·GitHub 종속으로 기각, 대화 추론은 오탐, 노션 상태 감시는 로컬 webhook 부재로 기각; PreToolUse hook 강제는 우회 관측 전까지 보류
- D-014: Claude Max 5x 전환 — 상 구현 Sonnet→Opus, 중 머지 판단 Sol→Opus; 교차 리뷰 규칙 유지, Claude 구현 비중을 늘리면 리뷰 담당 ChatGPT Plus가 병목이라 중 구현 선착순 배정 유지, usage.md 실측 후 재보정
