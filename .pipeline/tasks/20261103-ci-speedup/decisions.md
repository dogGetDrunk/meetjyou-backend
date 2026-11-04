# Spec 비평 결정

| 라운드 (최대 2) | 항목 | 수용/기각 | 근거 | 메인 설계자 |
|---|---|---|---|---|
| r1 | C-r1-01 | 수용 | spec의 `src/` 밖 `*.md` 허용이 intent 제외 경로(`tools/`·`scripts/`·워크플로)의 `.md`까지 생략 대상으로 넓힘 → 허용 목록을 `docs/**`·`.pipeline/tasks/**`·루트 `*.md`·`.claude/rules/**/*.md`로 좁히고 `tools/`·`scripts/`·`.github/` 아래 `.md` → `false` 사례 추가 | Claude (메인 설계) |
| r1 | C-r1-02 | 수용 | rename 시 이전 경로 누락 위험 실재 → 판정 스크립트가 `git diff --no-renames`로 직접 수집(삭제·추가 경로 모두), 임시 Git 저장소 rename 사례를 AT-001에 추가 | Claude (메인 설계) |
| r1 | C-r1-03 | 수용 | 생략 조건을 "PR 이벤트 + 판정 step 성공 + 출력 정확히 `true`"로 명시, 그 외 전부 전체 빌드. 스크립트가 diff 실패·빈 목록을 `false`로 처리하고 잘못된 ref 사례를 AT-001로 검증. 일부 기각: 워크플로 실패 주입 실행표 테스트는 일회용 PR이 필요해 비용 대비 이득 낮음 → 실제 코드 PR·문서 PR 실행 로그 2건으로 대체 | Claude (메인 설계) |
| r1 | C-r1-04 | 수용 | 코드 확인 결과 6개 스펙이 `SELECT COUNT(*) FROM performance_schema.data_lock_waits`를 서버 전역으로 셈, digest 테스트도 서버 전역. → 락 대기 수는 `data_locks.OBJECT_SCHEMA` 조인으로 자기 database만 세는 공유 헬퍼로 교체, digest 조회는 `SCHEMA_NAME` 조건 추가, AT-002에 "다른 database의 대기는 0, 자기 database 대기는 검출" 사례 추가. `SHOW ENGINE INNODB STATUS`는 로그 진단용(단언 없음)이라 유지. 좁힐 수 없는 관찰이 나오면 단언 완화 대신 escalation | Claude (메인 설계) |
| r1 | C-r1-05 | 수용 | 측정 정의 확정: job 시간 = `build-and-test` job startedAt→completedAt(큐 제외), dockerTest 시간 = Gradle 로그 `> Task :dockerTest` 줄부터 다음 `> Task` 줄까지, 표본 = T-02 포함 base 위의 성공한 코드 변경 실행 3건(run ID·head SHA 기록), 3건 미확보면 미검증으로 기록 | Claude (메인 설계) |
