---
id: T-DUMMY-MEDIUM
title: Dummy medium routing
tier: medium
effort_override: null
depends_on: []
requirements:
- REQ-DUMMY
acceptance_tests:
- AT-DUMMY
touches:
- .pipeline/tests/fixtures/medium-result.txt
consumes: []
produces:
- dummy-medium-ok
status: pending
---

## 목표

dummy-medium-ok 반환 라우팅 검증

## 구현 범위

- 파일 변경·탐색·git 작업 금지
- 실제 머지 금지

## 관련 spec 발췌

REQ-DUMMY: 정확한 완료 신호 1회 및 종료 후 idle
