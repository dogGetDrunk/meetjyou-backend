# 공통 에이전트 개발 파이프라인

- 적용 범위: 모든 프로젝트의 개발 작업, 프로젝트별 명시 규칙 우선
- 공통 원본: 설치본 `defaults/agents.yaml`, 역할 계약 `defaults/prompts/roles/`, 원문 명세 `defaults/agent-pipeline-spec.md`
- 프로젝트 상태: Git 루트 `.pipeline/`, 공유 worktree 소유권 `<git-common-dir>/pipeline/`
- 전제: Python 3.9 이상, Git, Bash, Orca, GitHub와 `gh`; GitHub 외 forge 미지원
- 자동 워커 단계: 설계·수용 테스트 작성 후 ready 티켓부터 구현·gate·교차 리뷰·판단·CI·머지
- 신규 프로젝트 전체 런칭 절차와 기존 프로젝트 개발 절차는 해당 프로젝트 규칙 준수

## 새 프로젝트

1. `git branch --show-current` 및 프로젝트 `AGENTS.md`·`CLAUDE.md` 확인
2. `agent-pipeline guide`, `agent-pipeline policy`로 공통 프로세스 확인
3. 프로젝트의 실제 테스트·빌드 명령과 원격 기본 브랜치 확인 후 초기화

```bash
# Kotlin 예시
agent-pipeline init --base-ref origin/main --gate-json '[["./gradlew","test","build"]]'
# 다른 언어 예시: shell 문자열 대신 argv 배열, 순서대로 실행
agent-pipeline init --base-ref origin/trunk --merge-method squash --gate-json '[["npm","test","--","--run"],["npm","run","build"]]'
```

4. 사람의 intent 승인 기록 후 설계·티켓·수용 테스트 준비
5. Orca version-matched orchestration 지침에 따라 Run 생성 후 실행

```bash
agent-pipeline dispatch .pipeline/tickets/T-001.md
agent-pipeline coordinator --run RUN_ID --repo id:REPO_ID --once
agent-pipeline coordinator --run RUN_ID --repo id:REPO_ID --interval 30
agent-pipeline gate
agent-pipeline watch --dispatch DISPATCH_ID
```

- 초기화는 기존 파일 덮어쓰기·intent 자동 승인 없음
- 프로젝트에 공통 엔진·모델 설정·역할 프롬프트 복사 금지, 전역 설치본 사용
- 설계 에이전트: `main-designer.md` → `sub-designer.md` → `test-writer.md` 계약 적용
- intent 승인: `.pipeline/logs/intent-approval.json`에 실제 승인 근거와 `intent_sha256` 기록
- 원문 기본 모델의 사람 승인은 공통 `defaults/defaults-approval.json` 해시 검증, 변경 시 재승인 필요
- 프로젝트의 gate·기본 브랜치·merge_method는 `project.yaml`, 승인된 작업 중 임의 완화 금지
- gate 명령은 coordinator checkout 설정 기준으로 worker worktree에서 실행
- 교차 리뷰·판단·CI는 gate와 동일 HEAD 기준, 배포·공유 DB migration·force push·시크릿 접근 금지
- timeout/heartbeat 부재만으로 재실행 금지, 기존 워커 종료 확인 후 재분배
- unknown receipt는 조사 우선, 상태 삭제·새 Run 우회 금지
- 정책·상태 분리: 서로 다른 저장소의 `T-001`과 intent 승인·로그·claim은 독립
- 동시 구현 2개 한도: 각 저장소의 모든 worktree/Run을 합산, 서로 다른 저장소 간 전역 quota는 별도 기능 없음
- 설치는 지침 기반 기본 적용, 실행 중인 에이전트에 강제 hook을 추가하는 방식 아님

## 설치·검증

```bash
python3 tools/agent-pipeline/install.py --global-instructions
~/.local/bin/agent-pipeline guide
agent-pipeline self-test
```

- 기본 설치: `~/.local/share/agent-pipeline/releases/<digest>` + 공용 venv + `~/.local/bin/agent-pipeline`
- 런처: 고정 release 사용, 원본 저장소 이동·브랜치 전환과 무관
- 업데이트: 설치 명령 재실행, 기존 런처는 준비 완료 후 원자적으로 교체
- Codex/Claude 전역 지침: 기존 본문 보존 후 관리 블록 추가, 재설치 시 같은 블록 갱신
- 다른 머신/CI: 설치하거나 소스 CLI `python3 tools/agent-pipeline/scripts/pipeline_cli.py …` 사용
- 공통 소스의 현재 보관 저장소는 Meetjyou, 실행 시 해당 저장소·Gradle에 의존 없음
