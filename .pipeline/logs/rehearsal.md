# 2026-11-01 Orca 리허설

- Run: `run_114e92e0ebbd`
- 범위: 파일 수정 없는 dummy 구현·교차 리뷰·머지 적격성 판단
- 실제 PR/merge·서비스 수용 테스트의 E2E 검증 아님
- 구현 워커: 별도 child worktree, `origin/main`, setup skip
- 모델·effort: worker-start receipt의 requested/effective 일치 확인

| 경로 | 모델 / effort | Dispatch | 결과 |
|---|---|---|---|
| 하 구현 | claude-haiku-5-5 / low | ctx_1ab73ee77e8e | dummy-low-ok |
| 중 구현 | gpt-6.1-terra / medium | ctx_f68e9def750d | dummy-medium-ok |
| 상 구현 | claude-sonnet-5-5 / high | ctx_af768d5fe70d | dummy-high-ok |
| 하 교차 리뷰 | gpt-6-luna / low | ctx_4b0cc49cc3f5 | approved |
| 중 교차 리뷰 | claude-sonnet-5-5 / medium | ctx_3d3e8371957e | approved |
| 상 교차 리뷰 | gpt-6.1-sol / high | ctx_8ad81c2cb628 | approved |
| 중 머지 판단 | gpt-6.1-sol / medium | ctx_e4225cc4467e | approve |
| 상 머지 판단 | claude-opus-5-5 / high | ctx_6a385e44737a | approve |
| 의도적 실패 | claude-haiku-5-5 / low | ctx_8064af52ff7d | failed (예상 결과) |
| 실패 후 다른 워커 재배정 | gpt-6-luna / low | ctx_5c3c8aff00cc | failed (동일 의도적 실패 과제, 예상 결과) |

- 하 티켓: 판단자 생략 정책 테스트 확인, 실제 merge 없음
- 재배정: 기존 failed 결과 확인 및 release(프로세스 종료) 후 동일 `task_a22c30117466`에 `--retry-of` 적용 확인
- timeout/heartbeat 부재만으로 재배정 금지: 단위 테스트 확인, 실제 장애 주입 E2E 미수행
- `ctx_8ad81c2cb628`의 부가 medium 판단(high effort)은 정식 검증에서 제외, 별도 medium effort 판단으로 재검증

## 실제 부트스트랩 코드 교차 리뷰

- 1차 `ctx_e015289048cc`: claim 재시도·다중 Run 중복 실행 blocking, 수정
- 2차 `ctx_5736e68a5859`: retry 소유권·unknown reservation·capacity/overlap·경로 정규화 blocking, 수정
- 3차 `ctx_973f4fde3364`: 4개 blocking 해소 승인, unknown 상태의 수동 조사 필요성은 fail-closed 한계로 기록
- `workerState`(worker-list)와 `worker.state`(worker-show): 실제 설치 CLI receipt에서 각각 확인, 스키마 차이 정상
- main 보호 확인: build-and-test 필수, strict=true, enforce_admins=true

## 자동 실행 루프 검증

- 구현 `ctx_07106f514a02`: 결정적 상태 전이 및 초기 테스트 작성
- 교차 리뷰 `ctx_aa8284e30b48`: crash window·gate SHA 귀속·claim 해제·리뷰 SHA·mutation journal blocking 6건, 회귀 테스트 후 수정
- 최종 검토 `ctx_780e03281b2a`: blocking 해소 승인
- 실제 초기화: coordinator --once → events=[], config_approved=true, idle=true
- commit SHA별 CI: PR #236 필수 build-and-test 적용; 최종 커밋 CI 결과는 PR 참조
- 알려진 보수적 한계: 실행 전 확실한 거부라도 unknown reservation이 남을 수 있음, receipt 확인 후 에이전트가 복구; 자동 삭제·무조건 재시도 금지
