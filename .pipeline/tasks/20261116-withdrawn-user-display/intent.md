# Intent

> 메인 설계 에이전트가 사람과 대화로 작성. 작성을 마치면 첫 줄의 template 표시 삭제.
> 승인 기록은 `approval.json`(`agent-pipeline approve-intent`), 승인 후 이 파일 수정 금지.

## 해결할 문제

- 탈퇴 회원의 작성자 표시가 화면마다 다름
  - 게시글 작성자(`PostAuthorSummary`): 탈퇴 요청 즉시(`DELETED`)부터 `탈퇴한 사용자` 로 가리고 `isWithdrawn=true`, `hasProfileImage=false`
  - 아래 4개 응답: `user.nickname`, `user.hasProfileImage` 를 그대로 내려줌
    - 채팅 메시지 발신자 `ChatMessageResponse.senderNickname` (조회와 WebSocket 브로드캐스트 공통)
    - 파티 멤버 목록 `PartyMemberResponse.nickname`
    - 가입 신청 목록 `PendingJoinRequest.nickname`
    - 알림센터 받은 신청 `ReceivedApplicationItem.nickname`
- 완전 탈퇴(`PURGED`) 회원은 익명화 값(`~` + id 36진수, 예: `~1a`)이 위 4곳에 그대로 표시됨
- 탈퇴는 게시글, 파티, 계획에 연쇄하지 않으므로(`.claude/rules/user.md`) 탈퇴 회원은 파티 멤버와 신청 목록에 계속 남음

## 의도

- 팀 결정(노션 작업 238 "탈퇴한 사용자 UI 표현 정책 결정", 11/16 작업 218에 반영): 탈퇴 회원의 공유 콘텐츠는 유지하고 작성자 표시만 `(탈퇴한 사용자)` 로 변경, 익명화 시점은 완전 탈퇴(탈퇴 후 30일)
- 사람 결정(민석, 11/16 이 세션)
  - 가리는 시점: **완전 탈퇴(`PURGED`)만**, 게시글 작성자까지 포함해 5개 응답 모두 같은 규칙. 유예 중(`DELETED`)인 회원은 원래 닉네임과 프로필 사진 그대로 표시
  - 문구: 괄호 포함 **`(탈퇴한 사용자)`** 로 변경, 게시글 작성자 문구도 같이 바뀜
  - 응답 필드: 4개 응답에 게시글과 같은 `isWithdrawn` 추가, 가려진 회원은 `hasProfileImage=false`. 기준은 위 시점과 같음(`PURGED` 일 때만 `isWithdrawn=true`)
- 표시 규칙(시점, 문구, 사진 여부)은 한 곳에 두고 5개 응답이 같이 씀 → 다음 응답이 추가될 때 규칙이 갈라지지 않게

## 포함 범위

- 탈퇴 회원 표시 규칙 공통화: `PURGED` 이면 닉네임 `(탈퇴한 사용자)`, `hasProfileImage=false`, `isWithdrawn=true`, 아니면 원래 값과 `isWithdrawn=false`
- 적용 대상 5개 응답: 게시글 작성자(`PostAuthorSummary`, 기존 `DELETED` 포함 기준을 `PURGED` 만으로 변경), 채팅 메시지 발신자, 파티 멤버 목록, 가입 신청 목록, 알림센터 받은 신청
- 4개 응답에 `isWithdrawn` 필드 추가(필드 추가만, 기존 필드 이름과 타입 유지)
- 단위 테스트(규칙)와 통합 테스트(5개 응답 각각 `NORMAL`, `DELETED`, `PURGED`)

## 제외 범위

- **공개 프로필 404 정책**: `getPublicUserProfile` 은 지금처럼 `DELETED` 부터 404 유지. 기능 정의서 탈퇴 정책 기준으로 따로 정한 규칙이라 이번 "표시 통일"에 넣지 않음 → 유예 중 회원은 채팅과 파티에서 닉네임은 보이지만 프로필을 열면 404 (아래 성공 기준 바깥, 결과에 기록)
- 푸시 알림 본문의 닉네임(`ChatService`, `PartyService` 의 `bodyArgs`): 발신 시점에 활동 중인 회원만 보내므로 대상 아님
- 탈퇴 회원의 대기 신청 자동 거절, 모집글 자동 마감, 파티장 위임: #233, #279 와 작업 238 질문 3 (다음 정기 회의)
- 채팅 메시지 본문 속 개인정보 처리(작업 238 안건 2)
- 프론트 화면 변경(문구 감싸기, 회색 처리 등)

## 성공 기준

- `PURGED` 회원: 5개 응답 모두 닉네임 `(탈퇴한 사용자)`, `hasProfileImage=false`, `isWithdrawn=true`
- `DELETED`(유예 중) 회원: 5개 응답 모두 원래 닉네임, 실제 `hasProfileImage`, `isWithdrawn=false` (게시글 작성자는 지금과 달라짐)
- `NORMAL` 회원: 기존 응답과 같은 값, `isWithdrawn=false`
- 표시 규칙의 정의가 코드에 한 곳뿐이고, 5개 응답이 모두 그것을 사용
- 이 변경으로 추가 쿼리 없음(이미 로딩한 `user` 의 `status` 만 사용)
- 회원 닉네임으로 `(탈퇴한 사용자)` 를 가입, 수정할 수 없음(기존 검증 테스트 유지)
- `./gradlew test` 와 `agent-pipeline gate` 통과

## 작업 크기

M — 채팅, 파티, 알림센터, 게시글 4개 모듈의 응답 5개에 공통 표시 규칙 하나를 적용하는 기능 단위 변경

## 출처

- 노션 태스크: 218 "회원 탈퇴 처리 마무리" https://app.notion.com/p/3ff56d9a97d98120b313e13c0dfc6275 (근거 결정: 작업 238)
- GitHub 이슈: dogGetDrunk/meetjyou-backend#207
