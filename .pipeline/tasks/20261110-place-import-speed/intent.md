# Intent

> 승인 기록은 `approval.json`(`agent-pipeline approve-intent`), 승인 후 이 파일 수정 금지.

## 해결할 문제

- 11/9 운영 소상공인 적재 2시간 57분 중 스테이징 2시간 34분(초당 약 110행)
  - 원인: `PlaceImportWriter.stage()`가 `jdbc.batchUpdate`로 1,000행씩 보내지만 MySQL 드라이버 옵션 `rewriteBatchedStatements`가 꺼져 있어 실제로는 행마다 DB 왕복(EC2 → OCI DB)
- 적재가 끝나도 `place_import_staging`에 약 102만 행이 다음 적재 전까지 남음
  - 지금은 적재 시작 때만 `TRUNCATE`. 1GB DB VM에서 디스크·버퍼 풀을 쓸데없이 차지

## 의도

- 스테이징 배치를 실제 다중 행 INSERT로 보내 왕복 수를 약 1,000분의 1로 줄임
- 적재가 끝나면(성공·실패 모두) 스테이징 테이블을 비움

## 포함 범위

- `application.yml` hikari `data-source-properties`에 `rewriteBatchedStatements: true` 추가
  - 기존 `useLocalSessionState`와 같은 위치. 레포에서 버전 관리되고 테스트에도 그대로 적용됨
  - 영향 범위: `jdbc.batchUpdate`는 여행지 적재(스테이징 INSERT, 재매칭 UPDATE)에만 있음. JPA `saveAll`은 Hibernate 배치(`hibernate.jdbc.batch_size`)가 꺼져 있어 변화 없음. 재매칭 건수는 이미 배치 결과 대신 쌍 개수로 셈
- `PlaceImportService.runImport()` 끝(성공·실패 공통)에 스테이징 `TRUNCATE`
  - 비우기 자체가 실패해도 적재 결과(SUCCEEDED/FAILED)는 바꾸지 않고 경고 로그만 남김. 다음 적재 시작 때 어차피 다시 비움
- 테스트(dockerTest, 실제 MySQL)
  - application.yml 드라이버 옵션으로 연결했을 때 스테이징 N행이 INSERT 문 N개보다 훨씬 적게 실행됨(문 실행 횟수로 확인) → 옵션을 지우면 red
  - 기존 적재 dockerTest(AT-002~005)를 같은 드라이버 옵션으로 돌려 다중 행 INSERT에서도 결과가 같음을 확인
  - 성공·실패 적재 후 스테이징 행 0

## 제외 범위

- 반영 단계(23분) 속도 — 이미 집합 SQL을 청크로 실행, 이번 원인과 무관
- 운영 서버 환경 변수(`SPRING_DATASOURCE_URL`) 변경 — 기각: 레포에 안 남고 테스트가 못 봄, 서버 수동 작업
- 스테이징 배치 크기(1,000) 조정, JPA 배치 활성화

## 성공 기준

- `./gradlew test build`(gate)와 `./gradlew dockerTest` 통과
- 위 dockerTest가 옵션 제거 시 red, 적용 시 green임을 직접 확인
- 적재 성공·실패 후 `place_import_staging` 0행
- (결정 필요, 아래) 운영 재적재 실측: 스테이징 단계 2시간 34분 → 15분 이하

## 결정 필요

- 운영 재적재 실측을 이 태스크에 넣을지
  - 넣음: 배포 후 관리자 API로 같은 분기 파일 재적재, 로그로 스테이징·반영 시간 측정 → result에 기록. 같은 파일이라 데이터 변화 없음(AT-003, 갱신 0건), 반영 단계 약 20분간 DB 부하
  - 뺌: 테스트로 왕복 감소만 확인, 운영 효과는 다음 분기 갱신 때 확인

## 작업 크기

S — 설정 1줄 + 서비스 정리 단계 1곳 + dockerTest, 기존 적재 로직 변경 없음

## 출처

- 노션 태스크: [여행지 검색 API 구현 (작업 ID 241)](https://app.notion.com/p/40f56d9a97d981c592f3ccc0e261e2ad) TODO "적재 속도 개선", "적재 후 스테이징 테이블 비우기"
- GitHub 이슈: 없음
- 근거 실측: PR #281(11/9 운영 적재 기록)
