### V-r1-01 Plan creation retries can return 409 across deployment

- Kind — An acceptance criterion not met.
- Grounds — `CreatePlanRequest.kt:32` adds a nullable field to the DTO hashed by `PlanService.kt:50–52`. `IdempotencyKeyService.kt:28–37` hashes the serialized DTO and rejects a mismatch. `spec.md:69` requires “`tourContentId` 없는 기존 요청은 응답 코드와 저장 결과가 이전과 같음.” The approved exception at `spec.md:130` names `CreatePostRequest`.
- Decision broken — REQ-004 and `intent.md:57`.
- Impact — A client that retries an unchanged plan creation request with a key recorded before deployment can receive `409 IDEMPOTENCY_KEY_REUSED` instead of the existing plan.

### V-r1-02 ADR still directs per-response Tour API lookups

- Kind — A silent reversal.
- Grounds — `docs/adr/0004-tour-api-realtime-integration.md:58` says “해당하면 응답 시마다 `contentid` 로 재조회해야 함” if the provider considers name storage caching. The approved intent explicitly rejects “조회마다 `detailCommon2` 재조회,” and `spec.md:60` says “쓰기와 조회 어디서도 관광공사 API를 호출하지 않음.”
- Decision broken — `intent.md:22–24` and REQ-004.
- Impact — The accepted ADR gives conflicting instructions for the display fallback and can lead a later implementation to consume Tour API quota on resource reads.

### V-r1-03 Source-switch tests omit two persisted-state checks

- Kind — An accepted critique item not implemented.
- Grounds — `TourContentRefApiIntegrationTest.kt:173–178` checks the post update response for SBIZ → Tour, then uses the post GET only to locate the party. Lines 209–216 check the plan update response for Tour → SBIZ and perform a plan GET only after switching back. `decisions.md:11` requires “소상공인 → 관광공사, 관광공사 → 소상공인 전환을 모집글(파티 동기화 포함)과 계획서에서 확인.”
- Decision broken — C-r1-04 and AT-003.
- Impact — These cases can pass if the update response contains the new reference but the post or plan retains its old reference after the write.

### V-r1-04 JSON token tests leave request serialization to the HTTP client

- Kind — An accepted critique item not implemented.
- Grounds — `TourContentRefApiIntegrationTest.kt:81–86,237–245` passes maps to `TestRestTemplate`. The test neither supplies literal JSON bodies nor checks the emitted tokens. `decisions.md:10` requires “raw body로 AT-003에서 확인.”
- Decision broken — C-r1-03 and AT-003.
- Impact — A client serializer that quotes the integer or omits the null key could leave both tests passing without exercising the required wire inputs.

### V-r1-05 Response assertions accept a numeric JSON ID

- Kind — An acceptance criterion not met.
- Grounds — `TourContentRefApiIntegrationTest.kt:121–126` uses `JsonNode.asText()` for every populated response assertion. A JSON number `126508` passes the same assertion as the string `"126508"`. The fixed response interface in `spec.md:101` is “`"tourContentId": "126508" | null`.”
- Decision broken — REQ-005 and AT-003.
- Impact — The nine response checks would miss a regression that changes the ID from a JSON string to a number.

### V-r1-06 The MySQL test result is missing from the task record

- Kind — An accepted critique item not implemented.
- Grounds — `build/reports/tests/dockerTest/index.html:23–35` records 181 tests with zero failures and zero ignored, and the `TourContentRefSchemaDockerTest` XML report records all five AT-001 cases passing. `result.md:15–16` has an empty gate evidence section. `spec.md:137` requires “결과(통과 수, 실패 수)를 result.md에 기록.”
- Decision broken — C-r1-05.
- Impact — The task record does not preserve the MySQL acceptance evidence available in the local build reports.

### V-r1-07 The migration recovery procedure is missing from the task record

- Kind — An accepted critique item not implemented.
- Grounds — `.claude/rules/place.md:27` documents the recovery procedure, while `result.md:5–19` contains only template sections. `decisions.md:13` requires “이 절차를 result.md에 기록,” referring to `flyway repair` and redeployment from the failed migration.
- Decision broken — C-r1-06 and REQ-001.
- Impact — A reader relying on the task result for rollout recovery cannot find the approved procedure there.