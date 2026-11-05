### B-r2-01 생성 성공 200 결정이 T-01 티켓·r2 지시문에 미반영

- 관련: REQ-001, AT-001-2, T-01; B-r1-01 수용 결정의 전달 누락
- 근거: `tickets/T-20261105-invalid-input-errors-01.md:71` 및 `logs/briefs/r2/T-20261105-invalid-input-errors-01.md:111`에 여전히 “경계값은 생성 201·교체 200” 명시
- 반대 근거: `spec.md:24`는 “생성·교체 모두 기존 200 유지”, `decisions.md:11`은 상태 코드 변경을 범위 밖으로 확정; r2 역할 지시문 `:3`은 “Implement from the ticket card alone” 요구
- 영향: 구현자가 티켓의 201을 따르면 확정된 기존 200 유지 결정과 충돌; T-01 touches는 DTO 3개와 보호 테스트뿐이라 상태 코드 변경도 허용 범위 밖
- 요청: 이미 확정한 200 유지 결정을 T-01 티켓 발췌에 반영하고 r2 구현 지시문 재렌더링; 보호 테스트의 2xx 허용은 B-r1-01 결정에 따른 유지 대상
- 확인 범위: 제공된 spec·결정·티켓·r2 지시문·보호 테스트의 정적 대조; HTTP 실행 없음

### B-r2-02 AT-003-1 컴파일 RED 예외가 T-02의 assertion RED 지시에 미반영

- 관련: REQ-003, AT-003-1, T-02; B-r1-02 부분 수용 결정의 전달 누락
- 근거: `tickets/T-20261105-invalid-input-errors-02.md:108` 및 `logs/briefs/r2/T-20261105-invalid-input-errors-02.md:148`은 원본 재던짐 케이스에도 “base assertion 실패 확인” 요구; r2 지시문 `:149`는 보호 테스트 불변 및 수정 되돌림 RED 요구
- 반대 근거: `spec.md:80` 및 `decisions.md:12`는 AT-003-1만 컴파일 실패를 RED로 인정하고, 나머지 AT는 해당 파일을 제외한 base 실행으로 증명한 결정 명시
- 타입 근거: 보호 원본 `tests/code/src/test/java/com/dogGetDrunk/meetjyou/common/idempotency/NonConflictIntegrityControllerTest.kt:37,59`의 `returns null`; 기존 `src/main/java/com/dogGetDrunk/meetjyou/post/PostService.kt:111` 및 `src/main/java/com/dogGetDrunk/meetjyou/plan/PlanService.kt:93`의 non-null 반환형
- 영향: AT-003-1의 base assertion 실패 요구와 승인된 컴파일 RED 예외가 동시에 남아 검증 계약 충돌; 해당 보호 파일을 포함한 source set에서는 다른 테스트만 선택해도 컴파일 단계 차단
- 요청: 테스트 재작성 없이, T-02 검증 절에 AT-003-1의 컴파일 RED 허용과 나머지 AT의 독립 base 실행 기준을 명시하고 구현 지시문 재렌더링; 기존 B-r1-02 결정을 그대로 전달하는 보정
- 확인 범위: 정적 타입·지시문 대조만 수행, 컴파일·Gradle 실행 없음; r2 보호 원본 9개는 Python `hashlib.sha256` 대조 결과 모두 렌더링 해시와 일치하여 B-r1-03 재발 없음
