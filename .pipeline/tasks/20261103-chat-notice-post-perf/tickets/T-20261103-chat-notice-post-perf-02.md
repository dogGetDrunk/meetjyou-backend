---
id: T-20261103-chat-notice-post-perf-02
title: 게시글 상세 읽기·조회수 트랜잭션 순차 실행
tier: high
effort_override: null
depends_on: []
requirements: [REQ-002]
acceptance_tests: [AT-002-1, AT-002-2, AT-002-3]
touches:
  - 'src/main/java/com/dogGetDrunk/meetjyou/post/'
  - 'src/test/java/com/dogGetDrunk/meetjyou/post/PostDetailConnectionIntegrationTest.kt'
consumes:
  - 'PostController → PostService.getPostByUuid: 외부 트랜잭션 없음'
  - 'PostViewService.incrementIfEligible'
  - 'CurrentUserProvider'
  - '기존 게시글 상세 응답 DTO.of'
produces:
  - 'PostService.getPostByUuid: 비트랜잭션 조정, readOnly 안에서 완성 DTO 생성 후 조회수 증가'
  - 'PostViewService.incrementIfEligible: 기본 @Transactional (REQUIRES_NEW 제거)'
status: pending
---

## 목표

게시글 상세 읽기 커넥션을 반납한 뒤 별도 조회수 트랜잭션 실행, pool size 1에서 DTO 반환과 증가 커밋 성공.

## 분류·결합도·DAG

- high: 명시적 트랜잭션 경계·커넥션 점유 변경(상 조건), 실제 롤백 검증 필요
- T-01·T-03과 writes 비중첩, NotificationDispatcher는 읽기 선례만 참조·수정 금지; 선행 티켓 없음

## 구현 범위

- 포함: getPostByUuid의 readOnly 경계를 TransactionTemplate 또는 별도 read 빈으로 분리, 조회·연관 로딩·DTO `.of()`까지 읽기 안에서 완료
- 읽기 경계 종료 → 증가 전 views를 가진 DTO 유지 → incrementIfEligible 호출 → DTO 반환 순서
- incrementIfEligible은 기본 @Transactional, 증가 실패는 기존 WARN 후 정상 상세 응답
- 계약은 PostController의 트랜잭션 없는 현재 호출 경로에 한정, 클래스 수준 @Transactional 신규 도입 금지
- 포함: 동일 유저 10분 중복·비로그인 미증가·실패 롤백·응답 views 의미 회귀 검증
- 제외: 상위 트랜잭션 호출 지원, 조회수 비동기화·캐시·카운터 설계 변경, 게시글 편집/party 잠금 변경

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/notification/`의 NotificationDispatcher: TransactionTemplate 선례(읽기만, 변경 금지)
- `src/main/java/com/dogGetDrunk/meetjyou/post/`의 상세 DTO `.of()`와 PostViewService: 응답·중복집계 규칙 재사용

## 참조 파일 (읽기 허용)

- `AGENTS.md`
- `CLAUDE.md`
- `.pipeline/README.md`
- `.pipeline/project.yaml`
- `src/test/resources/` — 기존 test profile 설정만 읽기, 시크릿 접근·설정 변경 금지
- `src/main/java/com/dogGetDrunk/meetjyou/post/` — PostController/Service, PostViewService, 조회수 로그·Repository, 상세 DTO/직접 연관
- `src/main/java/com/dogGetDrunk/meetjyou/notification/` — NotificationDispatcher의 TransactionTemplate 사용 선례만 읽기
- `src/main/java/com/dogGetDrunk/meetjyou/user/` — 현재 사용자·fixture
- `src/main/java/com/dogGetDrunk/meetjyou/party/` — 상세 DTO의 Party 연관·fixture
- `src/main/java/com/dogGetDrunk/meetjyou/plan/` — 상세 DTO의 Plan 연관·접근 guard
- `src/main/java/com/dogGetDrunk/meetjyou/preference/` — 상세 DTO에 필요한 기존 선호도 변환
- `src/main/java/com/dogGetDrunk/meetjyou/common/util/CurrentUserProvider.kt` — 현재 사용자 접근
- `src/test/java/com/dogGetDrunk/meetjyou/post/` — 기존 상세·조회수 fixture와 테스트 설정
- `.claude/rules/post-plan.md` — 게시글·plan 접근 불변식
- `.claude/rules/party.md` — 상세 fixture와 party 불변식

## 관련 spec 발췌

- REQ-002: 실제 상세 조회·DTO 변환·증가 커밋을 Hikari maximum-pool-size=1에서 수행, 테스트 본문 외부 트랜잭션 금지
- 증가 전 views 반환, 10분 중복 제외·유저 없음 미증가·증가 예외 후 정상 응답 유지
- readOnly 경계 안에서 DTO 완성(C-r1-04); 실패는 mock throw만이 아니라 실제 증가 트랜잭션 롤백 후 DB 상태 확인
- TransactionTemplate readOnly 또는 별도 빈의 선택은 spec이 허용한 구현 재량; 자기 호출 프록시 우회 금지

## 실행 순서

- [ ] AT-002-1~3 보호 파일 복사, pool 1의 현재 조회수 미증가 RED 확인
- [ ] 읽기 전용 TransactionTemplate 또는 별도 빈으로 완성 DTO 반환 경계 구성
- [ ] 조정 메서드의 트랜잭션 제거, incrementIfEligible 기본 전파 적용·WARN catch 유지
- [ ] pool 1 실제 커밋·중복·비로그인·실제 롤백 테스트 → 전체 test/build → dockerTest 증거 기록

## 실행·보호·완료 조건

- base_ref: `origin/main`; 이 카드의 범위만 변경, 승인된 intent 수정 금지
- test-writer가 선작성한 보호 파일을 태스크 `tests/code/<저장소 상대 경로>`에서 엔진 지시대로 복사; 보호 파일 수정 금지, 오류 의심 시 근거와 함께 escalation
- 구현 전 해당 AT 실패(RED), 구현 후 통과(GREEN) 확인; 애플리케이션 코드·테스트 코드 작성은 별도 역할
- 필수 gate: `./gradlew test build`; 추가 필수 증거: `./gradlew dockerTest`
- 로컬 Docker 미가동 시 dockerTest 미실행을 통과로 보고하지 않음; `.github/workflows/ci.yml`의 `./gradlew build dockerTest`가 동일한 불변 HEAD에서 통과한 증거로만 대체, 머지는 CI 통과 후
- 최종 HEAD·실행 명령·exit code·테스트 개수·실패/미실행 사유 기록; 코드 리뷰 의견으로 gate 결과 대체 금지
- 운영 배포·운영/공유 DB 접근·시크릿 접근 금지; 로컬 Testcontainers의 Flyway 적용만 허용
- 주석·로그 영어, 서비스 DTO `.of()`, CurrentUserProvider, `?: throw`, 명시적 트랜잭션, 반복문 Repository 조회 금지 등 CLAUDE.md 준수; 기존 IDENTITY outbox 건별 저장은 승인 spec 범위로 유지

## 입력·경로 한계

- 이 계획은 지정된 문서만으로 작성; 기존 애플리케이션 소스·테스트·CI 파일 미열람
- 아래 소스 디렉터리는 CLAUDE.md의 패키지 지도에 따른 모듈 읽기 범위; 세부 하위 파일 경로·기존 메서드의 전체 시그니처는 확인하지 않은 상태
- 다음 워커의 탐색 범위는 touches 및 참조 파일에 명시된 디렉터리 안의 열거 클래스·직접 의존 파일로 한정, 새 테스트 경로는 이 카드에서 지정
- 열거 클래스·직접 의존이 허용 범위에 없으면 추정 경로로 확장하지 않고 메인 설계자에게 누락 경로 escalation; 시크릿·자격증명 파일은 어떤 디렉터리 안에서도 제외
