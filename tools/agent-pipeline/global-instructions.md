<!-- agent-pipeline:begin -->
## 전역 에이전트 개발 파이프라인

- 모든 프로젝트 개발 작업의 기본 프로세스: `~/.local/bin/agent-pipeline guide`를 읽고 공통 파이프라인 적용
- 프로젝트별 명시 규칙 우선, 질문·설명·조사만 하는 요청은 워커 실행 대상 제외
- 프로젝트 `.pipeline/project.yaml`이 없으면 기존 프로젝트 규칙에서 테스트·빌드 명령과 원격 기본 브랜치 확인 후 `agent-pipeline init` 수행
- 공통 엔진·모델·역할 지시서는 전역 설치본 사용, 프로젝트별 복사·정책 분기 금지
- 프로젝트별 보관: intent·spec·티켓·수용 테스트·실행 로그·gate 명령·기본 브랜치
- 사람 intent 승인 후 설계·수용 테스트·ready 티켓 준비, Orca Run에서 coordinator 실행
- 공통 모델 승인과 프로젝트 intent 승인 구분, 기존 프로젝트 승인 다른 프로젝트로 전용 금지
- 구현·gate·반대 진영 리뷰·등급별 판단·CI 확인 후 머지, timeout/heartbeat 부재 시 종료 확인 후 재분배
- 최초 구축·엔진 자체 변경은 엔진 검증 테스트와 적용 프로젝트 검증을 직접 수행, 자기 수정을 위한 재귀 coordinator 실행 금지
<!-- agent-pipeline:end -->
