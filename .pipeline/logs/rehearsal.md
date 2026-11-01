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
