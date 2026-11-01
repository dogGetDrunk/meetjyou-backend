# Intent

> 메인 설계 에이전트가 사람과 대화로 작성. 작성을 마치면 첫 줄의 template 표시 삭제.
> 승인 기록은 `approval.json`(`agent-pipeline approve-intent`), 승인 후 이 파일 수정 금지.

## 해결할 문제

- 모집글(`Post`) 수정이 연결 파티(`Party`)에 반영되지 않음
  - `PostService.updatePost`: `post.capacity`·`itinStart`·`itinFinish`·`location`만 변경, `party.capacity`·일정·`destination`은 `createParty` 시점 값 고정
  - `PostService.updatePostStatus`: `RECRUITMENT_COMPLETED`로 바꿔도 `party.recruitmentStatus`는 `OPEN` 유지 (`Party.complete()`에서만 `CLOSED`)
- 정원 판정은 `party.capacity` 기준(`requestJoinParty`, `approveJoinRequest`) → 정원 3→10 수정 후에도 3명에서 `PARTY_FULL`
- 정원 축소 시 `joined > capacity`("4/3") 상태 가능
- 모집 완료한 모집글의 파티가 계속 가입 신청을 받음
- `GetPostResponse`·`GetPartyResponse`의 정원·일정 값 불일치

## 의도

- 모집글을 원본으로, 모집글 수정·상태 변경과 같은 트랜잭션에서 파티의 정원·일정·목적지·모집 상태 동기화
- 정원을 현재 `joined` 미만으로 줄이는 요청 거부
- 파티 행 쓰기는 기존 락 규칙 준수: `party.capacity`는 `joined`와 같은 컬럼군 → `partyRepository.lockByUuid`(파티 행 우선 락, `updatePostStatus`·`completeParty`와 같은 순서)

## 결정 (2026-11-02 사람 확정)

- **D1. 동기화 방향** — 확정 A
  - A: 모집글 = 원본, `updatePost`/`updatePostStatus`가 파티 필드 갱신
  - B: 파티 생성 후 모집글의 정원·일정·장소 수정 금지
  - 근거: 파티는 `createPost`로만 생성(1:1), 수정 UI 경로가 모집글뿐 → B는 기능 축소. A는 기존 API 계약 유지
- **D2. 정원 < 현재 `joined` 응답** — 확정 409
  - 프로젝트 규칙 "현재 상태 때문에 거부 → 409, 형식 오류 → 400" 적용. 값 자체(1~10)는 유효 입력
  - 신규 `ErrorCode` 추가 + `ErrorCodeStatusTest` 표 갱신
- **D3. 모집 완료 시 기존 PENDING 신청 승인 허용 여부** — 확정 허용 (팀 논의 필요, 노션 태스크에 기록 — 결론이 바뀌면 후속 태스크)
  - 허용: 신규 신청만 차단(`requestJoinParty`의 기존 `recruitmentStatus` 검사), 대기 중 신청은 방장이 승인 가능
  - 차단: `approveJoinRequest`에도 모집 상태 검사 추가
  - 근거: 보드 태스크 범위가 "계속 신청을 받음", 최소 변경. 정원 초과는 기존 capacity 검사로 계속 방어
- **D4. 모집 재개(`RECRUITMENT_COMPLETED` → `RECRUITING`)** — 확정 대칭: 파티도 `OPEN` 복귀. COMPLETED 파티는 기존 `validatePostWritable`로 수정 자체 차단

## 포함 범위

- `updatePost`: 파티 락 → 정원 검증(D2) → 파티 `capacity`·`itinStart`·`itinFinish`·`destination` 갱신
- `updatePostStatus`: 파티 `recruitmentStatus` 동기화 (D3·D4)
- 신규 ErrorCode·예외, `ErrorCodeStatusTest` 갱신
- 단위 테스트(Kotest `BehaviorSpec`) + 통합 테스트(동기화 결과, 정원 축소 거부, 모집 완료 후 신청 거부)
- `.claude/rules/party.md` 락 목록에 `capacity`(updatePost) 추가, 락 순서가 바뀌면 #165 accepted deadlock 목록 재검토

## 제외 범위

- 기존 데이터 보정(backfill): 10/31 점검 기준 운영 DB 파티 0건 → 불필요
- 파티 측 정원·일정 수정 API 신설
- 프론트엔드 변경
- `requestJoinParty`의 비락 정원 판정 등 기존 동시성 구조 재설계
- `party.name`, 이미지, plan 동기화(기존 동작 유지)

## 성공 기준

- 모집글 정원 3→10 수정 후 4번째 신청·승인 성공
- `joined` 미만으로 정원 축소 → 409, 모집글·파티 모두 변경 없음(롤백)
- 정원·일정·목적지 수정 후 `GetPostResponse`와 `GetPartyResponse` 값 일치
- 모집 완료 전환 후 신규 가입 신청 → `PartyRecruitmentClosedException`, 재개 시 다시 신청 가능
- `./gradlew test build` 통과 (detekt·Konsist 포함)

## 작업 크기

M — 기능 하나(post→party 동기화)지만 락 경로·모집 상태 전이·정원 검증·ErrorCode가 함께 바뀜

## 출처

- 노션 태스크: https://app.notion.com/p/40556d9a97d981fe9d88cec50e483ed9 ("모집글 수정 내용(정원·모집 마감)을 파티에 반영", 작업 ID 230)
- GitHub 이슈: https://github.com/dogGetDrunk/meetjyou-backend/issues/223
