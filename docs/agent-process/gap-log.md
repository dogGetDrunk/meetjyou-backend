# 갭 로그

> 사용자 추궁·검증자·자체 발견으로 드러난 빈틈 기록. 항목마다 **놓친 층 → 추가한 장치**를 남김.
> 층 정의와 절차: [README.md](README.md). 새 항목은 맨 아래에 `G<번호>`로 추가.

## 항목 형식

```
### G<n>. <한 줄 요약>
- 날짜 / 출처: YYYY-MM-DD, PR·커밋·세션
- 발견 경로: 사용자 추궁 / 검증자 / 자체 발견
- 놓친 층: L<n>
- 왜 놓쳤나:
- 추가한 장치: `<이 브랜치에서 바꾼 파일 경로>` 또는 이슈 #<n> (완료 게이트가 이 칸을 검사)
```

- G1~G9는 도입 이전 사건이라 장치가 도입 브랜치(`worktree-agent-verification-process`, `worktree-static-analysis`)에 있음

## 기록

### G1. 요구사항 변경의 영향 분석 누락 (enum↔시드 데이터 불일치)
- 날짜 / 출처: 2026-09-15, PR #130 (`917176e` → `e43cff4`)
- 발견 경로: 사용자 추궁("수정할 부분 저게 진짜 끝이야?")
- 놓친 층: L1
- 왜 놓쳤나: 변경 대상 필드만 확인하고 쓰기 경로가 읽기 불변식을 보장하는지는 보지 않음. 진실 원천이 두 개(enum·Flyway 시드)라는 점 미확인
- 추가한 장치: `.claude/templates/ledger.md` 영향 분석 체크리스트("쓰기↔읽기 불변식", "진실 원천 2개 이상"), 이슈 #131(CI 정합성 테스트)

### G2. 테스트 통과를 요구사항 충족의 근거로 보고
- 날짜 / 출처: 2026-09-15, PR #130 완료 보고 ("330개 전체 통과")
- 발견 경로: 사용자 추궁 (G1과 동일 시점)
- 놓친 층: L3
- 왜 놓쳤나: 완료 조건이 "테스트 통과"뿐이었고 요구사항별 증거를 요구하는 장치가 없음
- 추가한 장치: `.claude/hooks/completion-gate.py`(원장 ⬜/🟡 차단), `.claude/agents/requirement-verifier.md`

### G3. 원인 진단을 확인 전에 단정
- 날짜 / 출처: 2026-09-15, git push 실패 진단 ("의도된 보안 제약" → 실제 원인은 origin URL의 오타난 사용자명)
- 발견 경로: 사용자 추궁("이때까지 ssh로 push한거야 아님 갑자기 이렇게 된거야?")
- 놓친 층: L5
- 왜 놓쳤나: 가설과 확인된 사실을 구분해 표기하는 규칙이 없음
- 추가한 장치: CLAUDE.md "Verification Workflow"의 `[추정]` 표기 규칙 (문장 규칙 — 진단 발언의 확인 여부는 결정적으로 판정할 방법이 없음)

### G4. 작업 비용을 확인 전에 추정
- 날짜 / 출처: 2026-09-15, "정합성 테스트는 파일 하나면 끝" → 테스트 프로필이 Flyway off라 인프라 필요
- 발견 경로: 자체 발견
- 놓친 층: L5
- 왜 놓쳤나: G3과 동일
- 추가한 장치: G3과 동일 규칙, 원장 체크리스트 "테스트 환경 ≠ 운영 환경"

### G5. 진단 중 자격증명이 평문 노출
- 날짜 / 출처: 2026-09-15, `git credential fill` 실행
- 발견 경로: 자체 발견 (사후)
- 놓친 층: L2
- 왜 놓쳤나: 자격증명을 출력하는 명령을 막는 장치가 없음
- 추가한 장치: `.claude/settings.json` deny `Bash(git credential*)`(credential-osxkeychain 포함), `Bash(gh auth token *)`, `Bash(security find-*-password *)`

### G6. 요청 없이 커밋·PR 생성
- 날짜 / 출처: 2026-07-21(커밋), 2026-08-05(PR)
- 발견 경로: 사용자 추궁("왜 또 네 멋대로 커밋해?")
- 놓친 층: L2
- 왜 놓쳤나: CLAUDE.md 규칙은 있었지만 로컬 permission allow 목록이 `git commit *`, `gh pr *`를 미리 허용
- 추가한 장치: `.claude/settings.json` ask `gh pr create *`, `gh pr merge *` (프로젝트 ask가 로컬 allow보다 우선). 커밋·푸시는 background job 한정 허용이라는 사용자 결정(2026-09-17)에 따라 ask 대상에서 제외

### G7. 아무것도 검증하지 않는 회귀 테스트
- 날짜 / 출처: PR #125, #126 (`json.contains("itinStartAfterNow")` — 실제 키는 `isItinStartAfterNow`)
- 발견 경로: 사용자 추궁("재발하지 않을지 다시 확인해보라")
- 놓친 층: L1, L4
- 왜 놓쳤나: 테스트가 수정 전에 실패하는지(red) 확인하는 절차가 없음
- 추가한 장치: 원장 체크리스트 "버그 수정이면 red 증거 확보", 검증자의 "요구사항이 깨져도 통과하는 assertion" 점검

### G8. H2 테스트로는 못 잡는 MySQL 전용 오류
- 날짜 / 출처: PR #125 (`@JdbcTypeCode` 누락, H2 306개 통과 / MySQL 500)
- 발견 경로: 자체 발견 (dev 수동 검증)
- 놓친 층: L1
- 왜 놓쳤나: 테스트 환경과 운영 환경의 차이를 점검하는 항목이 없음
- 추가한 장치: 원장 체크리스트 "테스트 환경 ≠ 운영 환경", 이슈 #131(MySQL + Flyway 테스트 슬라이스)

### G9. 출처 없는 사실을 문서에 기재
- 날짜 / 출처: 2026-09-14, 자기소개서 초안 (차량 SW 배포 관련 서술)
- 발견 경로: 사용자 추궁("이 부분 사실이야?")
- 놓친 층: L5
- 왜 놓쳤나: 외부 사실 주장에 출처를 요구하는 규칙이 없음
- 추가한 장치: CLAUDE.md "Verification Workflow"의 출처 규칙 (문장 규칙 — 코드 외 산출물이라 결정적 검사 대상이 아님)

### G10. Kotest 테스트 이름이 "!"로 시작해 조용히 skip됨
- 날짜 / 출처: 2026-09-17, `worktree-static-analysis` 브랜치 ConventionTest 작성 중 ("!! 연산자를 쓰지 않는다")
- 발견 경로: 자체 발견 (완료 주장 전, 테스트 결과 XML의 skipped=1 확인)
- 놓친 층: L3
- 왜 놓쳤나: Gradle 결과가 BUILD SUCCESSFUL이라 skip된 테스트가 초록불로 보임. Kotest는 "!" 접두 이름을 비활성화로 해석
- 추가한 장치: `worktree-static-analysis` 브랜치 `build.gradle.kts` — skip된 테스트가 1개라도 있으면 빌드 실패 (red 확인 완료)

### G11. 검증자가 찾은 갭이 회고 프로토콜에 들어가지 않음
- 날짜 / 출처: 2026-09-18, 이 브랜치의 requirement-verifier 1·2차 검증
- 발견 경로: 검증자 (2차 보고 F7)
- 놓친 층: L6
- 왜 놓쳤나: 감지 장치가 사용자 발화(UserPromptSubmit)만 보고 있었음. 검증자·자체 발견 경로는 문장 규칙뿐이라, 실제로 1차 지적 7건을 판정·기록 없이 고치고 넘어갔음 (도입 브랜치 자신에 미적용)
- 추가한 장치: `.claude/hooks/record-verifier-gaps.py` — 서브에이전트 보고(SubagentHandback/SubagentStop)에 미충족·부분·근거 부족 판정이 있으면 추궁으로 기록, `.claude/settings.json`에 등록

### G12. 완료 게이트가 흔한 완료 표현·삭제·빈 원장·층 분류를 놓침
- 날짜 / 출처: 2026-09-18, requirement-verifier 2차 검증 (F1~F6)
- 발견 경로: 검증자
- 놓친 층: L3
- 왜 놓쳤나: 게이트 전체가 CLAIM 정규식 하나에 의존했는데 "구현했습니다" 류가 빠져 있었고, 변경 목록을 존재하는 파일로만 계산해 삭제를 못 봄. 빈 원장·층 표기 없는 판정·`#숫자`만 있는 장치 칸도 통과했으며, 테스트 마커만 브랜치 비종속이었음
- 추가한 장치: `.claude/hooks/completion-gate.py`(표현 보강, 삭제 반영, 빈 원장 차단, 층 표기 요구, 이슈 표기 강화), `.claude/hooks/agent_work.py`(브랜치별 마커, 삭제 시 인덱스 기준 시각), `.claude/hooks/test_hooks.py`(시나리오 40개)

### G13. worktree 세션에서 hook이 스크립트를 찾지 못해 게이트가 무력화됨
- 날짜 / 출처: 2026-09-18, 브랜치 커밋 직후 실제 세션의 PostToolUse 오류
- 발견 경로: 자체 발견 (완료 보고 이후)
- 놓친 층: L3
- 왜 놓쳤나: hook 명령을 `$CLAUDE_PROJECT_DIR` 기준으로 작성했는데, worktree 세션에서는 설정이 worktree에서 읽히면서도 이 변수는 원본 체크아웃을 가리킴. 원장에 [추정]으로 적어두고 실행으로 확인하지 않았고, 테스트가 스크립트만 직접 호출해 명령 문자열은 한 번도 실행하지 않았음
- 추가한 장치: `.claude/settings.json`(현재 저장소의 git 루트로 스크립트 경로 해석, 없으면 조용히 종료), `.claude/hooks/test_hooks.py`(설정 파일의 실제 명령 문자열을 낯선 CLAUDE_PROJECT_DIR로 실행하는 시나리오 3개)

### G14. 원장 수용 기준이 로컬 환경과 CI 환경의 차이를 반영하지 못해 멀티아치 빌드 실패 위험을 놓침
- 날짜 / 출처: 2026-09-20, AWS EC2(t4g.small, arm64) 이관 작업 중 requirement-verifier 검증 (R3/I3 "부분" 판정)
- 발견 경로: 검증자
- 놓친 층: L1
- 왜 놓쳤나: 원장 I1의 수용 기준을 "워크플로우 파일에 `linux/arm64` 플랫폼 명시"로만 좁게 정의해, `docker buildx build --platform ...`가 실제로 CI에서 성공하는지가 아니라 플래그 존재 여부만 확인 대상이 됨. 로컬 검증(I3)도 로컬 Docker Desktop 빌더가 QEMU를 이미 내장하고 있어 GitHub Actions `ubuntu-latest`(QEMU 미등록)와의 환경 차이를 드러내지 못함
- 추가한 장치: `.github/workflows/deploy.yml`에 `docker/setup-qemu-action@v3`·`docker/setup-buildx-action@v3` 스텝을 buildx 빌드 스텝 앞에 추가 — 크로스 아키텍처 빌드의 필수 전제조건을 CI 파이프라인 자체에 고정

### G15. 호스트 바인드 마운트 파일 시크릿이 원장 범위에서 완전히 누락됨
- 날짜 / 출처: 2026-09-20, AWS EC2 이관 workflow_dispatch 재시도(run 35496288525) 실패 진단 중
- 발견 경로: 자체 발견 (`docker logs spring_boot`로 배포 실패 원인 조사 중)
- 놓친 층: L1
- 왜 놓쳤나: 영향 분석 체크리스트(§2)가 "진실 원천"을 GitHub Secrets(`SPRING_DATASOURCE_URL` 등 env var) 기준으로만 점검했고, `docker-compose.yml`이 `./data/spring_boot/firebase-adminsdk.json`·`./data/spring_boot/oci/{config,private-key.pem}`를 호스트 파일 바인드 마운트로 참조한다는 사실을 확인하지 않음. 이 파일들은 git에도 GitHub Secrets에도 없고 OCI 서버에만 수동으로 존재해, "시크릿 = GitHub Secrets"라는 암묵적 가정이 깨짐. 새 인스턴스에 파일이 없자 Docker가 빈 디렉터리를 자동 생성했고, 이후 재전송한 파일이 이미 시작된 컨테이너의 바인드 마운트(디렉터리→파일 전환)에 반영되지 않아 두 번째 함정(컨테이너 재생성 필요)까지 발생
- 추가한 장치: `.github/workflows/deploy.yml`에 "호스트 파일 시크릿 존재 확인" 스텝 추가 — `.env` 업로드 직후·배포 실행 직전에 `firebase-adminsdk.json`·`oci/config`·`oci/private-key.pem` 3개 파일의 존재를 SSH로 확인하고, 하나라도 없으면 명확한 에러 메시지와 함께 즉시 실패(exit 1). 앞으로 어떤 새 호스트로 옮기든 이 사일런트 실패(빈 디렉터리 자동 생성 → 알아보기 어려운 빈 생성 예외)가 CI 단계에서 바로 드러나도록 고정

### G16. nginx 설정도 호스트 전용 파일이었고, deploy.yml 헬스체크가 nginx 계층을 전혀 검증하지 않음
- 날짜 / 출처: 2026-09-21, DNS 컷오버 준비 중 자체 발견
- 발견 경로: 자체 발견 (컷오버 전 점검 중 `docker logs nginx`로 확인)
- 놓친 층: L1
- 왜 놓쳤나: G15에서 "호스트 바인드 마운트 파일"을 firebase/oci 시크릿으로만 한정해서 봤고, `docker-compose.yml`이 `./data/nginx`(리버스 프록시 설정)·`./data/certbot`(TLS 인증서)도 동일하게 호스트 전용으로 마운트한다는 걸 놓침. 게다가 `deploy.yml`의 헬스체크가 `curl 127.0.0.1:8081/actuator/health`로 **spring_boot 컨테이너에 직접** 접속해 확인하기 때문에, nginx가 설정 없이 기본 페이지만 서빙하거나 죽어 있어도(exited) CI 헬스체크는 계속 성공으로 판정 — 실제 공개 도메인(HTTPS)은 완전히 깨진 채로 "배포 성공"이 보고될 수 있었음
- 추가한 장치: `.github/workflows/deploy.yml`의 "호스트 파일 시크릿 존재 확인" 스텝에 `~/meetjyou/data/nginx/app.conf` 존재 확인 추가(파일:37번째 줄 for 루프). Let's Encrypt 인증서(`data/certbot/conf/live/.../fullchain.pem`)는 DNS가 그 호스트를 가리켜야만 발급 가능한 구조라 이 사전 확인에는 포함하지 않음(정상적인 최초 컷오버 흐름에서는 원래 없는 게 맞는 상태이므로) — 대신 원장 §4에 "deploy.yml 헬스체크는 nginx/TLS 계층을 검증하지 않는다"는 한계를 기록해 재발 시 빠르게 원인을 좁힐 수 있게 함

### G17. 완료 게이트가 "브랜치 머지 후 완료 보고"를 처리하지 못해 오탐 차단
- 날짜 / 출처: 2026-09-21, AWS EC2 이관 PR #135 머지 직후 완료 보고 (사용자가 로컬 main을 머지 커밋까지 pull한 뒤)
- 발견 경로: 자체 발견 (완료 게이트가 실제로 오탐 차단하는 것을 직접 겪음)
- 놓친 층: L3
- 왜 놓쳤나: `resolve_base()`가 `git merge-base HEAD origin/main`으로 비교 기준점을 구하는데, 이 브랜치가 머지되고 로컬 `main`이 그 머지 커밋까지 갱신되면 `merge-base(HEAD, origin/main)`이 `HEAD` 자기 자신으로 퇴화함. 그 결과 "base 시점 gap-log.md"와 "현재 gap-log.md"가 동일해져 `validate_gap_log`가 이미 커밋·머지까지 끝난 갭 항목을 "새 항목 0건"으로 오판. 게이트가 "머지 전 완료 보고"만 상정하고 설계돼 이 경로를 검증하지 않았음
- 추가한 장치: `.claude/hooks/agent_work.py`의 `resolve_base()`에 퇴화 판정(`base == HEAD`) 시 폴백 추가 — 1순위 `git merge-base --fork-point origin/main HEAD`(리플로그 기반, origin/main이 옮겨가도 분기점 보존), 2순위 origin/main 팁이 이 브랜치를 병합한 머지 커밋일 때 그 첫 부모. `.claude/hooks/test_hooks.py`에 `scenarios_post_merge_base` 시나리오 추가(머지 커밋 생성 후 `origin/main`을 그 커밋으로 갱신하고 게이트가 여전히 조용히 통과하는지 검증) — 수정 전 코드로 되돌려 실제로 실패하는 것 확인 후(red) 수정 적용해 45/45 통과(green)

### G18. 멀티아치 전환이 배포 빌드 시간을 10배로 늘렸는데 원장 영향 분석이 빌드 시간을 보지 않음
- 날짜 / 출처: 2026-09-22, 사용자 "github actions 플로우가 너무 오래 걸리는데, 원인 파악해봐"
- 발견 경로: 사용자
- 놓친 층: L1
- 왜 놓쳤나: PR #135가 buildx 플랫폼에 `linux/arm64`를 추가할 때 수용 기준이 "빌드가 성공하는가"(G14)에만 맞춰져 있었고, Dockerfile builder stage가 대상 플랫폼마다 실행된다는 점(= Gradle 빌드가 QEMU 에뮬레이션 위에서 한 번 더 돎)을 영향 분석에서 다루지 않음. 빌드 step 152s(run 35345531993) → 1522s(run 35601775388, arm64 `dnf` 231.8s + `gradlew bootJar` 1269.5s)로 회귀했지만 실패가 아니라 느려진 것이라 어떤 층도 신호를 내지 않음
- 추가한 장치: `Dockerfile` builder stage를 `FROM --platform=$BUILDPLATFORM`으로 고정(JAR은 네이티브 1회 빌드 후 각 런타임 이미지에 COPY). `.github/workflows/deploy.yml` 빌드 step에 `timeout-minutes: 12` — 빌드 시간이 다시 회귀하면 조용히 느려지는 대신 실패하고 기존 Discord 실패 알림이 발송됨

### G19. 배포 스크립트에서 `compose down`을 없애면서 pull 실패가 거짓 성공이 되는 후퇴를 놓침
- 날짜 / 출처: 2026-09-22, 배포 다운타임 축소(f3ba43c) 후 PR 전 requirement-verifier 전체 브랜치 검증 (R3 "부분" 판정)
- 발견 경로: 검증자
- 놓친 층: L1
- 왜 놓쳤나: 기존 스크립트는 `down`이 먼저 앱을 내렸기 때문에, pull/up이 실패하면 헬스체크가 반드시 실패해 롤백·exit 1·Discord 알림으로 이어졌음. 이 "실패를 드러내는 부수효과"가 암묵적이었고, 영향 분석은 정상 경로·IP 변경·롤백만 시뮬레이션함. `set -e`가 없는 스크립트에서 `down`을 없애자 pull 실패 시 옛 컨테이너가 헬스체크를 통과해 exit 0, `CURRENT_TAG`에 배포되지 않은 SHA가 기록됨 (로컬 모의 스택에서 재현: `배포 성공: 9.99-missing`, 실행 이미지는 1.36)
- 추가한 장치: `.github/workflows/deploy.yml` — pull 실패 시 즉시 exit 1, 헬스 루프가 실행 중 컨테이너의 이미지 태그가 새 SHA일 때만 UP을 인정(수정 후 재현: pull 실패·up 실패 모두 exit 1, 무중단). `.claude/templates/ledger.md` 영향 분석 체크리스트에 "실패 경로 불변식" 항목 추가

### G20. hook 테스트 가상 시계 헬퍼가 hook이 무시한 실행에서도 옛 마커를 갱신
- 날짜 / 출처: 2026-09-22, CI 시간 단축(sleep → 가상 시계) 후 requirement-verifier 검증 (R1 "부분" 판정)
- 발견 경로: 검증자
- 놓친 층: L1
- 왜 놓쳤나: `gradle()` 헬퍼의 재스탬프 조건을 `getmtime > VIRTUAL_CLOCK_START`로 잡아, 이미 가상 시각인 기존 마커에도 참이 됨 → 실패·부분 실행 뒤에도 마커가 최신으로 보임. 원장 R1의 수용 기준이 "45/45 유지 + 게이트 뮤턴트 검출"뿐이라, 테스트 **헬퍼 자체**가 "hook이 무시한 실행"을 거짓으로 green 만드는 경로를 점검하지 않음. 기존 45개 시나리오 중 그 경로를 타는 것이 없어 통과
- 추가한 장치: `.claude/hooks/test_hooks.py` — 조건을 `> virtual_now`(hook이 방금 실제 시각으로 쓴 마커만)로 수정 + 시나리오 "failed run does not refresh a stale marker" 추가 (수정 전 FAIL 확인 후 green)

### G21. "다른 브랜치 마커 무효" 시나리오가 브랜치 격리를 검증하지 않았음 (공허한 테스트)
- 날짜 / 출처: 2026-09-22, 같은 검증에서 브랜치 무관 마커 뮤턴트로 발견. origin/main 버전 테스트도 같은 뮤턴트에서 45/45 (기존 결함)
- 발견 경로: 검증자
- 놓친 층: L1
- 왜 놓쳤나: 시나리오가 차단되는 진짜 이유가 마커의 브랜치가 아니라 `git checkout`이 feat/x 파일을 실제 현재 시각으로 다시 쓰는 것이었음. 시나리오 도입 시 해당 동작을 망가뜨린 뮤턴트로 red를 확인하지 않음
- 추가한 장치: `.claude/hooks/test_hooks.py` `scenarios_branch_isolation` 재작성 — feat/x 마커를 소스 수정으로 stale하게 만들고, checkout이 바꾼 mtime을 되돌려 마커의 브랜치만 판정을 가르게 함. 브랜치 무관 마커 뮤턴트(`record-full-test.py`가 항상 feat-x 마커에 기록)에서 FAIL 확인
