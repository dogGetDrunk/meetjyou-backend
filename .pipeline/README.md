# 만나쥬 파이프라인 프로젝트 상태

- 공통 프로세스: 전역 `agent-pipeline guide`, 배포 소스 [GUIDE.md](../tools/agent-pipeline/GUIDE.md)
- 프로젝트 어댑터: [project.yaml](project.yaml) — `origin/main`, merge commit, `./gradlew test build`
- 태스크 문서: [tasks/](tasks/) — 태스크당 `YYYYMMDD-slug/` 폴더(intent·approval·spec·결정·티켓·logs·result), 코드와 함께 버전 관리
- 시작 신호: `task` 스킬 — Claude Code `/task`, Codex `$task` (원본 [skills/task/SKILL.md](../tools/agent-pipeline/skills/task/SKILL.md))
- 공통 엔진·모델·역할 지시서: 전역 설치본, 프로젝트 복사 없음
- 프로젝트 공통 기록: [decisions.md](decisions.md)(파이프라인 구축 결정), [logs/usage.md](logs/usage.md)(한도 실측), [logs/rehearsal.md](logs/rehearsal.md)(11/1 리허설)

## 실행

```bash
python3 tools/agent-pipeline/install.py --global-instructions
~/.local/bin/agent-pipeline new-task 20261102-slug --title "제목" --size M
~/.local/bin/agent-pipeline tasks
~/.local/bin/agent-pipeline coordinator --run RUN_ID --repo id:REPO_ID --once
~/.local/bin/agent-pipeline gate
```

- source 검증: `scripts/gate.sh` — 공통 Python 테스트 + 만나쥬 test/build
- `scripts/dispatch.sh`: 전역 설치본 우선, 미설치 시 배포 소스 CLI fallback
- CI: 배포 소스 Python 테스트 + 기존 Gradle build/dockerTest
- 승인 판정: 태스크 `approval.json` 해시 일치 + intent·approval이 base ref에 반영(M·L: intent PR 머지, S: `task/<키>` push)
- 기본 모델의 사용자 승인은 전역 `defaults/defaults-approval.json`, 태스크 intent 승인과 분리
- 소유권: `<git-common-dir>/pipeline/`, 저장소의 모든 worktree·Run 합산
- timeout/heartbeat 부재만으로 재실행 금지, 종료 확인 후 재분배
- 실제 서비스 변경 PR→merge E2E는 미수행, 첫 태스크가 검증 겸함
