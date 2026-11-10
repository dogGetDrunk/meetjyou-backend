# 결과

> 태스크 종료 시 메인 설계 에이전트가 작성. 포트폴리오·회고 재료.

## 결과 요약

- 운영 '카페' 검색 9초(p95 9.1초)의 원인을 로컬 재현(102만 행, 버퍼 풀 128MB)으로 확정하고, 인프라·앱 메모리 추가 없이 쿼리·인덱스만 바꿔 해결
  - 원인: `ORDER BY (정확일치), id`가 일치 행 2.5만 건을 전부 읽게 함 → 테이블 거의 전 페이지가 128MB 버퍼 풀을 매번 넘침(반복 검색도 물리 읽기 5~7천 회). 구문 검색은 정렬이 없어도 후보 행을 전부 읽어 위치 확인
  - 해결: 정렬 제거 + 구문 대신 토큰 AND(인덱스만으로 답함) + 가져온 행만 `LIKE` 확인 → `LIMIT`에서 멈춤. 작은 지역은 새 커버링 인덱스만 훑는 경로
- 로컬 재현 검색 1회 전체 p95: '카페' 전국 343.5 → 38.1ms, 부산 416.3 → 20.7ms, 인천 139.4 → 1.4ms, '이디야커피' 309.2 → 17.4ms. 논리 페이지 읽기 약 100분의 1(129,162 → 1,196)
- 희귀어 비악화 기준(p95 +10ms 이내) 충족: 최악 지점(기준값 근처 지역) +7.8ms
- 단일 모드: intent → spec(로컬 재현 실측 포함) → 비평 1라운드(6건 전부 수용) → 이 세션 구현 → PR 1개

## 머지된 PR

| 티켓 | PR | 등급 | 구현 / 리뷰 모델 | 라운드 | 토큰 (output / 전체, `logs/tokens.md`) |
|---|---|---|---|---|---|
| (단일 모드) | 이 태스크 PR | M | Claude Opus 5.5 (구현) / 비평 sub_designer gpt-6.1-sol high | 비평 1 | 미집계 |

## 핵심 결정과 기각한 대안

- **방식 A(쿼리·인덱스 변경) 채택** / 기각: 앱 메모리 인덱스(힙 37~90MB 추정·102만 행 재로딩·갱신 반영 로직, A가 목표 달성하므로 비용만 증가), 정렬 제거만(작은 지역 + 흔한 단어 3.6초·여러 글자 구문 4.6초 환산 남음), DB 증설(intent 결정 2)
- **검색 3경로**: 정확 일치(커버링 인덱스 등호) → FTS 토큰 AND + `LIKE`(지역 없음·큰 지역) 또는 커버링 인덱스 `LIKE`(작은 지역)
  - 기각: 지역 필터는 항상 커버링 경로(큰 지역 희귀어가 지역 전체를 훑음), 시·도/시·군·구 단계로 경로 선택(세종처럼 작은 시·도가 FTS로 가면 수천 행)
- **경로 기준값 = 지역 활성 장소 3만**(구현 중 10만에서 내림): 희귀어 비악화 기준을 실측으로 확인해 결정. 측정 후 기준을 느슨하게 바꾸는 대신 설계값을 고침
- **토큰 규칙을 MySQL ngram 인덱싱과 일치**: 불용어 포함 토큰 제외('PizzaHut'의 `pi`), 문장부호로 단어 분리, 1글자 단어 제외 → FTS 후보 ⊇ `LIKE` 일치라 두 경로 결과가 같음. 쓸 토큰이 없으면 빈 결과(현재와 같음)
- **힌트 2개**(측정·테스트로 발견): `NO_MRR`(지역 코드 여러 개면 MRR이 일치 항목을 전부 모아 `LIMIT`에서 못 멈춤, 623 vs 19건), `FORCE INDEX`(IN 200개 초과 시 통계 추정 → 통계 없는 DB에서 전체 스캔)
- **지역별 건수 캐시**: 세대 객체 CAS + `REQUIRES_NEW` 조회로 적재 직후 옛 건수 재게시 차단 / 기각: `synchronized` 직렬화
- **V47**: `(region_code, active, search_name)` 추가 + 같은 선두의 `idx_place_region_active`(30MB) 삭제 → 작은 버퍼 풀 경쟁 감소, FK 인덱스 요구도 충족

## spec 대비 검증

- gate(`agent-pipeline gate` = `./gradlew test build`): exit 0, BUILD SUCCESSFUL — 테스트 807개 통과·스킵 0, detekt·Konsist 통과
- `./gradlew dockerTest`: 175개 통과·스킵 0
- AT 매핑: AT-001 `PlaceSearchCostDockerTest` / AT-002 `PlaceSearchDockerTest`(생성 한 줄만 변경) + `PlaceSearchPathDockerTest` / AT-003·004·005(dockerTest) `PlaceSearchPathDockerTest` / AT-005(단위) `PlaceRegionStatsTest`·`PlaceImportStatsInvalidationTest` / AT-006 `RegionSchemaDockerTest` / AT-007 `PlaceSearchQueryTest`
- 회귀 검출 확인(버그 주입 → red → 원복)
  - FTS 경로에 `ORDER BY id` → AT-001 4건 실패
  - `NO_MRR` 제거 → AT-001 지역 인덱스 경로 2건 실패(처음엔 green → 테스트 규모에서 MRR이 비용상 안 켜져서 `mrr_cost_based=off`와 검색당 상한 추가 후 red)
  - `FORCE INDEX` 제거 → AT-001 8건 전부 실패
  - 토큰 AND 대신 구문 → AT-001 보조 지표(페이지 읽기) 실패 — 구문 위치 확인 읽기는 `ROWS_EXAMINED`에 안 잡혀 보조 지표가 잡음
  - 적재 후 `invalidate()` 삭제 → AT-005 단위 실패 / 세대 확인 없이 게시 → 경합 테스트 실패
- 테스트가 잡은 구현 결함: `%`·`!`가 든 검색어에서 인덱싱 안 된 토큰을 요구해 FTS 경로만 0건(AT-003·004) → 토큰 규칙 수정
- 1차 CI 실패(PR #282 첫 실행): `PlaceImportApiIntegrationTest`가 `SUCCEEDED`를 본 직후 성공 알림을 동기로 확인 — 알림은 상태 공개 뒤에 나가는 기존 경합. 같은 자리에 넣은 `invalidate()`도 AT-005 dockerTest를 가끔 깨뜨릴 수 있어 **무효화를 상태 공개 전으로 이동**, 테스트의 성공·실패 알림 확인은 `eventually`로
- 비평 결정: `decisions.md`(C-r1-01~06 전부 수용), spec 변경: `spec-changelog.md`(비평 6 + 구현 중 5)
- 측정 원자료: `spec.md` "로컬 재현 실측"·"구현 후 로컬 재현 결과", 스크립트 `scripts/`

## 배포 후 사람 확인 항목

- **배포 시간**: V47이 운영 place 102만 행에 인덱스를 추가(로컬 4.6초, 운영은 디스크·버퍼 풀 때문에 더 길 수 있음). Flyway가 기동 중 실행해 그동안 앱이 안 뜸 → 한가한 시간에 배포
- 운영 '카페' 전국·`region=11` p95 1초 이하, 막창·스타벅스·해운대 등 기존 측정 검색어 비악화(intent 성공 기준)
- 운영 MySQL 불용어 설정이 기본값인지(`innodb_ft_enable_stopword=ON`, `innodb_ft_server_stopword_table` 없음) — 다르면 영문 검색 결과가 달라질 수 있음
- 건수 캐시 첫 로딩(로컬 154ms)은 기동 후·적재 후 첫 지역 필터 검색 1회에만 붙음

## 남은 후속 작업

- 적재 속도(`rewriteBatchedStatements`)·적재 후 스테이징 비우기 → 별도 S 태스크(intent 결정 1)
- 커버링 경로의 `LIKE`는 `utf8mb4_0900_ai_ci` 비교라 CPU가 듦(1,000항목당 약 0.3ms). 운영 DB CPU가 더 느리면 기준값(3만) 재조정 검토
