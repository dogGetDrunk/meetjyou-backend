# ADR-0003: 서버 컴포넌트 배치 — 앱은 AWS EC2, DB는 OCI 유지 (크로스클라우드 분리)

**Status:** Accepted
**Date:** 2026-09-21
**Deciders:** damiannlee

## Context

- 앱 서버를 OCI Always Free(AMD `VM.Standard.E2.1.Micro`, 2vCPU/956MB) → AWS EC2 `t4g.small`(2vCPU/2GiB, arm64, `ap-northeast-2`)로 이관 중
- 가용 자원: OCI Always Free 인스턴스 2개(춘천 `ap-chuncheon-1`) + EC2 1개
- 배치 대상: nginx, certbot, alloy, spring boot 앱, MySQL
- 핵심 질문: 크로스클라우드 hop의 지연 비용 vs 한 박스 동거 시 메모리 부족 리스크 중 어느 쪽이 큰가

## 실측 근거 (2026-09-21)

**크로스클라우드 비용**
- TCP connect RTT → OCI DB `3306`(60회): EC2 p50 4.25 / p90 4.45 / max 12.09ms, OCI 앱(같은 리전) p50 0.69 / p90 2.82 / max 72.68ms → 왕복당 +3.56ms(p50)
- 요청당 DB 왕복(로컬 MySQL 8.0.41 `general_log`, 읽기 API 14개):
  `왕복 = [Hikari 검증 SELECT 1: 0~1] + [읽기 트랜잭션 제어 5] + [비즈니스 쿼리 N]`
  - 제어 5 = `SET SESSION TRANSACTION READ ONLY` / `SET autocommit=0` / `COMMIT` / `SET autocommit=1` / `READ WRITE` 복원
  - 트랜잭션 없음 2회, 쿼리 1·2·3개 → 7·8·9회, `GET /posts`(데이터 有, ADR-0001 기준 9쿼리) ≈ 15회
- 같은 운영 DB에 붙은 두 앱 컨테이너에 `GET /api/v1/terms/active` 직접 100회 A/B:

  | | p50 | p90 | p99 | max |
  |---|---|---|---|---|
  | EC2 앱 → OCI DB | 31.0ms | 38.1 | 44.6 | 50.8 |
  | OCI 앱 → OCI DB | 7.7ms | 23.2 | 91.5 | 237.4 |

  - p50 차이 23ms = 예측(7 × 3.56ms)과 일치 → 비용은 `RTT × 왕복 수`로 설명됨
  - 꼬리는 EC2 쪽이 더 좁음(OCI micro의 steal·swap 영향)

**메모리**
- 이전 OCI 앱 인스턴스: JVM `VmSwap` 469MB, Serial Full GC 1회 43초(약 8.6일 1회)
- EC2 유휴: MemTotal 1837MB / java RSS 526MB / MemAvailable 800MB / swap 없음
- OCI DB의 mysql: RSS 117MB + `VmSwap` 475MB ≈ 592MB 커밋
- alloy: VmRSS 182MB 중 `RssAnon` 38MB — 나머지는 484MB 바이너리의 파일 매핑 페이지(회수 가능)

## Decision

| 위치 | 컴포넌트 |
|---|---|
| AWS EC2 `t4g.small` | nginx, certbot, spring boot 앱, alloy |
| OCI #2 (DB 인스턴스) | MySQL (유지) |
| OCI #1 (구 앱 인스턴스) | DNS 전환 후 롤백 대기 → 이후 용도 미정(검토 중) |

- 원칙: **요청 경로 안의 동거는 메모리 여유가 확인될 때만, 크로스클라우드 hop은 지연이 상수(타임아웃 규모 아님)로 측정될 때 허용**
- 크로스클라우드 비용 = 요청당 +23~53ms, 결정적·상수 → 프론트 타임아웃(8s)과 자릿수 차이
- 메모리 부족 비용 = 수십 초 정지 또는 OOM 재시작(약 27초) → 타임아웃급
- MySQL 튜닝 시 EC2 동거도 메모리상 가능(Option B)했으나, MySQL은 **OCI 유지로 확정**(사용자 결정 2026-09-21)

## Options Considered

### A. 앱 EC2 + DB OCI 분리 (채택)
- **Pros:** 메모리 리스크 격리, OCI 유휴 자원 활용, 꼬리 지연 오히려 개선
- **Cons:** 요청당 +23~53ms 상수 비용, 장애 도메인이 AWS × OCI × 인터넷 경로로 곱해짐, DB 포트 인터넷 노출(allowlist+TLS로 완화)

### B. EC2 한 대에 전부 동거 (기각 — 사용자 결정 2026-09-21: MySQL은 튜닝 여부와 별개로 OCI 유지)
- 메모리는 튜닝 시 가능하다는 아래 실측과 무관하게 OCI 유지로 결정. 뒷받침 근거: 부하 시 CPU 여유, 이관 범위 고정, OCI 무료 자원 활용
- EC2 실측(MiB): MemAvailable 745, 익명 메모리 735(java 534·dockerd 34·alloy 38·containerd 18 등), 커널 회수 불가분 ~90. java는 `mem_limit 700m`까지 최대 +156 증가 가능
- 로컬 MySQL 8.0.41(arm64, 커넥션 50개) RssAnon: 기본 설정 **368** (`performance_schema` 214·buffer pool 131) / `performance_schema=OFF`·`innodb_buffer_pool_size=64M` **133**
- 여유 = 745 − 156 − MySQL:
  - 기본 설정(운영 실측 592 적용) → **≈ 0** — 동거 불가
  - 튜닝 → 초기 **≈ 456**, 운영에서 관측된 장기 증가분(+220, 원인 미분리)을 전부 더해도 **≈ 236** [추정] — 메모리상 가능
- 남은 반대 근거:
  - CPU 경합 — 병목은 이미 앱 CPU로 확정(부하 시 97%), 같은 부하에서 MySQL도 평균 12.8%·최대 51% 사용. t4g.small 기준 성능 vCPU당 20%, 기본 Unlimited 모드라 24시간 평균 초과분 과금([AWS 문서](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-credits-baseline-concepts.html))
  - `performance_schema` off → `sys` 스키마 기반 DB 진단 상실
  - DB 이관 작업(덤프/복원·다운타임) — 이번 이관 합의 범위("앱 서버만") 밖
- 동거가 나은 점: 지연 −23~53ms, 가용성(서비스는 앱·DB 둘 다 필요 → 분리 시 두 벤더 가용성의 곱), DB 포트 인터넷 노출 제거, 백업을 EBS 스냅샷으로 단순화
- 재검토 조건: OCI 인스턴스 회수 발생 시, 또는 크로스클라우드 지연(+23~53ms)이 실제 사용자 문제로 관측될 때

### C. nginx(+certbot)를 OCI에 두고 EC2 앱으로 프록시 (기각)
- 모든 요청에 클라우드 간 hop 1회 추가 + 앱 포트 외부 노출, 얻는 이득 없음(지리 분산 목적 아님)

### D. alloy 분리 — OCI #1로 이동 or 제거 후 Micrometer 직접 push (기각)
- 실점유 38MB(RAM 2%)만 회수. 이동은 actuator 포트(현재 `127.0.0.1` 바인딩) 외부 노출, 제거는 의존성 변경 + 메트릭 이름 변화로 대시보드 깨질 위험 [추정]
- 재검토 조건: alloy에 로그·호스트 메트릭 등 수집 대상이 추가돼 `RssAnon`이 유의미하게 커질 때

## Consequences / 알려진 리스크

- **OCI Always Free 유휴 회수** — 7일간 CPU p95 < 20% 및 네트워크 < 20%면 회수 대상([Oracle 문서](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)). 계정은 Always Free, DB 인스턴스는 저 CPU(실측 0.82%)로 약 2년 운영됐으나 회수 이력 없음 → 발생 확률은 낮게 판단, 전용 방어 인프라는 두지 않음. 단 DB는 단일 사본이므로 **OCI 밖 백업**은 유지 대상
- **DB 백업 부재** — OCI 밖 백업 없음(2026-09-21 확인). DB가 Always Free 단일 인스턴스의 단일 사본 → 회수·디스크 장애 시 데이터 전손. **최우선 조치 대상**
- **IP 결합** — OCI DB 3306 allowlist가 `3.36.32.223/32` 단일 IP. EIP로 확인(2026-09-21) → stop/start로는 바뀌지 않음, EIP 해제 시에만 갱신 필요
- **TLS 인증서 미검증** — `requireSSL=true`만 적용, 서버 인증서 검증 없음(자체 서명). 암호화는 되나 MITM은 이론상 미방어
- **DB 호스트 메모리** — OCI DB 인스턴스도 swap 619MB 사용 중, 관측(alloy) 부재
- **운영 이원화** — 콘솔·방화벽 규칙이 두 벤더로 분산(3306 규칙 교체 중 구 앱 차단 사고 전례)
- **비용** — AWS public IPv4 $0.005/h ≈ 월 $3.6([AWS VPC 가격](https://aws.amazon.com/vpc/pricing/)), DB 트래픽 전송 비용은 현재 규모에서 무시 가능 [추정]

## Action Items

1. [x] EC2 IP가 EIP인지 확인 — EIP (2026-09-21)
2. [ ] **OCI 밖 주기 백업 도입 (백업 부재 확인, 최우선)** — 계획: `docs/ops/db-backup-plan.md`, 추적: 이슈 #142
3. [ ] (선택) 요청당 제어 명령 5회 축소 검토 — Hikari `auto-commit: false` + `hibernate.connection.provider_disables_autocommit`, Connector/J `readOnlyPropagatesToServer=false` [미검증]
4. [ ] (선택) MySQL 서버 인증서 검증(트러스트스토어) 도입

## 관련

- 이관 PR: #135
- 이관 후 실측: EC2 부하 테스트(이슈 #154), OCI 시절 인프라 이슈 잔존 확인(이슈 #155)
- 선행 실측: ADR-0001(`GET /posts` 9쿼리), 부하 테스트에서 DB 병목 아님 확인(2026-07-21)
