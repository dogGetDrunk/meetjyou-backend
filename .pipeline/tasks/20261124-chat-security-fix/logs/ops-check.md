# 운영 확인 (읽기 전용, 2026-11-24)

## 앱 로그 (EC2 `spring_boot` 컨테이너)

- 남아 있는 범위: 2026-11-22T08:09:43 ~ 2026-11-24T14:13:23 (2,287줄, 11/22 배포로 컨테이너 교체, 이전 로그 없음)
- `Data too long` 0건, `dedup_key` 0건
- 채팅 사용 흔적 없음: `STOMP CONNECT authorized` 0건, `Notification receivers resolved for chat message` 0건
- 로그 수집: Alloy는 `/actuator/prometheus` 메트릭만 Grafana Cloud로 전송, 앱 로그는 외부 보관 없음
- 결론: 로그로는 7/19~11/22 실제 유실 여부 판단 불가

## 운영 MySQL `sql_mode`

- OCI DB VM `mysql:8.0.41` 컨테이너, 실행 인자는 charset, collation, time-zone 3개뿐
- 서버 설정 파일(호스트 `/etc/mysql`, 컨테이너 안 `/etc/mysql`, `/etc/my.cnf*`)에 `sql_mode` 지정 없음 → MySQL 8.0 기본값(`STRICT_TRANS_TABLES` 포함)
- 레포 설정에도 `sql_mode`, `sessionVariables` 없음
- 미확인: `SPRING_DATASOURCE_URL`(GitHub 시크릿)에 세션 `sql_mode` 덮어쓰기가 있는지. 시크릿이라 열지 않음
- 결론: strict 모드일 가능성이 높음 → 7/19 이후 오프라인 수신자 채팅 푸시는 저장 단계에서 실패했을 가능성이 높음(미확정)

## 확정하려면

- 운영 DB에서 `notification_outbox` 의 `type = 'CHAT_MESSAGE'` 행 수(7/19 이후)와 `MAX(CHAR_LENGTH(dedup_key))` 조회 → 0행이면 유실 확정, 100자 행이 있으면 non-strict로 잘려 저장된 것
- DB 자격 증명 사용이 필요해 사람 승인 전 미실행
