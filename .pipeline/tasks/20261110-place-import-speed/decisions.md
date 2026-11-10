# Spec 비평 결정

- S 태스크, 비평 라운드 생략(단계 4-1: S는 선택)

## 설계 결정

| 항목 | 채택 | 기각한 대안 | 근거 |
|---|---|---|---|
| 배치 옵션 위치 | `application.yml` hikari `data-source-properties` | 운영 `SPRING_DATASOURCE_URL`에 쿼리 파라미터 추가 | 레포에 기록, dockerTest가 같은 값을 읽어 회귀 감지, 서버 수동 작업 없음 |
| 회귀 감지 방식 | 단일 연결 `Com_insert` 세션 카운터(1,000행 → 1) | `performance_schema` digest 집계, 배치 반환값(`SUCCESS_NO_INFO`) | 세션 카운터는 공유 MySQL의 다른 스펙·digest 초기화에 영향받지 않음, 반환값은 드라이버 구현 세부 |
| 기존 적재 dockerTest 연결 | `PlaceDb`가 application.yml 드라이버 옵션 사용 | 새 테스트만 옵션 적용 | 운영과 같은 다중 행 INSERT·다중 문 UPDATE로 AT-002~005 결과 동일함을 함께 확인 |
| 스테이징 비우기 시점 | 시작 + 끝(성공·실패 모두, 상태 공개 전) | 성공 때만 비우기(실패 분석용 보존) | 실패 원인은 상태 메시지·로그에 남고 같은 파일 재실행으로 재현 가능, 1GB DB VM 디스크·버퍼 풀 우선 |
| 끝 비우기 실패 처리 | 경고 로그, 결과 유지 | 실패로 전환·예외 전파 | 반영은 이미 끝남 — FAILED 표시는 거짓, 다음 실행 시작 때 다시 비움 |
