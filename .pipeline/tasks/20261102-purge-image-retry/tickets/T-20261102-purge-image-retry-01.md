---
id: T-20261102-purge-image-retry-01
title: purge 시 프로필 이미지 삭제 실패 유저 제외·재시도 + 알림
tier: medium
effort_override: null
depends_on: []
requirements: [REQ-1, REQ-2, REQ-3, REQ-4]
acceptance_tests: [AT-1-1, AT-1-2, AT-2-1, AT-3-1]
touches:
  - src/main/java/com/dogGetDrunk/meetjyou/user/WithdrawnUserPurgeScheduler.kt
  - src/main/java/com/dogGetDrunk/meetjyou/image/cloud/oracle/service/UserImgService.kt
  - src/main/java/com/dogGetDrunk/meetjyou/cloud/oracle/OracleObjectStorageService.kt
  - src/test/java/com/dogGetDrunk/meetjyou/user/WithdrawnUserPurgeSchedulerTest.kt
  - src/test/java/com/dogGetDrunk/meetjyou/image/cloud/oracle/service/UserImgServiceTest.kt
  - src/test/java/com/dogGetDrunk/meetjyou/cloud/oracle/OracleObjectStorageServiceTest.kt
  - .claude/rules/user.md
consumes:
  - DiscordAlertService.sendOperationalAlert(source, exceptionClass, summary, detail)
  - WithdrawnUserPurgeService.findPurgeTargets / purge(userIds, now)
produces:
  - UserImgService.deleteUserProfileImg(uuid): Boolean
  - OracleObjectStorageService.deleteObject — 404를 true로 판정
status: ready
---

## 목표
탈퇴 유저 purge에서 프로필 이미지 삭제가 실패하면 그 유저를 PURGED로 넘기지 않고 다음날 재시도, 실패는 Discord 알림. 이슈 #229.

## 구현 범위
- REQ-1: `WithdrawnUserPurgeScheduler.purgeExpiredWithdrawals` — 이미지 있는 대상의 삭제 결과 수집 → 실패 유저 id를 `purge` 인자에서 제외. 남은 id가 없으면 `purge` 미호출. 이미지 삭제는 계속 트랜잭션 밖·purge 전
- REQ-2: 실패 1명 이상이면 실행당 `sendOperationalAlert` 1회 (source 예: `"WithdrawnUserPurgeScheduler"`, 실패 수 + uuid 목록). 로그(영어)에 실패 uuid. 상수화
- REQ-3: `OracleObjectStorageService.deleteObject` — `BmcException` statusCode 404면 true (warn/info 로그), 그 외 기존대로 false. `UserImgService.deleteUserProfileImg`는 원본·썸네일 둘 다 시도 후 둘 다 성공일 때만 true
- REQ-4: 스케줄러 KDoc을 실제 동작(이미지 삭제 실패 → 제외 → 다음날 재시도)으로 갱신, `.claude/rules/user.md` Purge 항목에 한 줄 반영
- 수용 테스트 AT-1-1·1-2·2-1·3-1 작성. 수정 되돌리면 AT-1-1이 red인지 확인·PR에 기록
- 컨트롤러 `deleteUserProfileImg` 호출부는 반환값 무시 그대로 (동작 불변)

### 하지 말 것
- `PostImgService`·`PartyImgService` 실패 처리 변경
- 재시도 횟수 상한·새 상태·새 테이블·migration
- `WithdrawnUserPurgeService.purge` 트랜잭션 구조 변경

## 참고할 기존 패턴
- src/main/java/com/dogGetDrunk/meetjyou/auth/social/apple/AppleAuthorizationRevoker.kt (`sendOperationalAlert` 사용)
- src/main/java/com/dogGetDrunk/meetjyou/cloud/oracle/OracleObjectStorageService.kt `getObjectContent` (404 판정)
- Kotest `InstancePerLeaf` — 상태 있는 mock은 `beforeEach`에서 재생성/clear

## 관련 spec 발췌
- 원인: `deleteObject`가 예외를 삼키고 false, `deleteUserProfileImg`가 결과를 버림 → `anonymize()`로 PURGED → `findPurgeTargets`(DELETED만)에서 영구 제외
- 결정: D1 A안(제외 후 재시도), D2 404=성공, D3 실행당 알림 1건 — intent.md
- 검증: `./gradlew test build`
