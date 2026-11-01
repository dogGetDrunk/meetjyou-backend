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
- [x] agents.yaml 최초 사람 승인

## 2. 결정적 게이트

- [x] `scripts/gate.sh`: 정책 테스트 후 `./gradlew test build`, 실패 즉시 종료
- [x] 전체 Gradle test/build 통과 확인 (UP-TO-DATE 캐시 사용)
- [x] CLAUDE.md와 .pipeline/AGENTS.md 가드레일 반영, 역할 프롬프트에 CLAUDE.md 명시 제공; 사용자 root AGENTS.md 보존

## 3. Orca 실행·리허설

- [x] 원문 기본 모델·effort 실제 라우팅 확인; 자동 상향 단계 provider 지원은 운영 확인 항목
- [x] Run 생성, 로컬 티켓 DAG ready 분배·동시성 2 제한
- [x] task/dispatch ID·외부 mutation journal·공유 claim 영속 기록
- [x] gate 실패 환류, 교차 리뷰·등급별 판단·CI 머지 전이
- [x] timeout/heartbeat 조사·종료 확인 후 재분배 로직 및 단위 테스트
- [x] dummy 상·중·하 실제 역할 라우팅, 실패·release 후 재분배 증거 기록 (빈 diff, 실제 서비스 E2E와 구분)
- [x] 전체 로컬 게이트·자체 리뷰·교차 리뷰 및 기반 커밋·푸시·PR 생성
- 최종 커밋 CI·머지 증거: PR #236 checks 및 merge 상태 참조
