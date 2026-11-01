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
