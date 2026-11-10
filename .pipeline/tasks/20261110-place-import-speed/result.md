# 결과

## 결과 요약

- 스테이징 배치를 다중 행 INSERT로 전송: `application.yml` 드라이버 옵션 `rewriteBatchedStatements: true`
  - 로컬 MySQL: 1,000행 스테이징 시 INSERT 실행 1,000 → 1
- 적재 종료(성공·실패) 시 `place_import_staging` TRUNCATE, 비우기 실패는 경고 로그만
- 운영 재적재 실측(11/10, 배포 `fd71517` 후): 전체 2시간 57분 → **10분 26초**, 스테이징 2시간 34분 → **3분 53초**(목표 15분 이하 달성)

## 머지된 PR

| 티켓 | PR | 등급 | 구현 / 리뷰 모델 | 라운드 | 토큰 |
|---|---|---|---|---|---|
| single | (이 PR) | S | Opus 5.5 (메인 세션) | 비평 생략 | - |

## 핵심 결정과 기각한 대안

- `decisions.md` 참조. 요약
  - 옵션은 application.yml에 — 기각: 운영 DB URL 환경 변수(레포·테스트에 안 보임)
  - 회귀 감지는 단일 연결 `Com_insert` — 기각: digest 집계(공유 서버 간섭), 배치 반환값(드라이버 세부)
  - 끝 비우기 실패는 결과 유지 — 기각: FAILED 전환(반영은 이미 끝남)

## spec 대비 검증

- REQ-001: red 확인(옵션 없음 → `expected:<1L> but was:<1000L>`), 옵션 추가 후 green
- REQ-002: `PlaceDb`가 앱 드라이버 옵션 사용, `./gradlew dockerTest` 175건 통과(skipped 0, failures 0)
- REQ-003: red 확인(적재 후 스테이징 3·5·6행 잔류) → green. 끝 비우기 실패 시 SUCCEEDED·성공 알림·`clearStaging` 2회(`PlaceImportApiIntegrationTest`)
- gate `agent-pipeline gate`(`./gradlew test build`): `BUILD SUCCESSFUL`, exit 0, 단위·통합 806건 통과(skipped 0)

## 운영 재적재 실측 (2026-11-10)

- intent "결정 필요" 항목: 사람 결정 "실측"(세션 내 발언) → 배포 후 같은 분기 파일 재적재
- 실행: 관리자 API `POST /api/v1/admin/places/imports`, 운영 관리자 계정 `opsadmin`(id 15) 토큰을 EC2 안에서 JWT 키로 발급(키·토큰 미출력, 실측 후 토큰 파일 삭제)
- 결과: `SUCCEEDED`, 파일 `소상공인시장진흥공단_상가(상권)정보_20260930` — 읽음 2,753,778 / 스테이징 1,023,599 / 제외 1,730,179 / 신규·갱신·재매칭·비활성화 0(같은 파일, AT-003대로 변화 없음)

| 단계 (앱 로그 시각, UTC) | 11/9 (PR #281) | 11/10 (이 PR) |
|---|---|---|
| 다운로드+스테이징 (`started` → `staged`) | 2시간 34분 | **3분 53초** (12:12:07 → 12:16:00) |
| 반영 (`staged` → `finishedAt`) | 23분 | 6분 33초 (→ 12:22:33) |
| 끝 스테이징 비우기 (`finishedAt` → `succeeded` 로그) | 없음 | 약 8초 (→ 12:22:41) |
| 전체 | 2시간 57분 | **10분 26초** |

- 반영 시간은 비교 불가: 11/9는 102만 행 신규 INSERT, 11/10은 변경 0건(UPDATE·INSERT 조건에서 모두 걸러짐)
- 스테이징 비우기: 적재 전 102만 3,599행 잔류(배포 전 실행분) → 적재 후 0행, `place_import_staging.ibd` 약 375MB(데이터 183MB + 인덱스 192MB) → 144KB
  - `information_schema.tables` 크기는 통계 캐시라 TRUNCATE 직후에도 옛 값 → 파일 크기로 확인
- 자원(1분 간격): 앱 메모리 522 → 최대 544MiB(한도 1.5GiB, 11/9 최대 595MiB), CPU 최대 28%(스테이징 중), EC2 가용 메모리 최저 738MB — 서비스 영향 없음
- `place` 1,023,599행 전부 활성 유지

## 남은 후속 작업

- 반영 단계 실측은 실제 변경이 있는 다음 분기 파일에서(11/9 신규 적재 23분이 상한 참고값)
- 운영 DB 계정 최소 권한 분리 시 TRUNCATE에 DROP 권한 필요(기존 후속 그대로)
