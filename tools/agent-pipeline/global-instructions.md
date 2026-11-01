<!-- agent-pipeline:begin -->
## 전역 에이전트 개발 파이프라인

- 모든 프로젝트 개발 작업의 기본 프로세스: `~/.local/bin/agent-pipeline guide`를 읽고 공통 파이프라인 적용
- 프로젝트별 명시 규칙 우선, 질문·설명·조사만 하는 요청은 워커 실행 대상 제외
- 프로젝트 `.pipeline/project.yaml`이 없으면 기존 프로젝트 규칙에서 테스트·빌드 명령과 원격 기본 브랜치 확인 후 `agent-pipeline init` 수행
- 공통 엔진·모델·역할 지시서는 전역 설치본 사용, 프로젝트별 복사·정책 분기 금지
- 태스크 문서 보관: `.pipeline/tasks/<YYYYMMDD-slug>/`(intent·approval·spec·결정·티켓·수용 테스트·logs·result), git으로 코드와 함께 버전 관리
- 태스크 시작 신호: `/task [비움|텍스트|이슈 번호]` — 절차는 명령 본문
- `/task` 없이 코드 수정이 필요한 구현 요청 + 해당 작업의 승인된 태스크 없음 → 수정 전 1회 확인: "태스크(intent)로 진행 / S 예외로 바로 수정"
  - 이 확인이 스펙 카드를 대체, 같은 작업에서 추가 질문 없음
  - S 예외: 오타·단일 함수·명백한 버그처럼 되돌리기 쉬운 수정, 기존 스펙 카드 생략 기준과 동일
- 승인 = 사람 승인 기록(`approve-intent`) + intent·approval이 태스크 base ref에 반영(M·L: intent PR 머지, S: `task/<키>` push), 엔진이 바이트 일치 검사
- 공통 모델 승인과 태스크 intent 승인 구분, 다른 태스크·프로젝트로 승인 전용 금지
- 구현·gate·반대 진영 리뷰·등급별 판단·CI 확인 후 머지, timeout/heartbeat 부재 시 종료 확인 후 재분배
- 최초 구축·엔진 자체 변경은 엔진 검증 테스트와 적용 프로젝트 검증을 직접 수행, 자기 수정을 위한 재귀 coordinator 실행 금지
<!-- agent-pipeline:end -->
