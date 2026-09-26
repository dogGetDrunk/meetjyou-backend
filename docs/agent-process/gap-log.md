# 갭 로그

> 사용자 추궁·검증자·자체 발견으로 드러난 빈틈 기록. 항목마다 **놓친 층 → 추가한 장치**를 남김.
> 층 정의와 절차: vgate `docs/README.md`, 이 레포 적용분: [README.md](README.md).
> vgate(검증 프로세스) 자체의 갭 G2·G3·G4·G5·G6·G7·G9·G11·G12·G13·G17·G20·G21·G22·G23·G24·G25·G28·G29·G31·G32는 vgate 개발 레포 `~/orca/projects/vgate/docs/gap-log.md`로 이관 (번호 유지).
> G1·G19·G26·G27은 사건은 이 레포, 장치(원장 템플릿 항목)는 vgate — 양쪽에 존재. 새 항목은 G33부터.
> vgate 결함으로 판정된 갭은 장치 칸에 `vgate@<커밋 sha>` 인용 가능.

## 항목 형식

```
### G<n>. <한 줄 요약>
- 날짜 / 출처: YYYY-MM-DD, PR·커밋·세션
- 발견 경로: 사용자 추궁 / 검증자 / 자체 발견
- 놓친 층: L<n>
- 왜 놓쳤나:
- 추가한 장치: `<이 브랜치에서 바꾼 파일 경로>` 또는 이슈 #<n> 또는 vgate@<sha> (완료 게이트가 이 칸을 검사)
```

- G1~G9는 도입 이전 사건이라 장치가 도입 브랜치(`worktree-agent-verification-process`, `worktree-static-analysis`)에 있음

## 기록

### G1. 요구사항 변경의 영향 분석 누락 (enum↔시드 데이터 불일치)
- 날짜 / 출처: 2026-09-15, PR #130 (`917176e` → `e43cff4`)
- 발견 경로: 사용자 추궁("수정할 부분 저게 진짜 끝이야?")
- 놓친 층: L1
- 왜 놓쳤나: 변경 대상 필드만 확인하고 쓰기 경로가 읽기 불변식을 보장하는지는 보지 않음. 진실 원천이 두 개(enum·Flyway 시드)라는 점 미확인
- 추가한 장치: `.claude/templates/ledger.md` 영향 분석 체크리스트("쓰기↔읽기 불변식", "진실 원천 2개 이상"), 이슈 #131(CI 정합성 테스트)

### G8. H2 테스트로는 못 잡는 MySQL 전용 오류
- 날짜 / 출처: PR #125 (`@JdbcTypeCode` 누락, H2 306개 통과 / MySQL 500)
- 발견 경로: 자체 발견 (dev 수동 검증)
- 놓친 층: L1
- 왜 놓쳤나: 테스트 환경과 운영 환경의 차이를 점검하는 항목이 없음
- 추가한 장치: 원장 체크리스트 "테스트 환경 ≠ 운영 환경", 이슈 #131(MySQL + Flyway 테스트 슬라이스)

### G10. Kotest 테스트 이름이 "!"로 시작해 조용히 skip됨
- 날짜 / 출처: 2026-09-17, `worktree-static-analysis` 브랜치 ConventionTest 작성 중 ("!! 연산자를 쓰지 않는다")
- 발견 경로: 자체 발견 (완료 주장 전, 테스트 결과 XML의 skipped=1 확인)
- 놓친 층: L3
- 왜 놓쳤나: Gradle 결과가 BUILD SUCCESSFUL이라 skip된 테스트가 초록불로 보임. Kotest는 "!" 접두 이름을 비활성화로 해석
- 추가한 장치: `worktree-static-analysis` 브랜치 `build.gradle.kts` — skip된 테스트가 1개라도 있으면 빌드 실패 (red 확인 완료)

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

### G26. 원장 영향 분석이 다른 모듈의 연관 경유 쓰기 경로를 놓침
- 날짜 / 출처: 2026-09-24, 파티 이름 변경 PATCH(`worktree-party-rename`) PR 전 requirement-verifier 검증 (체크리스트 "쓰는 곳" 근거 불완전 지적)
- 발견 경로: 검증자
- 놓친 층: L1
- 왜 놓쳤나: "쓰는 곳"을 `party` 패키지 안에서만 찾음. `PostService.kt:300` `post.party.plan = ...`, `PlanService.kt:195` `party.plan = null`처럼 다른 모듈이 연관을 거쳐 잠금 없이 쓰는 경로는 grep 범위에 없었음. `Party`에 `@DynamicUpdate`가 없어 이런 경로의 flush는 모든 컬럼을 다시 씀 → 동시 이름 변경을 옛 값으로 덮어쓸 수 있음(기존 PUT에도 있던 선행 문제, 후속 과제)
- 추가한 장치: `.claude/templates/ledger.md` "쓰는 곳" 항목 — 필드 대입 grep에 그치지 않고 엔티티 로더(`findByUuid`·`require<Entity>`·연관 getter, 타 모듈 포함) 호출 지점마다 필드 대입과 변경 메서드 호출까지 추적, 실행한 grep 명령·경로별 잠금 여부 기재를 요구

### G27. G26 장치(필드 대입 grep)가 같은 누락을 재현함 — 원장에 "PartyService 내부는 잠금 사용" 오기재
- 날짜 / 출처: 2026-09-24, G26 수정 후 requirement-verifier 재검증 ("쓰는 곳" 부분 판정)
- 발견 경로: 검증자
- 놓친 층: L1
- 왜 놓쳤나: G26 장치로 넣은 grep(`party\.[a-zA-Z]+ *=`)은 필드 대입만 잡음. `PartyService`의 `completeParty`(`requireParty` → `party.complete()`), `confirmPartyImage`·`clearPartyImageState`(`requireParty(...).imageState =`)처럼 변경 메서드 호출이나 잠금 없는 로더 헬퍼를 거치는 쓰기는 구조적으로 빠짐. 그 grep 결과만 보고 원장에 "PartyService 내부 경로는 잠금 사용"이라 적음
- 추가한 장치: `.claude/templates/ledger.md` "쓰는 곳" 항목 — 필드 대입 grep 예시를 빼고, 엔티티 로더 호출 지점부터 필드 대입·변경 메서드 호출까지 추적하도록 기준을 바꿈. 한계: 문서 규칙이라 기계 강제 없음

### G30. deploy.yml push 트리거에 경로 필터가 없어 hook·문서 전용 머지에도 프로덕션 재배포
- 날짜 / 출처: 2026-09-22, hook 전용 PR 머지 후 배포 run 35727407928 (이전 세션 보고) → 이슈 #137, `330fa50`
- 발견 경로: 자체 발견
- 놓친 층: L1
- 왜 놓쳤나: 배포 워크플로 변경 시 영향 분석이 "무엇이 배포를 트리거하는가"를 보지 않음. 수정(#137)은 머지됐지만 갭 기록은 `main`의 로컬 triage에만 남고 gap-log로 옮겨지지 않음 — #146 전까지는 main에서 머지 직전 base가 복원돼 다른 PR의 항목으로 게이트가 우연히 충족되어 드러나지 않음
- 추가한 장치: 이슈 #137 (`.github/workflows/deploy.yml` `on.push.paths` 필터)


### G33. vgate 이관 원장이 plugin hook의 실제 로드를 증거 없이 가정 — 게이트가 신호 없이 사라질 수 있었음
- 날짜 / 출처: 2026-09-25, `worktree-vgate-migration` (vgate plugin 이관)
- 발견 경로: 검증자 (I1 부분 판정 — 검증 세션의 Bash 10여 회가 `debug: true`인데도 hook-debug에 기록되지 않음)
- 놓친 층: L1
- 왜 놓쳤나: plugin 스크립트를 직접 실행한 결과와 `claude plugin details`의 인벤토리(hook 6종 인식)를 "동작"의 증거로 취급. 내장 hook은 스크립트 부재 시 조용히 종료하도록 설계돼 있어 제거 즉시 무신호로 꺼지는데, 대체 장치가 harness에서 발동하는지는 실측하지 않음
- 추가한 장치: `.claude/vgate-guard.py` + `.claude/settings.json` Stop 등록 — opt-in 레포에서 이 세션에 vgate heartbeat가 없으면 경고 (red/green 확인: heartbeat 없음 → 경고, 있음 → 무음, 다른 세션 heartbeat 불인정, 미opt-in 레포 무음). vgate 쪽: heartbeat 기록(`scripts/vgate_common.py` `mark_alive`)과 원장 템플릿 항목 "장치 교체·이관은 실제 런타임 발동 증거"
