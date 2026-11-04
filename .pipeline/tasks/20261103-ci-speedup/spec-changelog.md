# Spec 변경 로그

| 일시 | 요구사항 ID | 변경 | 이유 | 메인 설계자 |
|---|---|---|---|---|
| 2026-11-03 | REQ-001 | intent A의 생략 조건(제외 목록: src·빌드 설정·워크플로·tools·scripts)을 허용 목록(`docs/**`·`.pipeline/tasks/**`·`src/` 밖 `*.md`)으로 구체화 | 제외 목록에 `config/detekt/**` 등 빌드 영향 경로 누락, 새 경로 추가 시 빌드 생략 위험. 생략 집합이 intent보다 좁아지는 방향(더 보수적) | Claude (메인 설계) |
| 2026-11-03 | REQ-001 | 허용 목록을 `docs/**`·`.pipeline/tasks/**`·루트 `*.md`·`.claude/rules/**/*.md`로 축소, 판정 스크립트가 base·head를 받아 `--no-renames` diff까지 수행, 생략 조건·fail-safe 명시 | C-r1-01·02·03 | Claude (메인 설계) |
| 2026-11-03 | REQ-002 | 락 대기·digest 관찰을 자기 database로 한정하는 계약과 AT 사례 추가 | C-r1-04 | Claude (메인 설계) |
| 2026-11-03 | REQ-003 | 측정 시간 정의·표본 선택·미검증 처리 확정 | C-r1-05 | Claude (메인 설계) |
