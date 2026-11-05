# Intent

> 메인 설계 에이전트가 사람과 대화로 작성. 작성을 마치면 첫 줄의 template 표시 삭제.
> 승인 기록은 `approval.json`(`agent-pipeline approve-intent`), 승인 후 이 파일 수정 금지.

## 해결할 문제

- **마커 입력 검증 없음 (#226)**
  - `CreatePlanRequest.markers`·`ReplaceMarkersRequest.markers`에 `@Valid`·`@Size` 없음 → 중첩 DTO 제약이 있어도 검사 안 됨
  - `CreateMarkerRequest` 필드 제약 없음
    - `place`(DB `VARCHAR(100) NOT NULL`)·`memo`(DB `VARCHAR(500)`) 길이 초과 → DB 오류 500
    - `lat`(DB `DECIMAL(10,8)`)·`lng`(DB `DECIMAL(11,8)`) 범위 초과 → DB 오류 500, 범위 안이어도 ±90/±180 밖은 무의미한 좌표
    - `dayNum`·`idx` 음수 허용
  - 마커 수 제한 없음 → 수만 개 요청 시 대량 insert
- **멱등 키 충돌 처리의 404 오분류 (#226)**
  - `PostController`·`PlanController`가 생성 중 모든 `DataIntegrityViolationException`을 키 경합으로 간주 → `resolveAfterConflict`
  - 기존 키 행이 없으면(= 키 경합이 아니었음) `PostNotFoundException`/`PlanNotFoundException` → 404
  - 오분류 사례: 길이 초과 입력, `Idempotency-Key` 자체가 컬럼 길이(255) 초과, 생성 중 참조 행 동시 삭제(FK 위반)
- **Apple ID token null 클레임 NPE (#230)**
  - `AppleJwtDecoder.decode`: `claims.expirationTime`(:43)·`issueTime`(:62) null이면 NPE → 401 대신 500 + 에러 알림
  - `subject` null도 미방어 (`AppleVerifier` → `SocialPrincipal`)
  - 만료 검사가 서명 검증보다 먼저 실행 → 서명 안 된 토큰의 클레임으로 분기·로그

## 의도

- 클라이언트의 잘못된 입력은 400/401로, 서버 오류(500·알림)나 엉뚱한 404로 나가지 않게 함
- 검증 위치는 기존 관례 유지: 요청 형식은 Bean Validation(`@Valid` → 400 `INVALID_INPUT_VALUE`), JWT 문제는 `InvalidJwtException`(401)

## 포함 범위

- 마커 요청 Bean Validation
  - 리스트: `@Valid` + `@Size(max = 200)` (D1) — `CreatePlanRequest.markers`, `ReplaceMarkersRequest.markers`
  - `CreateMarkerRequest`: `place` `@NotBlank` + `@Size(max = 100)`, `memo` `@Size(max = 500)`, `lat` −90~90, `lng` −180~180, `dayNum >= 1`·`idx >= 0` (D2)
- `Idempotency-Key` 헤더 길이 검증(최대 255자, D3) → 초과 시 400 (Post·Plan 생성 둘 다)
- `resolveAfterConflict`: 기존 키 행을 못 찾으면 원래 `DataIntegrityViolationException`을 그대로 재던짐 → 기존 `GlobalExceptionHandler` 경로(유니크 매핑 409 또는 500)로 처리
- `AppleJwtDecoder`: 서명 검증을 클레임 검사 앞으로 이동, `exp`·`iat`·`sub` null → `InvalidJwtException`(401)
- 각 항목 단위·통합 테스트

## 제외 범위

- 프론트 입력 제한 반영 (노션 TODO의 프론트 항목, 백엔드 값 확정 후 프론트 담당)
- `CreatePlanRequest.centerLat`/`centerLng`·`UpdatePlanRequest` 등 마커 외 좌표·필드 검증 (이슈 범위 밖, 문제 관측 시 별도)
- 멱등 키 행 만료·정리 (기존 알려진 한계)
- Google·Kakao 토큰 검증 경로의 null 클레임 점검
- 이미 저장된 데이터 정리·스키마 변경 (DB 컬럼 길이는 그대로, 검증 값을 DB에 맞춤)

## 결정 (2026-11-05 사람 확정)

- **D1. 마커 최대 개수 N = 200**
  - 근거: 하루 10~20곳 × 10일 내외 여행이면 충분, 수만 개 대량 insert는 차단
  - 기각: 100(긴 여행에서 부족 가능), 300(차단 효과 대비 이득 없음)
  - 프론트 협의는 아직 안 됨 → 200으로 먼저 구현, 노션 태스크 안건에 "프론트 논의 필요" 기록
- **D2. `dayNum >= 1`, `idx >= 0`**
  - 근거: 기존 테스트·픽스처가 `dayNum = 1, idx = 0`부터 사용
- **D3. `Idempotency-Key` 최대 255자** (DB 컬럼 길이와 동일)
  - 기각: 36자(UUID 고정) — 프론트 발급 방식이 정해지기 전 형식 제약을 만들게 됨

## 성공 기준

- 마커 길이·범위·개수 초과, 음수 `dayNum`/`idx` → 400 `INVALID_INPUT_VALUE` (생성·일괄 교체 둘 다), DB 오류 500 없음
- 255자 초과 `Idempotency-Key` → 400, 404 아님
- 키 경합이 아닌 `DataIntegrityViolationException`(예: FK 위반) → 404로 바뀌지 않고 원래 예외 경로로 응답
- 같은 키·같은 바디 동시 요청은 기존처럼 이긴 쪽 결과 반환, 같은 키·다른 바디는 409 유지 (기존 멱등 테스트 통과)
- Apple 토큰의 `exp`/`iat`/`sub` 누락 → 401 `InvalidJwtException`, 서명 불일치 토큰은 클레임 검사 전에 401
- 회귀 확인: 수정 되돌리면 새 테스트가 red
- `./gradlew build`(detekt·Konsist 포함), `./gradlew test` 통과

## 작업 크기

M — 검증 추가·예외 재던짐·JWT 검사 순서 변경이 plan·post·auth 3개 모듈에 걸쳐 있음 (각각은 국소 변경)

## 출처

- 노션 태스크: https://www.notion.so/40556d9a97d981539404d8ffdb5f0b2e (잘못된 입력에 맞는 오류 안내 정리, 마감 2026-11-08)
- GitHub 이슈: dogGetDrunk/meetjyou-backend#226, dogGetDrunk/meetjyou-backend#230
