# 결과

## 결과 요약

- 탈퇴 유저 purge에서 프로필 이미지 삭제 실패 유저를 PURGED에서 제외 → DELETED 유지, 다음날 재시도
- 실패 시 실행당 Discord 운영 알림 1건(실패 수 + uuid)
- OCI 404(이미 없음)를 삭제 성공으로 판정
- 이슈 #229 종료, 노션 작업 #235

## 머지된 PR

| 티켓 | PR | 등급 | 구현 / 리뷰 모델 | 라운드 |
|---|---|---|---|---|
| T-20261102-purge-image-retry-01 | #253 (merge `baaad2c`) | medium → high 승급 | gpt-6.1-terra ×4 실패 → claude-opus-5-5(medium) / 교차 리뷰 미완, Claude Opus 대체 리뷰 | 구현 5회, 리뷰 1회(미완) |

## 핵심 결정과 기각한 대안

- D1 실패 유저 purge 제외 후 다음날 재시도 / 기각: DB purge 진행 + 이미지 삭제 대기 테이블 (migration·재시도 잡 추가, 일 1회 소수 유저 규모에 과설계)
- D2 404 = 삭제 성공 / 기각: 404도 실패 — 썸네일 없는 유저가 영구 미purge + 매일 알림
- D3 실행당 알림 1건 / 기각: 유저당 1건 — 장애 시 알림 폭주
- 머지 경로: 교차 리뷰어(gpt-6.1-sol)가 입력 누락(구현자 진영·rules 원문)으로 escalation 후 종료 → 사용자 결정으로 Claude Opus 리뷰·머지 판단 대체, **반대 진영 교차 리뷰 생략** (PR #253 코멘트)

## spec 대비 검증

- AT-1-1·1-2·2-1·3-1 단위 테스트로 구현
- gate `./gradlew test build` head `e932917` 통과, CI `build-and-test` 통과
- 회귀: 수정 전 스케줄러는 실패 유저 id도 purge에 넘김 → AT-1-1·1-2 red

## 남은 후속 작업

- 게시글·파티 이미지 삭제(`PostImgService`·`PartyImgService`)의 같은 실패 무시 패턴 — 별도 이슈 후보
- 영구 실패 유저 재시도 상한 — 알림 빈도 관측 후 판단
- 파이프라인 엔진 결함
  - 교차 리뷰어 입력에 구현자 진영·적용 `.claude/rules` 원문 누락 → 리뷰 정체
  - 구현 워커 지시가 "GitHub 쓰기 금지"와 "draft PR 생성"을 동시 요구 → gpt-6.1-terra 4회 실패
