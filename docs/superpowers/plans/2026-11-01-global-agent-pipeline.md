# 전역 에이전트 파이프라인 분리 계획

- 목표: 승인된 전역 엔진·모델 정책과 프로젝트별 상태·검증 설정 분리
- 승인 근거: 사용자 「그렇게 분리해줘」, 직전 제안의 전역 공통/프로젝트별 경계
- 구조: `tools/agent-pipeline` 독립 배포 소스 → `~/.local/share/agent-pipeline` 설치본 → `~/.local/bin/agent-pipeline` 진입점
- 프로젝트 소유: `.pipeline/project.yaml`, intent·spec·티켓·수용 테스트·로그·intent 승인
- 전역 소유: 엔진·역할 지시서·기본 모델·모델 승인·검증 테스트·초기화 템플릿
- 제약: 기존 모델 승인 그대로 유지, intent 승인 프로젝트 간 복사 금지, 종료 확인 전 재분배 금지

## 실행 및 검증 순서

- [x] 공통 자산 이동, 현재 디렉터리 Git 루트와 설치 자산 경로 분리
- [x] `project.yaml`의 gate argv·remote/base branch·merge method 적용
- [x] `agent-pipeline init/dispatch/coordinator/watch/gate/policy/self-test` CLI 및 전역 설치기 구현
- [x] 중복 초기화 거부·독립 프로젝트 초기화·비 Gradle gate·정책 변경·기존 복구 회귀 테스트
- [x] CI 소스 경로 변경, Meetjyou 얇은 호환 스크립트 및 문서 갱신
- [x] 전체 Python 테스트·Gradle test/build·설치본의 독립 저장소 smoke 실행
- [x] Codex/Claude 전역 지침에 파이프라인 기본 진입점 추가
- [ ] 커밋·push·PR·CI 확인·merge

## 선택 근거

- 복사 배포 기각: 프로젝트별 정책 drift·중복 유지보수 발생
- 별도 GitHub 저장소 생성 보류: 전역 런타임 분리에 불필요한 원격 저장소 관리 범위 확대
- 공통 소스의 현재 보관처만 Meetjyou, 설치본은 원본 checkout·Gradle·프로젝트 이름에 의존하지 않는 독립 실행 구조
- 검증 명령 자동 추정 기각: 무검증 성공 방지, 프로젝트 규칙의 실제 명령 명시
