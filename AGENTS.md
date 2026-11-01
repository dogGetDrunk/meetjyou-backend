# AGENTS.md

- 대상: 이 저장소에서 작업하는 Codex 및 AGENTS.md를 읽는 에이전트
- 작업 시작 전 [CLAUDE.md](CLAUDE.md) 전체 필수 확인·준수 — 프로젝트 개요, Git 흐름, 코딩 컨벤션, 검증, 아키텍처, 파이프라인 안전 규칙의 단일 원본
- 모듈별 상세 규칙: `.claude/rules/*.md`의 `paths:`와 변경 대상 경로를 대조해 해당 문서 필수 확인 — Codex의 자동 로딩 가정 금지
- 프로젝트 파이프라인 사용법·설정: [.pipeline/README.md](.pipeline/README.md), [.pipeline/project.yaml](.pipeline/project.yaml)
- 공통 규칙 변경 위치: `CLAUDE.md`; 이 파일에 동일 규칙 복제 금지
