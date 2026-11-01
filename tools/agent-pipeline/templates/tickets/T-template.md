---
id: T-012
title: 모임 참가 신청 API
tier: high            # high | medium | low
effort_override: null # 기본값 대비 ±1단계까지만. 예: +1, -1
depends_on: [T-009, T-010]
requirements: [REQ-4, REQ-5]
acceptance_tests: [AT-4-1, AT-4-2, AT-5-1]
touches:              # 건드릴 모듈/파일 (의존성 판단 근거, 필수)
  - meeting/application/JoinService.kt
  - meeting/api/JoinController.kt
consumes:             # 사용하는 인터페이스 (필수)
  - MeetingRepository.findByIdForUpdate
produces:             # 새로 만들거나 바꾸는 인터페이스 (필수)
  - POST /meetings/{id}/join
status: pending       # pending | ready | in_progress | review | merged | blocked
---

## 목표
(한두 문장)

## 구현 범위
- 할 것
- 하지 말 것

## 참고할 기존 패턴
(파일 경로)

## 관련 spec 발췌
(필요한 부분만 붙여넣기. spec 전체를 링크하지 않는다)
