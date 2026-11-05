# #225·#228 수정 전후 부하 테스트 (2026-11-05)

> 팀 보드 태스크 "채팅방 목록·공지 발송·게시글 상세 서버 속도 개선"의 TODO "수정 전후 부하 테스트로 효과 확인"

## 결론

| 항목 | 수정 전 | 수정 후 | 변화 |
|---|---|---|---|
| 채팅방 목록 요청당 SQL 수 (방 30개) | 37 | 7 | −81% |
| 채팅방 목록 중앙값 (1 VU) | 39.2ms | 18.7ms | −52% |
| 게시글 상세 실패율 (100 VU) | 10.06% (151/1500) | 0% (0/3176) | 실패 제거 |
| 게시글 상세 조회수 누락 | 109건 (8.1%) | 1건 차이 | 사실상 제거 |
| 공지 발송 응답 (수신 4,502명) | 25.8~26.6s | 3.1~3.5s | 약 8배 단축 |

- 세 개선 모두 효과 확인
- 게시글 상세는 "느린 것"이 아니라 **풀 고갈로 인한 실패·조회수 유실**이 핵심 — 수정 후 Hikari 5초 타임아웃 835회 → 0회

## 환경

- 로컬 재현, 운영 미사용 — 운영엔 이미 수정본이 배포돼 "수정 전" 측정 불가, 공지 발송 측정은 실제 유저에게 푸시가 나가므로 운영 불가
- DB: throwaway `mysql:8.0.41` 컨테이너, `--cpus 0.25`(운영 DB VM 버스트 제한 근사), 버전별 별도 database
- 앱: 같은 머신에서 bootJar 실행, 프로필 `db,secrets,release`, `-Xmx1g -XX:+UseSerialGC`, Hikari 설정은 운영과 동일(pool 50, connection-timeout 5s)
  - 수정 전 = `3a3bbbc`(#260 머지 직전 main), 수정 후 = `18d569e`(#265 머지 후 main)
- 데이터(`gen_seed.py`, 두 DB 동일): 유저 5,001명(NOTICE 알림 끈 유저 500명), 파티·채팅방 30개, 방마다 메시지 40개
  - 채팅방 목록 요청자 50명: 30개 방 전부 JOINED, 방마다 안 읽은 메시지 20개(`last_read_message_id` 설정 → 수정 전 방별 쿼리 경로)
  - 게시글 상세 조회자 500명, 게시글 30개
- 인증: 로컬 전용 임시 JWT 키로 토큰 발급(`mint.py`), 운영 시크릿 미사용
- 부하기: k6, 순서 = 워밍업 20s → 채팅방 목록 → 게시글 상세 → 공지 발송 3회

## 상세 결과

### 채팅방 목록 `GET /api/v1/chat/rooms`

| 조건 | 지표 | 수정 전 | 수정 후 |
|---|---|---|---|
| 1 VU 30s | 처리량 | 18.5 rps | 27.5 rps (+49%) |
| | 중앙값 / p95 | 39.2 / 99.7ms | 18.7 / 85.6ms |
| 20 VU 60s (DB 포화) | 처리량 | 5.1 rps | 6.5 rps (+27%) |
| | 중앙값 / p95 | 3.88 / 4.80s | 3.00 / 3.99s |

- SQL 수는 performance_schema digest 합계 / 순차 요청 20회
- 포화 구간 개선폭(+27%)이 SQL 감소폭(−81%)보다 작음 — 없어진 30개는 방 하나 범위의 작은 count라 문장당 비용이 낮았던 것으로 추정 [추정, 문장별 시간 미측정]
- 운영은 EC2→OCI DB 왕복이 있어 왕복 30회 감소 효과가 로컬보다 클 것으로 예상 [추정, 미측정]

### 게시글 상세 `GET /api/v1/posts/{uuid}` (100 VU 60s, 로그인 조회자)

| 지표 | 수정 전 | 수정 후 |
|---|---|---|
| 처리량 | 24.0 rps | 51.8 rps (2.2배) |
| 실패 (HTTP 500) | 151 / 1500 (10.06%) | 0 / 3176 |
| 중앙값 / p95 / 최대 | 5.11 / 6.93 / 10.46s | 1.79 / 3.39 / 6.78s |
| 4초 초과 응답 | 896건 (59.7%) | 81건 (2.6%) |
| Hikari 5초 타임아웃 (앱 로그) | 835회 | 0회 |
| 첫 조회 요청 vs 실제 증가한 조회수 | 1,349 vs 1,240 (109건 누락) | 2,871 vs 2,870 |

- VU 100 > pool 50으로 일부러 풀보다 많은 동시 요청 — 수정 전에는 요청이 연결 1개를 쥔 채 `REQUIRES_NEW`로 2번째를 기다려 서로 막힘
  - 2번째 연결 대기 타임아웃 → 조회수 증가 실패(`runCatching`이 삼켜 200 응답) 109건 = 앱 로그 "Failed to increment view count" 109건과 일치
  - 나머지 타임아웃은 요청 자체 실패로 이어짐 → k6 기준 500 응답 151건
- 수정 후 1건 차이는 앱 로그의 증가 실패 0건이라 원인 미확인 (테스트 종료 시점 경계로 추정)
- 운영 실트래픽은 이 수준의 동시 요청이 아님 — 운영 의미는 "평소 속도 향상"보다 "트래픽 몰릴 때 실패·유실 절벽 제거"

### 공지 발송 `POST /api/v1/notices` (`notify=true`, `critical=false`)

| 회차 | 수정 전 | 수정 후 |
|---|---|---|
| 1 | 25.85s | 3.11s |
| 2 | 26.62s | 3.48s |
| 3 | 25.95s | 3.08s |

- outbox 적재 행 수는 양쪽 모두 매회 4,502건 — 결과 동일, 시간만 단축
- 응답 시간 = 공지 저장 + AFTER_COMMIT 동기 발송 적재(요청 스레드) 전체
- 남은 약 3초는 대부분 outbox 4,502건 건별 IDENTITY insert로 추정 — 수정 후 1회차 SQL 4,528개 중 4,502개가 insert [추정, 시간 분해 미측정]. 이번 범위 밖, 공지 발송 빈도상 문제 관측 전엔 보류
- SQL 수(`results/*/broadcast.txt`의 `stmts`)는 outbox 발송 스케줄러 쿼리가 섞여 회차마다 흔들림 — 1회차 기준 수정 전 10,391 / 수정 후 4,528

## 한계

- 로컬 측정 1회씩(공지 발송만 3회) — 절대값은 운영과 다름, 전후 비교용
- 운영 재측정은 하지 않음(D2: 배포 후 사람 작업). 운영 확인이 필요하면 공개 API·인증 sweep(`ProdFullReadScenarioSimulation`)은 가능하지만 이 세 경로를 겨냥한 시나리오가 아님

## 재현

```bash
docker run -d --name meetjyou-lt --cpus 0.25 -p 127.0.0.1:3307:3306 -e MYSQL_ROOT_PASSWORD=lt -e TZ=Asia/Seoul \
  mysql:8.0.41 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
python3 -c "import os,base64;print(base64.b64encode(os.urandom(48)).decode())" > jwt.key
./run-app.sh after 18082 <after bootJar> <firebase json> &      # Flyway가 lt_after 스키마 생성
python3 gen_seed.py > seed.sql && docker exec -i meetjyou-lt mysql -uroot -plt lt_after < seed.sql
python3 mint.py jwt.key > tokens.json
./measure.sh after 18082          # 결과: results/after/
# 앱 종료 후 before 버전으로 같은 순서 반복 (lt_before)
```

- `jwt.key`·`tokens.json`·`seed.sql`은 생성물이라 커밋 제외
