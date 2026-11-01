# 개발 파이프라인 구축 상태

- 기준: [원본 명세](agent-pipeline-spec.md) §12
- 현재 단계: 배정 정책 + Orca 실행 어댑터, 실제 더미 라우팅 리허설
- agents.yaml 원문 기본값: 2026-11-01 사용자 승인, `logs/defaults-approval.json`에 해시 기록
- 실행 의존성: Python 3 + `PyYAML==6.0.3`, Bash, Gradle wrapper
- 원본 명세는 보존본, 구축 중 확정한 차이는 아래 기록 우선

## 사용법

```bash
python3 -m pip install -r .pipeline/requirements.txt
scripts/dispatch.sh .pipeline/tickets/T-001.md --model claude-sonnet-5-5
scripts/dispatch.sh .pipeline/tickets/T-001.md --capacity high --failures 2
scripts/gate.sh
```

- dispatch 기본 동작: JSON 배정 계획 출력, 외부 쓰기 없음
- `--execute --run <run_id> --repo id:<repo_id>`: ready 티켓 실행, 승인 해시·의존성·동시성 검사 및 중복 claim 차단
- `python3 scripts/pipeline_watch.py --dispatch <id>`: timeout/heartbeat·liveness 조사, 종료 확인 전 재분배 금지
- 실패 횟수: 해당 티켓 등급에서의 테스트 실패 + blocking 리뷰 누적값
- 승급 시 새 등급에서 실패 횟수 초기화, 리뷰어·머지 판단자 재배정
- high: high → xhigh → max → 메인 설계 escalation
- gate: 정책 테스트 후 `./gradlew test build`, 첫 실패 종료 코드 전파
- `build`: detekt·Konsist·skipped-test guard 포함

## 확정 차이

- 2026-11-01 사용자 승인: timeout/heartbeat 부재 시 상태 조사, 기존 워커 종료 확인 후 재분배
- 로컬 Orca 도움말: `orchestration run` 없음, `run-create` + `worker-start` 지원
- 현재 Orca skill: `dispatch --inject`보다 `worker-start` 권장
- live preamble의 task/dispatch ID와 heartbeat 주기가 워커 계약의 권위

## 미완료 항목

- agents.yaml 기본값 최초 사람 승인: 완료
- 모델별 실제 계정 노출·지원 effort 검증: CLI help의 전체 옵션만으로 모델별 지원을 추정하지 않을 것
- Orca Run/DAG·동시성 2·dispatch receipt 저장·중복 실행 방지·heartbeat 감시·종료 확인 후 재분배 통합
- gate → 교차 리뷰(최대 3회) → 판단자 → PR/CI/머지의 자동 상태 전이
- 승인된 수용 테스트/intent 변경 탐지 및 가드레일의 실행 차단
- 보호 브랜치·필수 CI 설정 확인
- 상·중·하 실제 Orca 라우팅 리허설: `logs/rehearsal.md` 참조
- timeout·heartbeat 부재의 결정 로직은 단위 테스트, 실제 강제 장애 E2E는 미완료
- 역할별 한도 측정은 실제 관측값 확보 후 usage.md 기록

## 검증 근거

- `.pipeline/tests/test_dispatch.py`: 배정·반대 진영·하향 픽업·승급·effort·잘못된 입력·더미 티켓 CLI 검증
- 더미 티켓 검증은 오프라인 정책 검증이며 실제 Orca E2E 리허설 아님
- 역할 프롬프트의 금지 규칙은 지시사항이며 OS 수준 권한 강제를 대체하지 않음

## CLI 검증 출처

- Codex effort 설정 키: `model_reasoning_effort`, 모델·클라이언트별 지원 단계 상이
- 공식 문서: https://learn.chatgpt.com/docs/config-file/config-reference
- `codex --help`: `-c key=value`, `--model` 확인
- `claude --help`: `--effort low/medium/high/xhigh/max` 확인
- 로컬 Claude Code: 2.1.316, 명세의 최소 버전 2.1.293 이상
- Orca 웹 문서는 조회 실패, 설치 CLI의 help와 version-matched skill로 현재 명령 확인
