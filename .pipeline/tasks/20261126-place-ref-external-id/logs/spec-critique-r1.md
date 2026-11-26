### C-r1-01 Existing post creation retries can lose idempotency

- Grounds — `spec.md:49–61` adds a nullable field to `CreatePostRequest` and promises that existing requests keep their status and persistence behavior. The supplied `.claude/rules/post-plan.md`, “Idempotent create”, states that the request hash is computed from the serialized DTO and changes when DTO fields change. AT-003 does not require a replay against a key recorded with the old DTO.
- Impact — If the new default-null field changes the serialized DTO, an existing client can replay the same successful creation request after the release and receive a 409 because its stored hash was computed with the old DTO.
- Suggested fix — Specify how post creation compares legacy and new request hashes. Add an acceptance case that replays an unchanged legacy body against a key recorded with the pre-change serializer and checks the original resource is returned without a duplicate. Also require the same key with a different `tourContentId` to remain a 409.

### C-r1-02 Several promised response paths have no explicit assertions

- Grounds — `intent.md:36–37` requires the field in applicable read, create, and update responses. `spec.md:66–74` lists nine DTOs and delegates response assertions to AT-003. The AT-003 scenarios at `spec.md:56–62` explicitly inspect post and party reads. They lack named assertions for `CreatePostResponse`, `UpdatePostResponse`, `GetMyPartyResponse`, `UpdatePartyResponse`, and each plan lifecycle body.
- Impact — The stored value and detail reads can be correct while a response factory drops the field, leaving clients without the reference on those paths.
- Suggested fix — Map every response in REQ-005 to a concrete assertion in AT-003. Cover populated `tourContentId` values and use a representative SBIZ or region-only response to verify the declared null representation.

### C-r1-03 The API contract leaves JSON token coercion untested

- Grounds — `spec.md:37–40` validates a Kotlin `String?` after deserialization, while `spec.md:87–89` declares a quoted digit string and a 400 `INVALID_INPUT_VALUE` error. AT-002 supplies Kotlin strings and AT-003 at `spec.md:60` does not name any unquoted or non-string JSON inputs.
- Impact — A mapper that coerces a JSON number to a string can make an unquoted value pass the regex, with actual wire behavior depending on deserialization settings.
- Suggested fix — Specify which JSON token types the endpoint accepts and their error contract. Use raw request bodies in AT-003 to assert a quoted digit string, an unquoted number, and explicit `null` according to that decision.

### C-r1-04 Acceptance omits replacement between the two place sources

- Grounds — `intent.md:19–20,33–35` keeps the SBIZ FK, makes the references mutually exclusive, and requires party propagation. `spec.md:53–62` tests changing or omitting a tour ID and running the existing SBIZ cases. It does not require a transition from a persisted SBIZ reference to a tour reference or the reverse.
- Impact — An update or party sync that leaves the previous source populated can pass those scenarios and then fail the MySQL CHECK when a user changes sources.
- Suggested fix — Add both source transitions to AT-003 for posts and plans. Read each affected record back after the write, including the synced party, and assert the new reference and a null value for the previous source.

### C-r1-05 The plan does not schedule the required MySQL suite

- Grounds — `intent.md:40,58` requires MySQL `dockerTest` evidence. `spec.md:23–24,114–115` assigns the constraint and migration proof to AT-001, while the last milestone at `spec.md:123` only names `agent-pipeline gate`. The supplied gate is `./gradlew test build`, and the supplied `CLAUDE.md` lists `./gradlew dockerTest` separately.
- Impact — The documented completion path can report a passing gate without evidence that MySQL applied V48 or rejected conflicting references.
- Suggested fix — Make running AT-001 on MySQL an explicit milestone and require its executed result before completion. State the `dockerTest` command or the gate evidence that proves the suite ran.

### C-r1-06 V48 has no recovery procedure for partial application

- Grounds — `spec.md:14–16,94–99` puts three table alterations in a single Flyway migration. The plan itself identifies a possible CHECK/FK failure at `spec.md:114`. AT-001 covers clean application and the resulting schema, and the milestones at `spec.md:120–123` do not cover a failed migration on a later table.
- Impact — MySQL can commit the earlier `ALTER TABLE` before a later one fails. Retrying the migration after repairing Flyway history can then fail on the already-added column or constraint.
- Suggested fix — Document how to identify applied columns and constraints, which supported recovery procedure reconciles that state before a retry, and how to exercise the procedure on disposable MySQL. Include a failure after at least one table has been altered and a check that existing rows and FKs survive recovery.

### C-r1-07 The no-additional-call requirement is only checked at the resolver

- Grounds — `intent.md:14–15,22–26` requires using the saved reference and existing display fields without additional Tour API calls on read or write. `spec.md:43–46` covers the resolver, while AT-003 and the response criteria at `spec.md:56–74` specify only status, data, and snapshot assertions.
- Impact — An implementation that still resolves or enriches a reference through the external client on a service or response path can pass the stated checks while consuming quota or adding an availability dependency.
- Suggested fix — Add a no-call acceptance criterion for representative post and plan writes and post, party, and plan reads with a tour reference. Require evidence that fails if those paths invoke the Tour API client.

### C-r1-08 The module documentation drops the `destination` display fallback

- Grounds — `intent.md:22` specifies the existing `location`/`destination` strings for display. The supplied `.claude/rules/place.md`, “Destinations on post / party / plan”, also names both. `spec.md:80` directs the module-rule update to describe the display name as `location` only.
- Impact — The revised rule gives incomplete display guidance to callers that receive `destination` and can lead future changes to assume a common `location` field.
- Suggested fix — Match the approved wording in REQ-006 and require the document review to verify that it describes the existing display field for each resource.
