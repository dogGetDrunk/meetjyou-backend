### V-r1-01 AT-003-3 drops the approved subscriber checks

- **Kind** — A silent reversal.
- **Grounds** — The accepted decision requires “구독 준비를 정상 경로 control 프레임 도착으로 먼저 증명, 공격자 ERROR와 transport 종료, 1초 동안 미수신 확인으로 AT-003-1~3 강화.” The committed test checks the sender’s ERROR and transport closure only. It creates no victim connection or subscription. The changelog records the reduced coverage, and the decision record contains no human approval for that deviation. [decisions.md:12](/Users/serendipity/projects/meetjyou-backend-tasks/20261124-chat-security-fix/.pipeline/tasks/20261124-chat-security-fix/decisions.md:12) · [ChatStompFrameGuardIntegrationTest.kt:86](/Users/serendipity/projects/meetjyou-backend-tasks/20261124-chat-security-fix/src/test/java/com/dogGetDrunk/meetjyou/chat/ChatStompFrameGuardIntegrationTest.kt:86) · [spec-changelog.md:11](/Users/serendipity/projects/meetjyou-backend-tasks/20261124-chat-security-fix/.pipeline/tasks/20261124-chat-security-fix/spec-changelog.md:11)
- **Decision broken** — C-r1-05, REQ-003, AT-003-3.
- **Impact** — A passing test establishes sender rejection while leaving the required non-delivery to a demonstrably routable subscriber unverified.

### V-r1-02 The production check is missing from the required result record

- **Kind** — An accepted critique item not implemented.
- **Grounds** — REQ-000 says “수용 기준: `result.md` 에 다음을 기록” and requires the log window, evidence about the application connection’s effective SQL mode, and the conclusion with any remaining confirmation steps. `logs/ops-check.md` records those findings. `result.md` remains a template. [spec.md:8](/Users/serendipity/projects/meetjyou-backend-tasks/20261124-chat-security-fix/.pipeline/tasks/20261124-chat-security-fix/spec.md:8) · [ops-check.md:3](/Users/serendipity/projects/meetjyou-backend-tasks/20261124-chat-security-fix/.pipeline/tasks/20261124-chat-security-fix/logs/ops-check.md:3) · [result.md:3](/Users/serendipity/projects/meetjyou-backend-tasks/20261124-chat-security-fix/.pipeline/tasks/20261124-chat-security-fix/result.md:3)
- **Decision broken** — C-r1-01, REQ-000, intent line 30.
- **Impact** — The required result record omits the production check’s inconclusive outcome and the evidence needed to resolve it.

### V-r1-03 The H2 intermediate-state evidence is missing from the required result record

- **Kind** — An acceptance criterion not met.
- **Grounds** — REQ-002 says “이 중간 상태 결과를 `result.md` 에 기록.” The RED log records zero outbox rows and H2’s length error with the old key, followed by success with the shortened key. `result.md` contains no record of that comparison. [spec.md:33](/Users/serendipity/projects/meetjyou-backend-tasks/20261124-chat-security-fix/.pipeline/tasks/20261124-chat-security-fix/spec.md:33) · [red-observations.md:27](/Users/serendipity/projects/meetjyou-backend-tasks/20261124-chat-security-fix/.pipeline/tasks/20261124-chat-security-fix/logs/red-observations.md:27) · [result.md:3](/Users/serendipity/projects/meetjyou-backend-tasks/20261124-chat-security-fix/.pipeline/tasks/20261124-chat-security-fix/result.md:3)
- **Decision broken** — REQ-002, implementing C-r1-02.
- **Impact** — The required result record omits the evidence that H2 rejected the old key at the database boundary on the chat send path.

### V-r1-04 The MySQL test can pass with fewer than four outbox rows

- **Kind** — An acceptance criterion not met.
- **Grounds** — The spec defines the two-message, two-recipient scenario as “행 4개, 정확한 키 4개, 모두 100자 이하” and requires AT-001-2 to run the same scenario in MySQL. The Docker test derives the expected row count entirely from the messages it reads back. It never asserts that both messages persisted or that four outbox rows exist. [spec.md:23](/Users/serendipity/projects/meetjyou-backend-tasks/20261124-chat-security-fix/.pipeline/tasks/20261124-chat-security-fix/spec.md:23) · [ChatPushOutboxDockerTest.kt:99](/Users/serendipity/projects/meetjyou-backend-tasks/20261124-chat-security-fix/src/dockerTest/java/com/dogGetDrunk/meetjyou/chat/ChatPushOutboxDockerTest.kt:99)
- **Decision broken** — REQ-001, AT-001-2.
- **Impact** — If only one send persists, two matching outbox rows can satisfy the test. If neither persists, empty message and outbox results can also pass.