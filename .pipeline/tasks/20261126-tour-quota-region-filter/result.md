# 결과

## 결과 요약

- #296 관광공사 하루 한도 초과(코드 22) 뒤 min(다음 KST 자정, +1시간)까지 호출 중지, 중지 중 검색은 외부 왕복과 로그 없이 `tourStatus=FAILED`
- #297 일반 시군구 필터(217개)에서 `lDongSignguCd` 를 함께 전달해 관광지 묶음이 그 시군구 안에서 결과를 채움
- 응답 형식, 스키마 변경 없음

## 변경 내용

- `TourApiException.Reason.QUOTA_EXCEEDED` 추가, `TourApiClient` 가 게이트웨이 XML `returnReasonCode=22` 와 JSON `resultCode=22` 를 이 사유로 던짐 (다른 코드는 `RESULT_CODE` 유지)
- `TourQuotaPause`(새 클래스): `AtomicReference<Instant?>` 중지 시각, `Clock` 주입
- `TourPlaceSearch`
  - 비활성 → `DISABLED`, 중지 중 → `FAILED`, 그 밖 → 호출 (`when` 분기)
  - `QUOTA_EXCEEDED` 수신 시 중지 시작과 warn 1줄(`resumeAt` 포함), 다른 실패는 지금처럼 warn 후 다음 검색에서 재호출
  - `areaOf`: SIGUNGU이고 부모가 시도이고 하위 지역이 없으면 시군구 코드 뒤 3자리 전달, 그 밖은 시도만
  - 결과 재필터(`isWithin`) 유지
- `TourPlaceSource.searchKeyword` 에 `signguCode` 파라미터 추가, `RegionCatalog.hasChildren` 추가
- `.claude/rules/place.md` 관광공사 절에 중지 규칙과 시군구 전달 규칙 추가
- 기존 테스트 변경: `TourApiClientTest` 의 "게이트웨이 XML 22 → `RESULT_CODE`" 기대를 `QUOTA_EXCEEDED` 로 변경 (이번 태스크의 의도된 동작 변경)

## 비평 결정

- S 태스크, 사람 결정으로 생략 (2026-11-26 "승인. 비평, 검증 생략")

## 구현 후 검증

- S 태스크, 사람 결정으로 생략 (위와 같음)

## gate 근거

- `agent-pipeline gate` (`./gradlew test build`, detekt 포함) exit 0, `BUILD SUCCESSFUL in 2m 16s`
- 테스트 876개, 실패 0, 건너뜀 0 (`build/test-results/test` 집계)
- 첫 두 번의 gate는 detekt `ReturnCount`, `TooManyFunctions` 로 실패 → `when` 분기와 `TourQuotaPause` 분리로 해결 (baseline 추가 없음)

## 핵심 결정과 기각한 대안

- 중지 기간 = min(다음 KST 자정, +1시간)
  - 기각: 자정까지만 중지(이슈 제안) — 리셋 시각 미확인, 리셋이 자정보다 늦으면 자정 직후 22를 다시 받고 하루를 잃음
  - 기각: 고정 1시간만 — 자정 리셋이 맞으면 최대 1시간 늦게 복구
  - 비용: 자정 전 시간당 1회, 최대 약 23회 헛호출
- 시군구 코드 전달은 일반 시군구 217개로 한정
  - 기각: 일반구 39개와 일반구를 둔 시 13개까지 전달 — 관광공사가 이들을 어떤 코드로 내려주는지 미확인, 틀리면 0건으로 지금보다 나빠짐
- 중지 상태는 프로세스 메모리 (앱 인스턴스 1대, 응답을 저장하지 않으므로 ADR-0004 캐싱 금지와 충돌 없음)
  - 기각: 응답에 한도 초과 전용 `tourStatus` 추가 — 앱 계약 변경
  - 기각: 유저별 제한과 응답 캐시 — 문제 관측 전, ADR-0004
- detekt 위반은 억제(`@Suppress`)나 baseline 대신 구조로 해결

## 남은 후속 작업

- (사람) 실제 서비스키로 `searchKeyword2` 에 `lDongSignguCd` 를 넣은 호출 1회 확인: 파라미터가 적용되는지, 일반구 지역(예: 수원 장안구)이 `111` 로 오는지 `110` 으로 오는지. 확인되면 일반구 전달 범위 확장 판단
- (사람) 런칭 전 관광공사 운영 계정 신청 (노션 244 안건)
- 운영 배포 뒤 warn 로그 `Tour search paused` 발생 빈도 관찰 (중지 1회당 1줄)
