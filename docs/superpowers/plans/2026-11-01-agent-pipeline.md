# Agent pipeline 구축 계획

- 기준 사양: `.pipeline/agent-pipeline-spec.md` §12
- 실행 방식: 현재 세션 직접 구현
- 목표: intent 승인 이후 요구사항·수용 테스트·구현·검증·교차 리뷰·머지의 Orca 자동화
- 구조: Bash 진입점, Python 정책 판정, agents.yaml 배정 데이터, Orca runtime 실행 상태
- 변경 경계: `.pipeline/`, `scripts/`, 공통 에이전트 지침; 서비스 코드 제외

## 1. 오프라인 배정 정책

- [x] 원본 명세·agents.yaml·문서 템플릿·역할별 지시서 저장
- [x] `scripts/dispatch.sh` → `pipeline_dispatch.py` 배정 JSON 출력
- [x] 등급·모델·effort override·누적 실패·리뷰 횟수 검증
- [x] 하향 픽업·반대 진영 리뷰·판단자·승급 정책 테스트
- [x] 더미 티켓 3개 subprocess 검증
- [ ] agents.yaml 최초 사람 승인

## 2. 결정적 게이트

- [x] `scripts/gate.sh`: 정책 테스트 후 `./gradlew test build`, 실패 즉시 종료
- [x] 전체 Gradle test/build 통과 확인 (UP-TO-DATE 캐시 사용)
- [ ] 공통 CLAUDE.md/AGENTS.md에 가드레일 참조 반영 (기존 사용자 미추적 AGENTS.md 소유권 보존)

## 3. Orca 실행·리허설

- [ ] 계정별 모델·effort 지원 확인
- [ ] runtime Run/DAG 생성과 ready 분배·동시성 2 제한
- [ ] task/dispatch ID 영속 기록 및 exactly-once 완료 처리
- [ ] gate 실패를 원 구현자에게 환류, 교차 리뷰·등급별 머지 전이
- [ ] timeout/heartbeat 조사·종료 확인 후 재분배
- [ ] dummy 상·중·하 실제 E2E, 실패·재시도·재분배 증거 기록
- [ ] 전체 테스트·자체 리뷰 후 커밋·푸시·PR·CI 검증
