# Spec critique, round 1

### C-r1-01 AT-002 leaves provider coverage for social linking implicit

- **Grounds.** `intent.md:54` requires zero active connections during verification for Kakao, Google, and Apple on both login and linking. `spec.md:20–23` requires a case for each provider on login. `spec.md:32–35` sets no equivalent coverage for linking. `spec.md:119–121` installs all three mocks and prescribes an `accessToken`-only request. The login flow still runs `socialTokenOf` before calling the mock (`spec.md:17`).
- **Impact.** AT-002 can pass with one provider. The shared fixture also leaves it unclear whether each request reaches the selected verifier through the existing API contract.
- **Suggested fix.** Require Kakao, Google, and Apple cases independently for both AT-001 and AT-002. Specify a valid request for each provider and assert that the intended verifier was invoked before evaluating its transaction and connection observations.

### C-r1-02 AT-003 stops at the exception and can pass without demonstrating a read timeout

- **Grounds.** `intent.md:56` requires a test for HTTP 503 `SOCIAL_PROVIDER_UNAVAILABLE` when Kakao exceeds the read timeout. `spec.md:43–48` states the HTTP mapping but tests only the verifier exception, its error code, and an elapsed-time ceiling. It does not require evidence that the server received the request and withheld its response.
- **Impact.** An HTTP status or response-body mapping regression can pass the test. A fast connection failure that follows the same `ResourceAccessException` mapping can also meet the exception and elapsed-time assertions without exercising the read timeout.
- **Suggested fix.** Require the local server to acknowledge receipt and hold the response, then verify the public HTTP status and error code with the configured Kakao client. Tie test completion to the held response and use a bounded wait for cleanup so the elapsed-time ceiling alone cannot establish the cause.

### C-r1-03 The party download plan omits inherited images with no linked post

- **Grounds.** `spec.md:69–76` defines `INHERITED` as a post PAR and covers a missing party and `NONE`. It leaves the empty result of `findByParty_Uuid` and missing entries from `findAllByParty_UuidIn` unspecified. The supplied `.claude/rules/post-plan.md` says, “deleting a post does not delete the party.” `intent.md:23,57` requires response and status parity.
- **Impact.** A party can outlive its post. The move permits the original and batch resolvers to handle that missing link differently from the current behavior, including a changed status or a changed slot in a batch response.
- **Suggested fix.** Record the current original and thumbnail outcomes for an `INHERITED` party with no linked post, and add AT-005 cases that assert those outcomes and whether OCI is invoked.

### C-r1-04 AT-006 does not require an observation for every expected OCI call

- **Grounds.** `spec.md:83–89` records connection and transaction state in the OCI mock callbacks and lists expected paths, including a mixed thumbnail batch. `spec.md:121` requires reset between cases but does not require the test to find the expected callback observations or preserve more than one observation per request. `intent.md:33–34,55` requires assertions during OCI calls for every DB-first image path.
- **Impact.** A case with no OCI invocation can pass an assertion over an empty observation list. A batch that retains only the last observation can miss a connection held during an earlier call.
- **Suggested fix.** Require AT-006 to verify the expected OCI operation and observation count for each fixture, including the selected thumbnail objects, then assert zero connections and no active transaction for every observation.

### C-r1-05 The final milestone omits the required MySQL Docker test gate

- **Grounds.** `intent.md:59` requires successful `./gradlew build` and `./gradlew dockerTest`. The supplied project gate is `./gradlew test build`, and `spec.md:125–129` ends at `agent-pipeline gate`. Although `spec.md:30,111` mentions individual Docker tests, neither line requires a full `dockerTest` run.
- **Impact.** The listed milestones can finish without executing the full MySQL suite required by the approved intent, including regressions around social-link conflicts and refresh-token rotation.
- **Suggested fix.** Add a separate successful `./gradlew dockerTest` run to the final milestone alongside the configured pipeline gate and require its execution result in the completion record.

### C-r1-06 The approved Apple revocation follow-up is absent from the plan

- **Grounds.** `intent.md:43,47–50` excludes account withdrawal and Apple unlink from implementation and explicitly requires a follow-up entry in `result.md`. `spec.md:91–97,125–129` lists the documentation and completion work without that entry.
- **Impact.** The task can close with the two known OSIV connection holds missing from the required follow-up record.
- **Suggested fix.** Add a completion requirement for `result.md` to record both excluded routes and their remaining connection hold during Apple revocation.

### C-r1-07 The proposed OSIV guidance applies exclusion beyond DB-first routes

- **Grounds.** `spec.md:5–9,58` limits the need for OSIV exclusion to requests that access the DB before external I/O. `spec.md:96` requires exclusion whenever a new external-call route is added. `intent.md:39,58` preserves OSIV outside the intended exclusions.
- **Impact.** The proposed KDoc directs future implementers to exclude routes that perform external I/O before their first DB access, which can break lazy loading later in those requests without addressing a connection hold.
- **Suggested fix.** Limit the exclusion rule in REQ-007 to routes with DB work before external I/O. Retain the connection assertion for new DB-first exclusions and align the guidance with REQ-004.
