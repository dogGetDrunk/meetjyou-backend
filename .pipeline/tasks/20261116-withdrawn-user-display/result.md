# 결과

> 태스크 PR 전에 태스크 세션이 작성. 포트폴리오와 회고 재료.

## 결과 요약

- 다른 회원을 공유 콘텐츠 옆에 보여 주는 응답 5개가 표시 규칙 하나(`UserDisplay.of`)를 씀 (#207)
  - 게시글 작성자, 채팅 메시지 발신자(이력 조회와 WebSocket 브로드캐스트), 파티 멤버 목록, 가입 신청 목록, 알림센터 받은 신청
- 규칙: 완전 탈퇴(`PURGED`)만 가림 → 닉네임 `(탈퇴한 사용자)`, `hasProfileImage=false`, `isWithdrawn=true`. `NORMAL` 과 `DELETED`(유예 중)는 원래 값, `isWithdrawn=false`
- 배포된 동작 변경 2건(앱 확인 필요)
  - 게시글 작성자: 유예 중(`DELETED`) 회원도 이제 원래 닉네임과 사진으로 보임 (이전: 탈퇴 요청 즉시 가림)
  - 문구: `탈퇴한 사용자` → `(탈퇴한 사용자)`
- 4개 응답에 `isWithdrawn` 필드 추가(필드 추가만, 기존 필드 유지)

## 변경 내용

- 신규 `user/dto/UserDisplay.kt`: 문구 상수와 가림 분기의 유일한 정의
- `PostAuthorSummary`, `ChatMessageResponse`, `PartyMemberResponse`, `PendingJoinRequest`(`PartyService.getPendingJoinRequests`), `ReceivedApplicationItem`(`NotificationCenterService.getReceivedApplications`)가 `UserDisplay` 사용
- `ChatSwaggerDocController` 예시 응답에 `isWithdrawn` 추가
- `.claude/rules/user.md`: 표시 규칙과 "공개 프로필 404가 더 엄격한 건 의도된 차이" 1줄
- 테스트
  - 신규: `UserDisplayTest`(AT-001), `ChatMessageResponseTest`(AT-003), `WithdrawnUserDisplayIntegrationTest`(AT-003~006, HTTP, 세 상태 모두)
  - 추가 케이스: `GetPartyMembersTest`(AT-004), `GetPendingJoinRequestsTest`(AT-005), `NotificationCenterServiceTest`(AT-006), `ChatServiceTest` 브로드캐스트 페이로드(AT-003b)
  - 수정: `PostAuthorSummaryTest`, `PostAuthorSummaryIntegrationTest`(AT-002, DELETED 기대값 반전 + PURGED 케이스), `UserRequestValidationTest`(AT-007, 상수 참조만)
  - 픽스처: `UserFixtures.gracedUser`, `UserFixtures.purgedUser`

## 비평 결정

- 1라운드, 비평 모델 `gpt-6.1-sol`(high, 다른 벤더), 3건
  - C-r1-01 수용: HTTP 테스트에 NORMAL 대상(호스트, 정상 신청자) 추가
  - C-r1-02 수용: 브로드캐스트 이벤트 페이로드 단위 검증(AT-003b) 추가
  - C-r1-03 기각-오판: `agent-pipeline gate` 가 곧 `./gradlew test build`(`.pipeline/project.yaml`)

## gate 근거

- `agent-pipeline gate`(`./gradlew test build`): `BUILD SUCCESSFUL`, exit 0
- 테스트 821건 통과, 실패 0, 건너뜀 0 (`build/test-results/test` 집계)
- detekt, Konsist 통과. 1차 실행은 새 테스트의 줄 길이와 파라미터 수로 detekt 실패, 2차 실행은 통합 테스트 준비 코드의 detached 엔티티로 4건 실패 → 둘 다 테스트 코드 수정 후 통과
- `./gradlew compileDockerTestKotlin` exit 0 (dockerTest는 실행 안 함, DB 스키마와 쿼리 변경 없음)
- 쿼리 수 테스트(`ChatReadServiceQueryOptimizationTest`, `PartyQueryOptimizationTest`) 기대값 변경 없이 통과 → 추가 쿼리 없음

## 핵심 결정과 기각한 대안

- 가리는 시점은 `PURGED` 만, 게시글까지 같은 규칙(민석, 11/16)
  - 근거: 작업 238 결론(익명화는 완전 탈퇴 시점), 유예 중 회원은 복구 가능
  - 기각: 탈퇴 즉시(`DELETED`부터) 가림. 게시글과 공개 프로필이 이미 쓰던 기준이라 일관성은 높지만, 238 결정과 어긋남
- 공개 프로필 404는 `DELETED` 부터 그대로 유지(민석, 11/17 승인 때)
  - 기능 정의서 기준으로 따로 정한 규칙이라 "표시 통일"에 넣지 않음
  - 감수: 유예 중 회원은 채팅이나 파티에서 닉네임이 보이지만, 프로필을 누르면 404
- 문구 `(탈퇴한 사용자)`(민석): 작업 238 표기를 따름. 괄호와 공백이 닉네임 패턴 밖이라 실제 회원과 겹치지 않음(`UserRequestValidationTest`)
- 규칙 위치: 응답마다 분기하지 않고 `UserDisplay` 값 객체 하나에 둠
  - 기각: `User` 엔티티에 표시 메서드(화면 문구가 도메인 엔티티로 들어감), 응답마다 직접 분기(이번 #207이 바로 그렇게 갈라진 결과)
- `isWithdrawn` 필드 추가(민석): 앱이 문구 비교 없이 회색 처리 등을 할 수 있게, 게시글 응답과 같은 이름

## 남은 후속 작업

- (사람) 프론트에 공유: 게시글 작성자 기준 변경(유예 중 회원 다시 표시), 문구 괄호 추가, 4개 응답의 `isWithdrawn` 신규 필드
- (사람) 노션 작업 218 TODO "채팅, 파티 멤버, 알림센터의 탈퇴 회원 표시 통일" 체크와 이슈 #207 닫기 (PR 머지 후)
- 범위 밖으로 남긴 것: 푸시 본문 닉네임(발신 시점 활동 회원만), 탈퇴 회원 대기 신청 자동 거절과 모집글 자동 마감(#233, 작업 238 질문 3), 파티장 위임(#279), 채팅 본문 속 개인정보(작업 238 안건 2)
