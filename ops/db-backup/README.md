# DB 백업 (이슈 #142)

- 운영 MySQL(OCI)의 `meetjyou` 스키마를 앱 서버 EC2에서 매일 04:00 KST에 `mysqldump` → 다른 벤더(AWS)에 사본 보관
- RPO 24h, EC2 로컬 최근 7개 보관(개수 기준). S3 업로드는 미도입 — 배경·결정: `docs/ops/db-backup-plan.md`
- 레포 파일은 배포 워크플로(`deploy.yml`) 대상 아님 → 변경 시 아래 설치 절차로 수동 재설치

## 파일

| 파일 | 설치 위치 |
|---|---|
| `backup.sh` | `/usr/local/bin/meetjyou-db-backup` |
| `meetjyou-db-backup.service` / `.timer` | `/etc/systemd/system/` |
| (서버에서 작성) `my.cnf` — 백업 계정 자격 증명 | `/etc/meetjyou-backup/my.cnf` (root, 600) |
| (서버에서 작성) `backup.env` — `DISCORD_WEBHOOK_URL=...` | `/etc/meetjyou-backup/backup.env` (root, 600) |

## 백업 계정 (운영 DB, 최초 1회)

```sql
CREATE USER 'backup'@'<EC2 소스 IP>' IDENTIFIED BY '<password>';
GRANT SELECT, SHOW VIEW, LOCK TABLES ON meetjyou.* TO 'backup'@'<EC2 소스 IP>';
```

- 앱 계정 재사용 금지 — 앱 계정은 쓰기·관리 권한 보유
- 프로시저·트리거·이벤트·뷰 추가 시 덤프 옵션(`--routines`/`--events`)·권한 재검토

## 설치 (EC2)

```bash
sudo install -m 755 backup.sh /usr/local/bin/meetjyou-db-backup
sudo install -d -m 700 /etc/meetjyou-backup
sudo tee /etc/meetjyou-backup/my.cnf >/dev/null <<'EOF'
[client]
user=backup
password="<password>"
host=158.180.71.173
ssl-mode=REQUIRED
EOF
sudo chmod 600 /etc/meetjyou-backup/my.cnf
echo "DISCORD_WEBHOOK_URL=<url>" | sudo tee /etc/meetjyou-backup/backup.env >/dev/null
sudo chmod 600 /etc/meetjyou-backup/backup.env
sudo install -m 644 meetjyou-db-backup.service meetjyou-db-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now meetjyou-db-backup.timer
sudo systemctl start meetjyou-db-backup.service   # manual first run
```

## 확인

```bash
systemctl list-timers meetjyou-db-backup.timer
journalctl -u meetjyou-db-backup.service -n 20
sudo ls -l /var/backups/meetjyou/
```

- 파일명 시각은 호스트 시간대(UTC) 기준 — 04:00 KST 실행분은 `meetjyou-YYYYMMDD-1900.sql.gz` 전후
- 실패 시 Discord 알림(앱 알림과 같은 채널). 알림이 안 와도 `journalctl`에 실패 기록

## 복원

```bash
docker run -d --name restore -e MYSQL_ROOT_PASSWORD=pw -e MYSQL_DATABASE=meetjyou mysql:8.0.41
gzip -dc meetjyou-YYYYMMDD-HHMM.sql.gz | docker exec -i restore mysql -uroot -ppw meetjyou
```

- 운영 복원 시: 앱 중지 → 대상 DB 복원 → `flyway_schema_history` 최신 버전 확인 → 앱 기동
- 리허설 기준: 주요 테이블 row 수 운영 대조 + `flyway_schema_history` 최신 버전 일치
