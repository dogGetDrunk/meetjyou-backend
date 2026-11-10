# Spec

## 요구사항

### REQ-001 스테이징 배치를 다중 행 INSERT로 전송

- 요구사항: `application.yml` `spring.datasource.hikari.data-source-properties`에 `rewriteBatchedStatements: true`
- 수용 기준: application.yml 드라이버 옵션으로 연 연결에서 `PlaceImportWriter.stage()`에 1,000행을 넘기면 서버의 INSERT 실행(`Com_insert`, 같은 세션) 증가가 1
  - 옵션을 지우면 1,000 → red
- 수용 테스트: `PlaceImportDockerTest` "스테이징 배치" — `SingleConnectionDataSource`(application.yml 드라이버 옵션)로 `SHOW SESSION STATUS LIKE 'Com_insert'` 전후 비교

### REQ-002 다중 행 INSERT에서도 적재 결과 동일

- 요구사항: 기존 적재 dockerTest가 운영과 같은 드라이버 옵션으로 실행
- 수용 기준: `PlaceDb`가 application.yml 드라이버 옵션을 연결 속성으로 사용, 기존 AT-002~005 전부 통과
- 수용 테스트: 기존 `PlaceImportDockerTest` 전체, `PlaceSearch*DockerTest`(PlaceDb 공용)

### REQ-003 적재 종료 시 스테이징 비우기

- 요구사항: `PlaceImportService.runImport()`가 성공·실패 모두 끝에서 `writer.clearStaging()` 호출. 시작 시 비우기는 유지(앞 실행이 비우기 전에 프로세스가 죽은 경우)
  - 상태(SUCCEEDED/FAILED) 공개 전에 비움 → 상태가 RUNNING이 아니면 스테이징은 이미 0행
  - 끝 비우기의 `DataAccessException`은 경고 로그만, 원래 결과·예외 유지
- 수용 기준
  - 성공 적재 후 `place_import_staging` 0행
  - 검증 실패(시·도 50% 미만)·반영 도중 실패 후 0행
  - 끝 비우기가 실패해도 상태 SUCCEEDED, 성공 알림
- 수용 테스트
  - `PlaceImportDockerTest` AT-002(성공), AT-005 50% 미만·반영 도중 실패에 스테이징 0행 단언 추가
  - `PlaceImportApiIntegrationTest` "끝 비우기 실패" — mock writer의 두 번째 `clearStaging()`이 예외 → SUCCEEDED

## 확정 인터페이스

### API 시그니처·DTO

- 변경 없음

### DB 스키마

- 변경 없음

### 테스트 지원

- `support/ApplicationDriverProperties.kt`: application.yml의 `spring.datasource.hikari.data-source-properties.*`를 `Map<String, String>`으로 읽음
  - `JdbcSessionStateRoundTripDockerTest`의 private `loadDriverProperties()`를 옮겨 공용화

## 변경 파일

- `src/main/resources/application.yml`
- `src/main/java/.../place/importer/PlaceImportService.kt`
- `src/dockerTest/java/.../support/ApplicationDriverProperties.kt`(신규), `support/PlaceDb.kt`, `config/JdbcSessionStateRoundTripDockerTest.kt`
- `src/dockerTest/java/.../place/PlaceImportDockerTest.kt`
- `src/test/java/.../place/importer/PlaceImportApiIntegrationTest.kt`

## 마일스톤

1. 테스트 먼저(REQ-001 red 확인) → 옵션 추가(green)
2. 스테이징 비우기 + 테스트
3. gate(`./gradlew test build`) + `./gradlew dockerTest`
