검토 범위: REQ-001~REQ-005의 구현자 지시서 4개·티켓·spec·보호 테스트 12개. 아래 경로는 별도 표시가 없으면 `.pipeline/tasks/20261103-chat-notice-post-perf/` 기준.

### B-r1-01 약관 발송 메서드의 구형 계약과 확정 인터페이스 혼재

- 중요도: blocking / REQ-003·REQ-004·REQ-005 / T-03·T-04
- 근거: `logs/briefs/r1/T-20261103-chat-notice-post-perf-03.md:74`의 produces는 `TermsReconsentBroadcaster.broadcast(termsUuid: UUID): Int`; 같은 지시서 169·172행의 확정 계약은 T-03 `broadcastOnPublish(UUID)`, T-04 `resend(UUID)`.
- 근거: `logs/briefs/r1/T-20261103-chat-notice-post-perf-04.md:61,70,89,216`의 consumes·produces·구현 범위·실행 순서는 `broadcast(UUID)`에 `validateApplicability: Boolean = false`를 추가하고 재발송에서 true로 호출하도록 요구; 같은 지시서 160·163행 및 `spec.md:161,164`는 두 public 메서드 분리 계약. 원본 티켓에도 동일 내용 존재.
- 보호 테스트 의존: `tests/code/src/test/java/com/dogGetDrunk/meetjyou/notification/BroadcastPreferenceBatchTest.kt:73,105` 및 `BroadcastAlreadySentTest.kt:134,138`은 `broadcastOnPublish` 호출; `tests/code/src/test/java/com/dogGetDrunk/meetjyou/terms/TermsReconsentBroadcastApiIntegrationTest.kt:217,245,251`은 `resend(UUID)`의 존재·`REQUIRES_NEW` annotation·잠금 후 적격성 검사를 요구.
- 영향: 카드의 produces·실행 순서대로 단일 flag 메서드만 구현하면 수정 금지인 보호 테스트의 호출·reflection 계약 미충족; 두 방식을 모두 제공하면 spec에 없는 호환 메서드와 진입 경로 추가. 구현자가 선택해야 할 계약 불일치.
- 수정 제안: 메인 설계자가 T-03 produces와 T-04 consumes·produces·구현 범위·실행 순서를 확정 계약 `broadcastOnPublish(UUID)`·`resend(UUID)`로 통일한 뒤 지시서 재생성; 보호 테스트·spec의 두 메서드 계약 유지.

### B-r1-02 수정 금지인 경쟁 테스트의 race 함수가 30줄 상한 위반

- 중요도: blocking / REQ-005 / AT-005-3·AT-005-4 / T-04
- 근거: `tests/code/src/dockerTest/java/com/dogGetDrunk/meetjyou/notification/BroadcastConcurrencyDockerTest.kt:102-146`의 `private fun race(isTerms: Boolean, original: Boolean)` 본문 43줄. 저장소 `CLAUDE.md`의 `30-line hard cap on function bodies` 및 `config/detekt/detekt.yml:6-10`의 `LongMethod` 활성화·threshold 30과 충돌.
- 수정 금지 근거: `logs/briefs/r1/T-20261103-chat-notice-post-perf-04.md:242-249`의 원문 복사·수정 금지·sha256 검사 계약. 현재 보호 파일 해시도 지시서와 일치하므로 구현자가 헬퍼 추출로 고치면 보호 계약 위반.
- 영향: 구현과 독립적인 보호 테스트 규칙 위반; 구현자 재시도로 해결할 수 없는 설계 단계 수정 대상.
- 수정 제안: 메인 설계자 또는 테스트 작성 역할이 요청 준비·경쟁 실행·결과 단언을 헬퍼로 나누고 기존 4개 시나리오·실제 lock-wait 관측·finally 해제·단언 유지; 변경된 원본 기준 지시서 해시 재생성.

검증: Python 정적 검사 exit 0, 티켓 4개의 지시서 내 원문 포함 확인, 보호 테스트 12/12 sha256 일치, `race` 본문 43줄 확인. Gradle test·build·dockerTest·detekt 미실행으로 컴파일·런타임·gate 통과 판정 없음. 변경 대상은 본 보고서 1개; 남은 처리 사항은 B-r1-01·B-r1-02별 메인 설계자의 수용/기각 및 근거 기록.
