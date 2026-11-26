# 결과

## 결과 요약

- 모집글, 파티, 여행계획서가 관광공사 장소를 `contentid`(`tourContentId`)로 참조 가능해짐(노션 244 TODO "장소 참조를 출처 + 외부 id 구조로 변경")
- 공사 API 추가 호출과 장소 이름 저장 없이 해결, 기존 앱 요청(`tourContentId` 없음)은 동작 불변
- ADR-0004 Decision을 실제 구조(`place_id` 유지 + `tour_content_id` 추가)로 개정

## 변경 내용

- 스키마: `V48`(post), `V49`(party), `V50`(plan) 각각 `tour_content_id VARCHAR(20) NULL` + CHECK `chk_<table>_single_place`(`place_id IS NULL OR tour_content_id IS NULL`), 파일마다 `ALTER` 한 문장
- `TravelDestination` 에 `tourContentId`, 엔티티 3개에 컬럼과 대입 추가 → 파티는 기존 `createParty`, `syncFromPost` 경로로 그대로 복사
- `TravelDestinationResolver`
  - `tourContentId` 는 `^[0-9]{1,20}$` 형식만 검증, 위반 시 400
  - `placeId` 와 함께 오면 장소 조회 전에 400
  - 지역 없이 오면 미지정 `00`, 공사 API 호출과 지역 일치 검사 없음
  - 수정 시 세 값이 저장값과 같으면 검사 없이 유지(기존 재전송 규칙 확장)
- 요청 DTO 4개(`CreatePostRequest`, `UpdatePostRequest`, `CreatePlanRequest`, `UpdatePlanRequest`)에 선택 항목 `tourContentId`
- 응답 DTO 9개에 `tourContentId`(값이 없으면 `null` 로 필드 존재), `GetPlanResponse` 는 기본값 `null` 로 예전 `planSnapshot` JSON 호환
- JSON 계약: `"126508"` 과 따옴표 없는 `126508` 모두 허용(같은 형식 검증), 명시적 `null` 은 생략과 같음
- 문서: ADR-0004 Decision, Consequences "미확인", 재검토 조건 정정과 개정 이력, `.claude/rules/place.md` 관광공사 참조 규칙

## 비평 결정

| 항목 | 결정 | 요약 |
|---|---|---|
| C-r1-01 | 기각-감수(damiannlee 승인) | `CreatePostRequest` 필드 추가로 멱등 키 해시 변경 → 배포 경계 재시도만 409, 중복 없음. 구 해시 호환 비교는 과설계 |
| C-r1-02 | 수용 | 응답 9개를 각각 단언 |
| C-r1-03 | 수용 | JSON 토큰 계약 명시와 테스트 |
| C-r1-04 | 수용 | store와 관광공사 사이 출처 전환을 모집글(파티 포함), 계획서, MySQL UPDATE에서 확인 |
| C-r1-05 | 수용 | `gate` 가 돌리지 않는 dockerTest를 로컬에서 실행하고 결과 기록 |
| C-r1-06 | 수용 | 복구 절차 문서 대신 원인 제거: 테이블별 마이그레이션 3개, 각 `ALTER` 한 문장 |
| C-r1-07 | 수용 | 카운팅 `TourPlaceSource` fake로 쓰기와 조회 흐름에서 관광공사 호출 0회 단언 |
| C-r1-08 | 수용 | 표시 필드를 모집글 `location`, 파티와 계획서 `destination` 으로 정정 |

## 구현 후 검증

- 라운드 1, 검증 커밋 `71f77e5`, 7건, 실행 실패와 재시도 없음

| 항목 | 결정 | 요약 |
|---|---|---|
| V-r1-01 | 기각-감수(damiannlee 승인) | `CreatePlanRequest` 도 C-r1-01과 같은 멱등 해시 변경, 같은 근거로 감수 |
| V-r1-02 | 수용 | ADR "미확인" 항목의 "응답 시마다 재조회" 문구가 이번 결정과 충돌 → 정정 |
| V-r1-03 | 수용 | 출처 전환 뒤 모집글 GET, 계획서 GET으로 저장 상태 단언 추가 |
| V-r1-04 | 수용 | 토큰 테스트를 리터럴 JSON 문자열 본문으로 변경 |
| V-r1-05 | 수용 | 응답 값이 JSON 문자열 노드인지 확인 |
| V-r1-06 | 수용 | 아래 gate 근거에 dockerTest 결과 기록 |
| V-r1-07 | 수용 | 아래 후속 작업에 마이그레이션 복구 절차 기록 |

## gate 근거

- 최종 `agent-pipeline gate`(`./gradlew test build`, detekt, Konsist 포함): exit 0, `BUILD SUCCESSFUL`
  - 검증 반영 후 전체 실행 4분 25초, 마지막 확인 실행은 입력이 같아 전 태스크 `UP-TO-DATE`
- `./gradlew test`: 153 suite, 882 테스트, 실패 0, skip 0
- `./gradlew dockerTest`(MySQL 8.0.41, 운영과 같은 버전): 28 suite, 181 테스트, 실패 0, skip 0
  - `TourContentRefSchemaDockerTest`(AT-001) 5건 통과: 컬럼 형식, 동시 지정 CHECK 위반(오류 코드 3819), 단일 지정 성공, 한 UPDATE 출처 전환 두 방향 성공, 기존 FK 유지
  - 우려했던 "참조 동작 있는 FK 컬럼에 CHECK 금지"(오류 3823)는 `fk_*_place` 에 참조 동작이 없어 발생하지 않음
- 첫 gate에서 `PostDetailConnectionIntegrationTest` 3건이 컨텍스트 로드 중 `OutOfMemoryError: Java heap space` 로 실패
  - 원인: 새 통합 테스트가 고유 설정으로 Spring 컨텍스트를 하나 더 캐시에 올림(테스트 JVM 기본 힙 512MB, `@SpringBootTest` 38개)
  - 조치: 새 테스트를 `TravelDestinationApiIntegrationTest` 와 같은 컨텍스트 설정(H2 URL, 속성, `@Import`, `@MockBean`)으로 맞춰 컨텍스트 공유, 재실행 통과
  - 기각: 테스트 `maxHeapSize` 상향(빌드 전체 설정 변경, 컨텍스트 증가를 가림)

## 핵심 결정과 기각한 대안

- 저장 구조 = `place_id` 유지 + `tour_content_id` 추가(사람 선택)
  - 기각: `place_source` + `place_ref` 통일(ADR 원문). 출처 확장성은 있으나 소상공인 FK 무결성 상실, 목록 응답 배치 로딩 재작성. 세 번째 출처 계획 없음
- 응답 = `contentId` 만, 화면 이름은 기존 문자열(사람 선택)
  - 기각: 조회마다 `detailCommon2` 재조회(하루 1,000회 한도 소모, 공사 장애가 게시글 조회로 번짐)
- 쓰기 검증 = 형식만(사람 선택)
  - 기각: 저장 시 공사 API 확인(쓰기 경로가 외부 장애와 한도에 묶임)
  - 감수: 없는 `contentid` 나 지역 불일치 값이 저장될 수 있음
- 동시 지정 차단 = 리졸버 400 + MySQL CHECK 이중화(H2는 CHECK 없음, 리졸버가 앞에서 막음)

## 남은 후속 작업

- **배포 주의**: PR에 마이그레이션 `V48`~`V50` 포함 → main 머지 = 운영 Flyway 적용(되돌릴 수 없음, 머지 시 hook 확인)
  - 실패 시 복구: 각 파일이 `ALTER` 한 문장이고 InnoDB 원자적 DDL이라 실패한 파일은 변경을 남기지 않음. 원인 수정 후 `flyway repair`(실패 이력 행 제거) → 재배포하면 실패한 파일부터 다시 적용
- **감수한 위험**(C-r1-01, V-r1-01): 모집글과 여행계획서 생성의 멱등 키 해시가 바뀜. 배포 직전 키로 배포 직후 같은 요청을 재시도하면 원래 자원 대신 409(중복 생성 없음)
- **사람 작업**: 공사에 "선택 장소 이름 저장이 캐싱 금지에 해당하는지" 문의 메일 발송(Gmail 초안만 있음). 허용되면 이름 컬럼 추가를 별도 결정
- **프론트 합의**: 모집글과 계획서 작성 시 관광지 선택 → `tourContentId` + `regionCode` 전송, 화면 이름은 `location`/`destination` 에 넣어 보내기. 노션 244 TODO "프론트와 검색 결과 형식 합의"와 함께 진행
- **노션 244 TODO 갱신**: "장소 참조를 출처 + 외부 id 구조로 변경" 체크(보드 쓰기라 사람 확인 후)
- **테스트 힙 한도**: 테스트 JVM 기본 힙 512MB에 컨텍스트가 거의 찬 상태. 병렬 태스크(246, 247)가 새 컨텍스트를 만들면 같은 OOM 가능 → 기존 컨텍스트 설정 재사용 또는 별도 태스크로 힙 설정 검토
- 248(`location` 길이 검증, 완료 파티 409)은 이 PR 머지 후 진행, 마이그레이션은 `V51` 부터
