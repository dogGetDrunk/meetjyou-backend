# 자동 실행 루프 운영 계약

- 진입점: `agent-pipeline coordinator`, 명령은 `agent-pipeline guide` 참조
- 상태: queued → implementing → gating → reviewing → judging(하 생략) → merging → merged
- 실패 수정: needs_fix → implementing, blocking만 재리뷰 유발
- 중단: blocked / escalated / rejected, 결과의 대상·사유를 설계 에이전트가 처리; intent 변경만 사람 결정 게이트
- ready 조건: 명시적 depends_on 전부 merged, touches 비중첩, 저장소의 모든 worktree·Run 합산 동시 구현 최대 2
- 공유 claim: 구현 수정 때도 재예약, 응답 유실 시 unknown 유지, merge 이후 해제
- gate 중 crash: 검증 결과 불명으로 blocked; gate 전후 clean 및 동일 HEAD 확인
- 외부 디스패치·poll/release·PR ready·merge: mutation 전 pending journal, 다음 상태와 receipt 함께 저장
- JSON 검토 보고서: ticket·dispatch_id·head_sha·round 검증; gate SHA와 다르면 승인 거부
- 리뷰 최대 3라운드: 하 → 중 승급, 상·중 → merge_with_followup 또는 reject 판단
- merge_with_followup: 실제 후속 티켓 또는 완전한 followup_card 필수, 없는 티켓 ID만으로 머지 불가
- commit+push+draft PR 작성자: 구현자; PR 미존재·다중 PR·CI 누락/실패·SHA 불일치 시 머지 차단
- draft ready 전환 후 CI 재확인, `gh pr merge --{project.yaml의 merge_method} --match-head-commit SHA`
- 구현 실패/실행 timeout: 종료가 확인된 원 Task의 재시도, circuit breaker 우회 금지
- 수정·리뷰·판단은 원 worktree에서 새 세션, 동일 프로세스·provider 세션 재사용 보장은 없음
- 요구사항 설계·spec 비평·수용 테스트 작성은 ready 이전 단계, 각 역할 지시서 적용
- CLI 시작/idle 및 개별 역할 라우팅 실제 검증, 전체 서비스 변경 E2E는 별도 검증 필요
