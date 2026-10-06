# DB 백업 도입 계획 (1단계 완료)

- 상태: **1단계 완료** (2026-10-05) — 이슈 #142. 설치·복원 절차: `ops/db-backup/README.md`
- 배경: `docs/adr/0003-multi-cloud-component-placement.md` Action Item 2
- 작성: 2026-09-21

## 배경

- 운영 MySQL(8.0.41 도커 컨테이너)은 OCI Always Free 인스턴스(`158.180.71.173`) **1대에 단일 사본**
- OCI 밖 백업 **없음**(2026-09-21 확인) → 인스턴스 회수(Always Free 유휴 정책)·디스크 장애·오조작 시 데이터 전손
- MySQL은 OCI 유지로 결정(ADR-0003) → 백업은 **다른 벤더(AWS)** 에 두는 것이 목표

## 설계 (1단계 — 최소 구성)

| 항목 | 결정 | 이유 |
|---|---|---|
| 실행 위치 | 앱 서버 EC2(`3.36.32.223`) | 이미 OCI DB 3306 allowlist 등록됨 → 방화벽 변경 불필요, 사본이 곧바로 OCI 밖에 생김 |
| 방식 | `mysqldump --single-transaction` 논리 덤프 + gzip | InnoDB 일관 스냅샷, 잠금 없음. 데이터 소규모(2026-07 기준 스키마 1.2MB)라 물리 백업 불필요 |
| 주기 | 1일 1회, 트래픽 저점(예: 04:00 KST) | 현재 규모 기준 RPO 24h 수용 [사용자 확인 필요] |
| 보관 | EC2 로컬 7일치 로테이션 | 신규 인프라 없음 |
| 스케줄러 | systemd timer (또는 cron) | timer는 실패 이력이 `journalctl`에 남음 |

- 덤프 대상: `meetjyou` 스키마 테이블만 — 마이그레이션 V1~V34에 프로시저·함수·트리거·이벤트·뷰 없음(2026-09-21 grep 확인) → `--routines`/`--events` 불필요. 추후 추가되면 옵션·권한 재검토
- `flyway_schema_history` 포함됨 → 복원 후 Flyway 이력 일치

## 적용 결과 (2026-10-05)

- RPO 24h 수용, 보관은 EC2 로컬 최근 7개(개수 기준 — 기간 기준 삭제는 연속 실패 시 정상 백업까지 지워 0개로 수렴)
- 스크립트·유닛은 레포 커밋 + 수동 설치(`deploy.yml` 자동 설치 기각: CI에 호스트 sudo 권한 부여)
- 클라이언트는 `docker run mysql:8.0.41` — EC2에 클라이언트 미설치, 서버와 버전 일치
- 덤프 무결성 장치 2중: `pipefail` + 덤프 말미 `-- Dump completed` 확인. 로컬 변형 테스트에서 둘 다 제거 시 20B 빈 백업이 "성공" 처리됨을 확인, 하나만 있어도 차단
- 백업 계정 `'backup'@'3.36.32.223'` `REQUIRE SSL`, 권한 `SELECT, SHOW VIEW, LOCK TABLES ON meetjyou.*`
- 실측 DB 크기: 덤프 원본 81KB / gzip 19KB (2026-10-05)
- 복원 리허설: 로컬 `mysql:8.0.41` 복원 → user·party·post·chat_message·notification row 수, 테이블 27개, `flyway_schema_history` V34 운영과 일치
- root 재설정 불필요 — 앱 계정이 `root@'%'`(전권 + GRANT OPTION)였음. 9/21 `Access denied`는 컨테이너 내부 `root@localhost`로 추정 [추정]. 앱의 슈퍼유저 사용 자체는 별도 과제

## 구현 체크리스트

1. [x] **백업 전용 DB 계정** 생성 — 앱 계정 재사용 금지(쓰기 권한 과다)
   - 권한: `SELECT, SHOW VIEW, LOCK TABLES` on `meetjyou.*` (`--single-transaction`이라 LOCK TABLES는 실사용 안 하나 mysqldump 호환용)
   - 호스트 제한: `'backup'@'3.36.32.223'` 수준 — Docker NAT 경유 시 실제 보이는 소스 IP 확인 필요 [추정]
2. [x] **자격 증명 파일** — `/etc/meetjyou-backup/my.cnf`(`[client] user/password/host/ssl-mode=REQUIRED`), `chmod 600`, root 소유. 명령줄·스크립트에 비밀번호 노출 금지(`ps`·셸 히스토리)
3. [x] **백업 스크립트** — 요지:
   ```bash
   set -euo pipefail
   mysqldump --defaults-extra-file=/etc/meetjyou-backup/my.cnf \
     --single-transaction --no-tablespaces --set-gtid-purged=OFF meetjyou \
     | gzip > "${DIR}/meetjyou-$(date +%F).sql.gz.tmp"
   mv "${DIR}/meetjyou-$(date +%F).sql.gz.tmp" "${DIR}/meetjyou-$(date +%F).sql.gz"
   find "${DIR}" -name 'meetjyou-*.sql.gz' -mtime +7 -delete
   ```
   - `pipefail` 필수 — 없으면 mysqldump 실패해도 gzip 성공으로 빈 백업이 "성공" 처리됨
   - `.tmp` → `mv`로 반쯤 쓴 파일이 정상 백업처럼 남는 것 방지
   - `--no-tablespaces` — MySQL 8.0.21+에서 `PROCESS` 권한 요구 회피
   - 클라이언트는 서버와 같은 8.0 계열 사용(`mysql:8.0.41` 이미지로 `docker run --rm` 실행도 가능)
4. [x] **실패 알림** — 실패 시 Discord 웹훅 전송(앱이 이미 `discord.webhook-url` + `DiscordAlertService` 사용 중 → 같은 채널 재사용 검토)
5. [x] **systemd timer 등록** + 수동 1회 실행으로 파일 생성·크기 확인
6. [x] **복원 리허설 (필수)** — 백업 파일을 로컬 `mysql:8.0.41` 컨테이너에 복원 → 주요 테이블 row 수를 운영과 대조 + `flyway_schema_history` 최신 버전 일치 확인. **복원해 본 적 없는 백업은 백업으로 간주하지 않음**
7. [x] ADR-0003 Action Item 2 체크 + 이 문서 상태를 "완료"로 갱신

## 2단계 (선택)

- S3 업로드(IAM 인스턴스 프로파일 + 버킷 lifecycle) — EC2 인스턴스 소실 시에도 백업 보존. 1단계만으로도 "DB(OCI)와 백업(AWS) 동시 소실"은 벤더 2곳 동시 장애라 확률 낮음 → 필요성은 1단계 운영 후 판단
- 주기 복원 리허설 자동화

## 남은 과제

- 2단계(S3 업로드·주기 리허설 자동화) — 위 "2단계" 참조, 1단계 운영 후 판단
- 실패 알림의 운영 환경 실전송은 미검증(로컬 웹훅 수신만 확인)
