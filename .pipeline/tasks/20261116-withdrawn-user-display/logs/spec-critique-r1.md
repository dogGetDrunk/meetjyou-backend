# Spec critique — round 1

### C-r1-01 The HTTP test plan does not establish the required NORMAL-state coverage

- Grounds: `intent.md:32` requires integration tests for each of the five responses with `NORMAL`, `DELETED`, and `PURGED`; `intent.md:46` requires unchanged values for NORMAL users. However, `spec.md:97-100` defines response subjects only for DELETED and PURGED. The NORMAL host is the requesting principal, and the plan does not require asserting its display fields in returned data. AT-002 refers to an existing integration test (`spec.md:23`), but the documents do not establish the complete NORMAL coverage.
- Impact: The planned tests can pass while an adapter changes active users' nicknames or image flags, or returns `isWithdrawn=true`, contrary to the approved regression requirement.
- Suggested fix: Explicitly map all five response DTOs to HTTP assertions for all three statuses. For NORMAL subjects, assert the original nickname, actual image flag wherever that field exists, and `isWithdrawn=false`; identify any existing cases that supply this coverage rather than assuming it.

### C-r1-02 WebSocket broadcast behavior has no acceptance criterion or verification

- Grounds: `intent.md:11` explicitly includes chat history and WebSocket broadcast in the chat sender response scope. REQ-003 names `ChatMessageResponse.of`, but its acceptance criterion and AT-003 cover only history HTTP retrieval and a DTO unit test (`spec.md:27-29`). The integration design likewise specifies only HTTP reads (`spec.md:96-100`). No criterion or check establishes that the broadcast path uses the updated response conversion and serializes `isWithdrawn`.
- Impact: HTTP retrieval and direct factory tests could pass while the live broadcast payload omits the new field or uses a different sender-display mapping. One of the explicitly included delivery paths would remain unverified.
- Suggested fix: Add an explicit broadcast acceptance criterion and a check through the actual broadcast payload construction/serialization path, including `isWithdrawn` and the applicable display values. State how the broadcast and history paths are verified to share the conversion. Choose the feasible status scenarios without assuming an inactive user can send a new message, since the supplied chat rules reject such sends.

### C-r1-03 The final verification step omits the supplied project build gate

- Grounds: The supplied Project context defines the gate as `./gradlew test build`; supplied `CLAUDE.md` identifies `build` as the entry point for detekt, Konsist, and the skipped-test guard. The final milestone lists only `agent-pipeline gate` (`spec.md:108`), and the spec does not state that this command executes the supplied Gradle gate or require its successful result.
- Impact: The plan lacks a verifiable completion condition for the mandatory project checks; passing the task-document gate alone does not establish that the changed production code and tests satisfy those checks.
- Suggested fix: Require successful `./gradlew test build` with recorded output in the final verification milestone, in addition to the intent's `agent-pipeline gate`. Do not treat this document-only critique as execution evidence for either command.
