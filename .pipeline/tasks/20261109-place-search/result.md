# 결과

> 태스크 종료 시 메인 설계 에이전트가 작성. 포트폴리오·회고 재료.

## 결과 요약

- 여행지 검색 1차: 지역 286행 시드, 소상공인 상가 적재(ADMIN API, 재실행·재매칭·완전성 검사), 지역·장소 통합 검색 API, 모집글·파티·계획서 대표 여행지 참조(전환 기간 방식)
- 단일 모드: spec → 하위 설계 비평 1라운드(8건 전부 수용) → 이 세션 구현 → PR 1개
- 대상 데이터 실측(2026-09 파일): 전체 275만 행 중 대상 업종 1,023,599건, 시·군·구 256 + 일반구 상위 시 13

## 머지된 PR

| 티켓 | PR | 등급 | 구현 / 리뷰 모델 | 라운드 | 토큰 (output / 전체, `logs/tokens.md`) |
|---|---|---|---|---|---|
| (단일 모드) | 이 태스크 PR | M | Claude Opus 5.5 (구현) / 비평 sub_designer gpt-6.1-sol high | 비평 1 | 미집계 |

## 핵심 결정과 기각한 대안

- **API 전환 = 전환 기간**(사용자 결정): 기존 `location`/`destination` 필수 유지 + `regionCode`·`placeId` 선택 / 기각: 한 번에 교체 + 강제 업데이트(프론트 합의 전, 현재 앱 글 작성 깨짐)
- **기존 행 = '00' 미지정 지역**(사용자 결정) / 기각: 실제 지역 기본값(집계 오염), NULL 유지
- **관광공사 제외**: 콘텐츠랩 정책상 DB 저장은 '서버구축 방식' 승인 필요(미신청). 장소 테이블에 `source` 구분을 두어 승인 후 적재기만 추가
- **지역 = 코드 문자열 + 메모리 카탈로그**(엔티티 관계 아님) — 286행·마이그레이션으로만 변경 → 응답당 지역 쿼리 0, 기존 엔티티 생성자 불변 / 기각: `@ManyToOne Region`(생성자·H2 FK·응답 직후 연관 미반영 문제)
- **장소 = `@ManyToOne(LAZY)` + 기존 `default_batch_fetch_size: 100`** → 목록 N+1 없음(AT-011: 2건·10건 SQL 수 동일)
- **적재 반영 순서**: 같은 키 갱신 → 1:1 재매칭 → 신규 → 비활성화(마지막, 앞 단계 전부 성공 시에만), 5,000행 단위 트랜잭션 / 기각: 단일 트랜잭션(102만 행 X락이 모집글 FK 검사를 수 분간 막음)
- **번호 바뀐 가게 재매칭**(분기당 약 4.7만 곳): 키 매칭 후 남은 양쪽을 전체 값 `(시·군·구, 검색용 이름, 도로명주소)`로 1:1일 때만 / 기각: 키만 사용(멀쩡한 가게가 "영업 정보 없음")
- **완전성 검사 = 시·도별 50%** / 기각: 전체 50%(지역 CSV 누락 통과), CSV 16개 고정(시·도 개편 시 깨짐)
- **검색 = MySQL ngram FULLTEXT, 공백 제거 이름 구문 검색** / 기각(보류): 메모리 인덱스 — 2GB 서버, 운영 실측 후 판단
- **실행기는 서비스 내부 단일 스레드**: `Executor` 빈 등록 시 Boot `applicationTaskExecutor`가 꺼짐(11/7 스케줄러 함정과 같은 종류)
- **다운로드 = `SbizFileSource` 인터페이스**: 테스트에서 fixture zip·차단 소스로 전체 적재 경로 검증

## spec 대비 검증

- gate(`agent-pipeline gate` = `./gradlew test build`): exit 0, BUILD SUCCESSFUL — test 791개 통과·스킵 0, detekt·Konsist 통과
- `./gradlew dockerTest`: 75개 통과·스킵 0 (신규 17: PlaceImportDockerTest 8, PlaceSearchDockerTest 6, RegionSchemaDockerTest 3)
- AT 매핑: AT-001·012 `RegionSchemaDockerTest` / AT-002~005 `PlaceImportDockerTest` / AT-006 `PlaceImportApiIntegrationTest` / AT-007 `SbizFileClientTest` / AT-008 `PlaceSearchDockerTest` / AT-009·011 `TravelDestinationApiIntegrationTest` / AT-010 `TravelDestinationResolverTest`
- 회귀 검출 확인(일부러 버그 주입 → red 확인 후 원복): 비활성화를 맨 앞으로 → AT-004·005 실패 3건, 재매칭 1:1 필터 제거 → AT-004 실패, `default_batch_fetch_size=1` → AT-011 SQL 수 비교 실패
- 테스트가 잡은 실제 버그: 포털 응답에 charset이 없으면 `String` 본문이 ISO-8859-1로 해석돼 한글 파일명이 깨짐 → 바이트로 받아 UTF-8 디코딩
- spec 대비 변경
  - `CreatePartyResponse`는 미변경(공개 파티 생성 API가 비활성이라 사용처 없음)
  - 검색어 길이는 공백을 모두 뺀 길이로 검사(spec "앞뒤 공백 제거"보다 엄격, 검색용 이름과 같은 기준)
  - 요청 DTO의 새 필드는 맨 뒤에 기본값과 함께 추가(위치 인자 호출 호환)
- detekt baseline: `PostService`·`PartyService`·`PlanService` 생성자 LongParameterList 항목 3개를 새 시그니처로 **교체**(항목 수 불변, 이미 동결된 지적의 시그니처 변경 — #222 커밋과 같은 방식). 새 지적은 `@Suppress`+이유(`Place` 생성자, `GetPostResponse.of`, 백그라운드 잡 catch) 또는 코드 수정

## 남은 후속 작업

- **배포 후 사람이 실행**: ADMIN 토큰으로 `POST /api/v1/admin/places/imports` → `GET .../latest`로 SUCCEEDED·건수 확인, 소요 시간·메모리 기록 / `GET /api/v1/places/search?q=카페` 등 p95 실측 → 기준 초과 시 메모리 인덱스 또는 지역 선택 강제 태스크
- 운영 DB 계정 최소 권한 분리 시 `place_import_staging` TRUNCATE에 DROP 권한 필요
- 프론트 합의: 검색 응답·`region`/`place` 필드, 앱 반영 후 문자열 필드 제거 태스크
- 다음 태스크 후보: 마커 장소 참조, 직접 입력 장소·관리자 승인, 분기 자동 갱신(`@Scheduled`), 관광공사 적재(승인 후), 추천 집계
- 11/9 회의 미개최 → 지역 단계·일반구 화면 표기 결정 대기(백엔드는 응답에 시·도·상위 시 모두 제공)
