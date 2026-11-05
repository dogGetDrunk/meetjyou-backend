# Spec 비평 결정

| 라운드 (최대 2) | 항목 | 수용/기각 | 근거 | 메인 설계자 |
|---|---|---|---|---|
| r1 | C-r1-01 | 수용 | 교체 API는 기존 마커가 있는 경로라 "저장 0건"은 잘못된 기준. 생성은 건수 변화 0, 교체는 기존 행 보존으로 분리 | Claude Opus 5.5 |
| r1 | C-r1-02 | 수용(부분) | 비경합 DIVE의 실제 HTTP 500·404 아님 통합 AT 추가(AT-003-3, `@SpyBean` 주입). 플랜 경로 동시 요청 통합 테스트 신설은 기각 — 컨트롤러 복구 코드가 게시글과 동일 구조이고 기존 `PostIdempotencyIntegrationTest` + 플랜 단위 테스트가 분기를 덮음, 같은 키·다른 바디 409는 복구 경로 전에 `createPlan`의 `resolveExisting`에서 결정돼 이번 변경과 무관 | Claude Opus 5.5 |
| r1 | C-r1-03 | 수용 | `@MockBean AppleJwksCache`로 실제 디코더·verifier·핸들러를 통과하는 로그인 API 통합 AT 추가(AT-004-2), JWKS 장애 503 포함. 디코더 단위에도 공급 실패 전파 케이스 추가 | Claude Opus 5.5 |
| r1 | C-r1-04 | 수용(부분) | 결함 재현 AT와 동작 유지 AT를 spec에 구분. 같은 SHA 게이트 출력은 엔진 리뷰·머지 게이트가 이미 요구하므로 별도 절차 신설 안 함 | Claude Opus 5.5 |
| plan | (plan 워커) | 수동 보완 | plan 워커가 저장소 경로를 받지 못해 `failed`(티켓 3·케이스 9는 작성됨). 재실행해도 입력이 같아 같은 결과 → 메인 설계자가 touches·참조 파일·보호 테스트 경로(`src/test/java/...`)를 채우고 blocked 해제 | Claude Opus 5.5 |
| tests | (보호 테스트 결함) | 수정 | 메인 설계자 base/GREEN 실행으로 발견·수정: ① detekt 위반 18건(미사용 `@MockBean` 필드 → 클래스 단위, 긴 줄·30줄 초과 함수 분할, 6파라미터 헬퍼 → `copy()`) ② `IdempotencyKeyLengthServiceTest` mock 호출 기록이 leaf 간 누적(Kotest 인스턴스 재사용) → `beforeEach { clearAllMocks() }` ③ `NonConflictIntegrityIntegrationTest` 정리 단계의 `@Modifying` 삭제가 트랜잭션 없이 실행 → `TransactionTemplate` ④ `AppleInvalidClaimsLoginIntegrationTest`가 토큰에 nonce 해시 미포함 → nonce 불일치 401이 `sub` 결함을 가림, 발급 nonce의 SHA-256 주입 (brief r1 초안에서 지적) | Claude Opus 5.5 |
| r1 | B-r1-01 | 수용 | 생성 API는 `@ResponseStatus` 없이 현재 200 — spec의 201은 메인 설계자 오기. 상태 코드 변경은 범위 밖이라 spec·케이스를 기존 200으로 정정, 보호 테스트 2xx 검사는 그대로 | Claude Opus 5.5 |
| r1 | B-r1-02 | 수용(부분) | AT-003-1만 base 컴파일 실패를 RED로 인정 — 테스트가 확정 인터페이스(nullable 반환)를 직접 stub하는 게 계약 자체. 재작성(리플렉션 등)은 테스트를 흐리게 해 기각. 나머지 AT의 base RED는 메인 설계자가 AT-003-1을 뺀 실행으로 확인(spec 회귀 증명 절) | Claude Opus 5.5 |
| r1 | B-r1-03 | 수용 | 점검 도중 메인 설계자의 보호 테스트 수정으로 해시 불일치. 테스트 확정 후 brief r2로 재렌더링·재점검 | Claude Opus 5.5 |
| r2 | B-r2-01 | 수용 | B-r1-01(기존 200 유지) 결정을 T-01 티켓 발췌에 반영 | Claude Opus 5.5 |
| r2 | B-r2-02 | 수용 | B-r1-02(AT-003-1만 컴파일 RED 인정) 결정을 T-02 검증 절에 반영, 테스트 재작성 없음 | Claude Opus 5.5 |
