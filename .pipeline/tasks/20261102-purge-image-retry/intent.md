# Intent

## 해결할 문제

- `WithdrawnUserPurgeScheduler.purgeExpiredWithdrawals`(:32)가 `UserImgService.deleteUserProfileImg` 결과를 확인하지 않음
  - `OracleObjectStorageService.deleteObject`는 모든 예외를 삼키고 `false` 반환, `deleteUserProfileImg`는 반환값을 버림(`Unit`)
- 이어서 `WithdrawnUserPurgeService.purge`가 `anonymize()` → `hasProfileImage=false`, 상태 PURGED
- OCI 일시 장애·권한 오류 시: 이미지 삭제 실패에도 PURGED → `findPurgeTargets`(DELETED만) 대상에서 빠져 재시도 없음 → 프로필 원본·썸네일 OCI 영구 잔존
- 스케줄러 KDoc "실패 시 다음날 재시도"는 이미지 삭제 실패에는 성립하지 않음
- 영향: 탈퇴 유저 개인 이미지 미파기 (개인정보 파기 책임)

## 의도

- 이미지 삭제가 확인되지 않은 유저는 PURGED로 넘기지 않고 다음 실행에서 다시 시도
- 실패는 조용히 묻히지 않고 운영자가 알 수 있게 Discord 알림

## 포함 범위

- 스케줄러: 이미지 삭제 결과 확인 → 실패 유저를 이번 `purge` 대상 id에서 제외
- `UserImgService.deleteUserProfileImg`: 원본·썸네일 둘 다 삭제 성공 여부 반환
- `OracleObjectStorageService.deleteObject`: 404(이미 없음)를 성공으로 판정 (D2)
- 실패 시 `DiscordAlertService.sendOperationalAlert` 알림 (D3)
- 스케줄러 KDoc·`.claude/rules/user.md` Purge 항목 갱신
- 단위 테스트: 실패 유저 제외·성공 유저 정상 purge·알림 발송, 404 성공 판정

## 제외 범위

- 게시글·파티 이미지 삭제(`PostImgService`·`PartyImgService`)의 같은 실패 무시 패턴 — 별도 이슈 후보
- 영구 실패 유저의 재시도 횟수 상한·별도 상태 — 알림으로 관측 후 판단
- 분산 락(ShedLock) 등 다중 인스턴스 대응

## 열린 결정 (사람 결정 필요)

- **D1. 실패 유저 처리 방식**
  - A안 (추천, 이슈 수정 방향): 이번 purge에서 제외 → DELETED 유지 → 다음날 재시도
    - 장점: 스키마 변경 없음, 스케줄러 국소 변경
    - 비용: OCI 장애 동안 해당 유저 DB 익명화도 지연 (닉네임 해제·소셜 계정 해제 포함, 장애 기간만큼)
  - B안: DB purge는 진행 + 이미지 삭제 대기 레코드(새 테이블)로 별도 재시도
    - 기각 사유 후보: migration·테이블·재시도 잡 추가 → 일 1회 소수 유저 규모에 과설계
- **D2. 404(오브젝트 없음) 판정**
  - 추천: 성공으로 처리
    - 근거: 안 하면 `hasProfileImage=true`인데 썸네일이 없는 유저(업로드 일부 실패·수동 삭제 등)가 영원히 purge 안 됨 + 매일 알림
    - 영향: `deleteObject` 호출부 4곳(user·post·party 이미지) 모두 반환값 무시 중 → 동작 변화 없음
- **D3. 알림 단위**
  - 추천: 실행 1회당 알림 1건 (실패 수 + 실패 uuid 목록)
  - 대안: 유저당 1건 — 장애 시 알림 폭주

## 성공 기준

- 이미지 삭제 실패 유저: 이번 실행 후에도 DELETED 유지, 다음 실행의 `findPurgeTargets`에 다시 포함
- 같은 실행의 이미지 삭제 성공·이미지 없는 유저: 기존대로 PURGED
- 실패 시 Discord 운영 알림 1건 발송, 실패 없으면 알림 없음
- 404 응답은 삭제 성공으로 판정
- `./gradlew build`(detekt·Konsist 포함), `./gradlew test` 통과
- 회귀 확인: 수정 되돌리면 새 테스트가 red

## 작업 크기

S — 스케줄러 1곳 + 스토리지 삭제 판정 1곳의 국소 버그 수정, 스키마 변경 없음

## 출처

- 노션 태스크: https://app.notion.com/p/40556d9a97d981869056c2fadb499c17 (탈퇴 회원 프로필 사진 완전 삭제 보장, 작업 #235)
- GitHub 이슈: dogGetDrunk/meetjyou-backend#229 (2026-10-26 전체 코드 리뷰 Med)
