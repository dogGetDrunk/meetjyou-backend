# 에이전트 개발 파이프라인

- 기준: [원본 명세](agent-pipeline-spec.md) §12
- 설정: [agents.yaml](agents.yaml), 2026-11-01 원문 기본값 사용자 승인
- 의존성: Python 3.9 이상, `PyYAML==6.0.3`, Bash, Orca, gh, Gradle wrapper
- 구축 결정: [decisions.md](decisions.md), 실제 리허설: [logs/rehearsal.md](logs/rehearsal.md)

## 실행

```bash
python3 -m pip install -r .pipeline/requirements.txt
scripts/dispatch.sh .pipeline/tickets/T-001.md
scripts/dispatch.sh .pipeline/tickets/T-001.md --model claude-sonnet-5-5 --failures 2
scripts/gate.sh
python3 scripts/pipeline_coordinator.py --run RUN_ID --repo id:REPO_ID --once
python3 scripts/pipeline_coordinator.py --run RUN_ID --repo id:REPO_ID --interval 30
```

- dispatch 기본값: 배정 JSON 출력, 외부 쓰기 없음
- `dispatch --execute --run RUN_ID --repo id:REPO_ID`: 승인·ready·의존성·공유 소유권·동시성 검사 후 실제 실행
- 루프: ready 티켓 → 구현 → 실제 worker worktree의 gate → 반대 진영 리뷰 → 등급별 판단 → CI 확인 → merge
- 구현자: 검증·작업 브랜치 commit/push·draft PR 생성; 코디네이터: 승인 후 ready 전환·merge
- 초기 ready 티켓 없음: 실제 idle smoke 검증 완료
- S/M/L 설계·수용 테스트 생성은 역할 지시서를 따르는 설계 에이전트 단계, 루프 입력은 준비된 티켓

## 승인 입력

- agents.yaml 승인 해시: `logs/defaults-approval.json`
- intent 승인 해시: `logs/intent-approval.json` — 실제 사람 승인 후 메인 설계 에이전트가 다음 형식으로 기록

```json
{"approved_by": "user", "intent_sha256": "approved intent.md SHA-256", "date": "approval timestamp"}
```

- 템플릿 intent는 미승인 상태; 실제 서비스 티켓의 실행 전 별도 intent 승인 필요
- 승인 없이 ready 티켓만 만드는 방식으로 실행 불가
- intent 변경 시 다음 구현·리뷰 디스패치 및 PR ready/merge 차단
- 승인 기록 임의 갱신 금지; 실제 사람 승인 또는 명세 §9의 허용된 마일스톤 조정 근거 필요

## 운영 상태와 복구

- 공유 상태: `<git-common-dir>/pipeline/`, 티켓 ID 기준 전 worktree/Run 소유권
- 상태 저장: 원자적 JSON 교체 + flock, 외부 mutation 전 unknown marker
- timeout 기본값: 작업 경과 1800초, heartbeat 유예 600초; worker-start 30000ms는 시작 준비 제한
- 살아 있거나 확인 불가능한 워커의 부재만으로 중복 실행 금지
- 종료 증거 확인 후 fence/release, 실패 Task 재시도 시 원 Task·worktree 유지
- gate/review 실패의 수정은 같은 worktree·배정 모델의 새 세션, 전체 피드백 전달
- `python3 scripts/pipeline_watch.py --dispatch DISPATCH_ID`: 단일 워커 조사
- `--retry-ticket PATH --failures N`: 소유권·승인·동시성·종료 증거 확인 후 재시도
- unknown/blocked: receipt 조사 우선, 상태 파일을 지우거나 새 Run으로 우회 금지
- 코드·리뷰·gate·CI는 동일 SHA 확인, 미커밋 변경 및 검토 SHA 불일치 차단
- 수용 테스트 수정 금지·시크릿 접근 금지 등 역할 계약: `prompts/roles/`

## 검증 범위

- 실제 Orca: 상·중·하 구현, 반대 진영 리뷰, 상·중 판단, 의도적 실패·다른 워커 재배정·release
- 단위/통합: 정책, subprocess gate 종료 코드, 공유 claim, 응답 유실, crash injection, HEAD 이동, timeout/heartbeat 판정
- 프로젝트: `scripts/gate.sh` → Python 전체 테스트 + `./gradlew test build`
- CI: 기존 필수 `build-and-test`에 Python 정책 검증 포함, main 보호·strict·enforce_admins 확인
- 실제 서비스 변경을 생성해 PR 머지까지 하는 전체 E2E와 강제 프로세스 장애 주입은 미수행; 빈 diff 라우팅 리허설과 구분
- 루프의 DAG는 명시적 depends_on + merged 상태 기준 계산; Orca의 독립 task DAG를 별도로 생성하지 않는 구현
- 제공 문서 밖 탐색·시크릿 금지는 역할 지침, OS 수준 접근 차단을 대체하지 않음
- 모델별 자동 상향 effort의 실제 지원은 provider 거부 receipt로 보수적 중단; 임의 모델 우회 금지
- 실제 한도 소모량은 운영 첫 1~2주 `/usage`, `/status` 관측값만 usage.md 기록

## CLI 확인 근거

- 로컬 Claude Code 2.1.316, Orca 1.4.245 도움말·version-matched skill·실제 receipt
- `codex --help`: `-c key=value`, `--model`; `claude --help`: `--effort`
- Codex effort 키 `model_reasoning_effort`: [공식 설정 문서](https://learn.chatgpt.com/docs/config-file/config-reference)
