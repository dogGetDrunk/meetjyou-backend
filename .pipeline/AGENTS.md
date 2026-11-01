# Pipeline agent contract

## Pipeline guardrails

- 파이프라인 공통 계약: `agent-pipeline guide` 및 전역 설치본 `defaults/prompts/roles/`·`defaults/agent-pipeline-spec.md` §10
- 운영 배포·운영/공유 DB migration·force push·보호 브랜치 직접 push·시크릿 접근 금지
- 승인된 intent 수정 금지, 변경 필요 시 사람 결정 게이트
- 구현 에이전트의 수용 테스트 수정 금지, 오류 의심 시 escalation
- task/dispatch ID는 Orca live preamble 기준, 실패 포함 worker_done 정확히 1회
- timeout/heartbeat 부재만으로 중복 워커 시작 금지, 종료 확인 후 재분배
