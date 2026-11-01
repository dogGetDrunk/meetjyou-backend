# 만나쥬 파이프라인 프로젝트 상태

- 공통 프로세스: 전역 `agent-pipeline guide`, 배포 소스 [GUIDE.md](../tools/agent-pipeline/GUIDE.md)
- 프로젝트 어댑터: [project.yaml](project.yaml) — `origin/main`, merge commit, `./gradlew test build`
- 프로젝트 소유: intent·spec·티켓·수용 테스트·실행 로그·intent 승인
- 공통 엔진·모델·역할 지시서: 전역 설치본, 프로젝트 복사 없음
- 이전 리허설·결정 근거: [logs/rehearsal.md](logs/rehearsal.md), [decisions.md](decisions.md)

## 실행

```bash
python3 tools/agent-pipeline/install.py --global-instructions
~/.local/bin/agent-pipeline dispatch .pipeline/tickets/T-001.md
~/.local/bin/agent-pipeline coordinator --run RUN_ID --repo id:REPO_ID --once
~/.local/bin/agent-pipeline gate
```

- source 검증: `scripts/gate.sh` — 공통 Python 테스트 + 만나쥬 test/build
- `scripts/dispatch.sh`: 전역 설치본 우선, 미설치 시 배포 소스 CLI fallback
- CI: 배포 소스 Python 테스트 + 기존 Gradle build/dockerTest
- 템플릿 intent는 미승인 상태, 실제 작업 전 `.pipeline/logs/intent-approval.json` 필요
- 기본 모델의 기존 사용자 승인은 전역 `defaults/defaults-approval.json`으로 이동
- 설정 변경 승인 해시와 실제 intent 승인 해시 분리, 타 프로젝트로 intent 승인 복사 금지
- 소유권: `<git-common-dir>/pipeline/`, 저장소의 모든 worktree·Run 합산
- timeout/heartbeat 부재만으로 재실행 금지, 종료 확인 후 재분배
- 이전 리허설의 빈 diff 라우팅과 실제 서비스 변경 PR→merge E2E는 별개, 후자는 미수행
