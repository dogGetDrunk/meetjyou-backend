# Spec critique — round 1

### C-r1-01 The approved pre-implementation production check is missing

- Grounds — `intent.md:30` and `intent.md:59` require a read-only check of production application logs and MySQL `sql_mode` before implementation, with the conclusion recorded in `result.md`. The milestones at `spec.md:85–91` omit this step and its output.
- Impact — The team could finish implementation without establishing whether the production evidence supports actual chat push loss or only the possibility of loss.
- Suggested fix — Add the approved read-only check before implementation and require `result.md` to record the log window, whether a `Data too long` error was attributable to the chat outbox write, the effective SQL mode for application connections, and any gaps that leave the conclusion inconclusive.

### C-r1-02 The RED sequence cannot establish that H2 rejects the old key

- Grounds — `intent.md:42` requires an H2 failure when the old 115-character key is restored. `spec.md:21` assigns this proof to AT-001-1, but `spec.md:13` describes assertions on the stored key and `spec.md:87–88` runs RED before the schema change, then applies both changes before the next documented check.
- Impact — An assertion about the old key's value or length can fail even when H2 successfully stored it. That failure would leave the required database length guard unproven for the actual chat path.
- Suggested fix — Add an explicit check with REQ-002 applied and the old 115-character key still in use. Require evidence that the outbox write fails at H2's length limit, then verify the same path succeeds with the shorter key.

### C-r1-03 The outbox tests do not define how committed insertion is observed

- Grounds — `spec.md:13` and `spec.md:82` call for testing chat send through outbox insertion, but specify neither a commit boundary nor an independent readback. The supplied `.claude/rules/notification.md` says the handler runs `AFTER_COMMIT` and inserts in `REQUIRES_NEW`.
- Impact — A rollback-only fixture transaction can prevent the handler from running. A check that substitutes the event or the handler's call for the resulting row would leave the required persistence path unverified.
- Suggested fix — Require the normal chat send to commit and verify the persisted outbox row for the intended offline recipient from a separate transaction. Make any wait for asynchronous completion bounded and tied to that row.

### C-r1-04 No acceptance test explicitly covers both dimensions of key uniqueness

- Grounds — `spec.md:12` requires distinct keys for different recipients of one message and for different messages to one recipient. `spec.md:13` assigns only key format, length, and row insertion to the two tests, with no fixture or assertion for either comparison.
- Impact — A valid-looking row can satisfy the described tests while another recipient or a later message has its outbox row suppressed.
- Suggested fix — Assign both comparisons to a named acceptance test. Assert the expected persisted rows and their exact keys across at least two recipients of one message and two messages to the same recipient.

### C-r1-05 The SEND tests lack an observable ready subscriber and omit sender closure

- Grounds — `intent.md:24–25` requires sender ERROR plus disconnection and no delivery to subscribers. `spec.md:27` requires connection closure, but the acceptance criteria at `spec.md:29–32` mention only the sender ERROR and absence of a subscriber frame. They specify no evidence that the target subscription was registered and routable before checking silence.
- Impact — Subscriber silence can pass when the subscription is late or misconfigured, particularly for a user-scoped queue. A server that emits an ERROR but keeps the sender connected can also satisfy the stated acceptance criteria.
- Suggested fix — Require AT-003-1 through AT-003-3 to establish each target subscription is ready and routable using a legitimate control delivery or equivalent broker evidence. Then assert the sender's ERROR and transport close and check for non-delivery over a bounded interval.

### C-r1-06 The duplicate-CONNECT RED check has no outcome that confirms its premise

- Grounds — `intent.md:10` and `intent.md:28` identify framework handling of the duplicate request as unverified. `spec.md:41` and `spec.md:87` call for a pre-fix RED run, but give no criterion that distinguishes the second request reaching the application's CONNECT handler from Spring rejecting it independently.
- Impact — A failing test can be recorded as confirmation even if the disputed path never reaches application code, leaving the necessity and reachability of the planned guard unresolved.
- Suggested fix — Define pre-fix evidence that the second request is dispatched to `handleConnect` on the original session, and record which layer accepts or rejects it. Specify the decision branch for the main designer if Spring rejects it before dispatch or the pre-fix evidence is inconclusive.

### C-r1-07 The tests do not show that duplicate rejection preserves the original room binding

- Grounds — `intent.md:28` and `intent.md:45` require the original room binding to survive rejection and the session to remain subject to revocation for that room. `spec.md:39–42` expects the duplicate to close the transport, uses a first-CONNECT-only session for the kick test, and describes AT-004-2 as checking repository calls only. The raw client in AT-004-1 cannot directly observe the server's session attributes after rejection.
- Impact — The listed observations can miss a change to the original binding on the rejection path. A kick test on a connection that never attempted a duplicate cannot detect that change.
- Suggested fix — Specify where the tests observe the original server-side room binding immediately after the duplicate is rejected and before teardown. Have the main designer state how this observation, duplicate transport closure, and the original-room revocation check jointly satisfy the approved behavior when kick and duplicate rejection cannot each close the same transport.

### C-r1-08 The STOMP alias tests stop short of the declared transport behavior

- Grounds — `spec.md:48–52` requires the alias to share CONNECT authentication and duplicate handling, with an ERROR and connection close on an unauthenticated request and a successful connection on a valid one. AT-005-1 and AT-005-2 are unit tests, while the raw WebSocket test at `spec.md:42` uses only CONNECT.
- Impact — Unit assertions can pass while the actual decoder, interceptor, and error handler disagree on alias dispatch, rejection, or transport close. They also leave the one-connection rule across command forms without an explicit acceptance check.
- Suggested fix — Add transport-level coverage of the valid and unauthenticated alias outcomes and verify the duplicate rule also holds when the initial and duplicate requests use different command forms.
