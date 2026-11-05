### B-r1-01 생성 성공 201 명세와 보호 테스트의 2xx 허용 충돌

- 관련: REQ-001·REQ-002, AT-001-2·AT-002-2, T-01·T-02
- 근거: `spec.md:24`의 “생성 201 / 교체 200”, `spec.md:37-38`의 “정상 생성(201)” 및 “255자 201”; 각 티켓·brief에서도 같은 수용 기준 유지
- 반대 근거: 보호 파일 `tests/code/src/test/java/com/dogGetDrunk/meetjyou/common/idempotency/IdempotencyKeyLengthIntegrationTest.kt:46-47`에 현재 생성 API는 200이므로 “any 2xx”로 검사한다는 주석, `:211-212`에서 실제로 `is2xxSuccessful`만 검사; `tests/code/src/test/java/com/dogGetDrunk/meetjyou/plan/MarkerInputValidationIntegrationTest.kt:259-260,276,294`에서도 생성 상태를 2xx 전체로 허용
- 영향: 보호 테스트 통과로 명시된 201 수용 기준 충족 입증 불가; 특히 T-01은 DTO만 수정하는 범위라 상태 변경을 위한 PlanController 수정도 touches 밖
- 요청: 메인 설계자가 기존 200 유지 또는 201 변경을 명시적으로 결정하고 spec·티켓·보호 테스트·재렌더링 brief를 일치시킬 것; 테스트 작성자의 주석만으로 요구사항 완화 불가
- 확인 범위: 제공 문서·보호 테스트·읽기 허용 컨트롤러 정적 대조, HTTP 실행 미수행

### B-r1-02 nullable 반환형 이후에만 컴파일되는 보호 테스트와 base assertion RED 계약 충돌

- 관련: REQ-003, AT-003-1, T-02
- 근거: `logs/briefs/r1/T-20261105-invalid-input-errors-02.md:148`은 “원본 재던짐 … base assertion 실패 확인”, `:149,155-156`은 수정 되돌림 RED 및 보호 테스트 수정 금지 요구
- 타입 근거: 읽기 허용 파일 `src/main/java/com/dogGetDrunk/meetjyou/post/PostService.kt:111`의 기존 반환형은 `CreatePostResponse`, `src/main/java/com/dogGetDrunk/meetjyou/plan/PlanService.kt:93`은 `CreatePlanResponse`로 둘 다 non-null; 보호 파일 `tests/code/src/test/java/com/dogGetDrunk/meetjyou/common/idempotency/NonConflictIntegrityControllerTest.kt:37,59`는 이 메서드의 MockK stub에 `returns null` 지정
- 영향: 반환형을 수정 전으로 되돌린 상태에서는 이 Kotlin stub의 null과 non-null 반환형이 충돌하여 테스트 assertion 이전 컴파일 실패; 해당 보호 파일이 통합된 T-02 source set에서는 다른 AT를 골라 실행해도 compileTestKotlin 단계의 장애 요인
- 요청: 테스트 작성자가 수정 전·후 반환형 모두에서 컴파일되는 오류 주입 방식으로 AT-003-1을 작성하고 assertion RED를 확인하거나, 메인 설계자가 이 AT에 한정한 컴파일 RED 허용 여부와 다른 AT의 독립 RED 실행 절차를 명시할 것; 구현 워커에게 보호 테스트 수정이나 비공개 base 보정을 맡기지 말 것
- 확인 범위: 기존 시그니처·MockK stub의 정적 타입 대조, 컴파일 명령 미실행; 실제 컴파일 로그를 확보한 것으로 간주 금지

### B-r1-03 렌더링된 보호 해시와 현재 복사 원본 불일치

- 관련: REQ-002·REQ-003·REQ-004, T-02·T-03
- 근거: 각 brief의 Acceptance test files 절은 sha256 byte equality를 gate 전 검사하며 원본을 그대로 cp·commit하도록 지시; 해당 cp 원본을 hashlib.sha256으로 재검사한 실제 불일치
- T-02 `tests/code/src/test/java/com/dogGetDrunk/meetjyou/common/idempotency/IdempotencyKeyLengthServiceTest.kt`: brief 기대값 `020bbd0093490903fa5f424b3f451351d022b4dba9f328326c3a69064477eb10`, 현재 원본 `294d5743bc7feae9d4cd1acf64c0ba8eb801fbdc9298fbdce8e8a7cabc2da912`
- T-02 `tests/code/src/test/java/com/dogGetDrunk/meetjyou/common/idempotency/NonConflictIntegrityIntegrationTest.kt`: brief 기대값 `df8d12ff07fc587c84d244dc3485b001a20fba0463382832402a62dda057da67`, 현재 원본 `1fa8a050390ddcb898de02e80c371f0516cd3c027626e985238501b02cd5e301`
- T-03 `tests/code/src/test/java/com/dogGetDrunk/meetjyou/auth/social/apple/AppleInvalidClaimsLoginIntegrationTest.kt`: brief 기대값 `d9b8ef8253a0a5e4c61e5d0e501913639e16acc5baeed9c941e8d2f6cb7b5162`, 현재 원본 `f06d0fa45a3c085f061f985f349257b7c4ef3ff53831632cbc68f65cb79bf9f5`
- 영향: 구현 워커가 명시된 cp 명령을 그대로 실행해도 렌더링된 brief의 보호 해시와 다른 파일 커밋; 제품 코드 정합성과 무관하게 hash gate 실패
- 요청: 테스트 작성 완료본을 확정하고 엔진의 보호 해시·brief를 함께 재생성한 뒤 재검토; 구현 워커의 테스트 수정·예전 바이트 추측 복원 금지
- 확인 범위: 2026-11-05T13:37:06.640075+00:00 스냅샷에서 9개 중 3개 불일치; 본 검토의 테스트 수정 없음, Gradle 실행 없음
- 변경 재확인: 최초 Apple 로그인 테스트의 nonce 누락은 재조회 시 원본 `AppleInvalidClaimsLoginIntegrationTest.kt:77-104,121`의 nonce 보존·SHA-256 주입으로 수정된 상태여서 별도 미해결 결함에서 제외; 새 바이트와 brief 해시 불일치는 위 항목에 포함
