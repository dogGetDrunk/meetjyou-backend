# 결과

> 태스크 종료 시 메인 설계 에이전트가 작성. 포트폴리오·회고 재료.

## 결과 요약

- **완료** (2026-11-05) — 티켓 4개 전부 머지, 이슈 #225·#228 해결
- 경과: 2026-11-04 1차 시도 엔진 결함으로 취소 → 엔진 수정(#41~#44) 후 재착수 → 진행 중 엔진 결함 4건 추가 발견(#50·#51·#53·#55), 3건 수정·1건 우회 → 머지
- 재착수 시 1차 구현 커밋 재사용(T-01~03), T-04만 신규 구현

## 머지된 PR

| 티켓 | PR | 등급 | 구현 / 리뷰·판정 모델 | 구현 배정(누적) | triage |
|---|---|---|---|---|---|
| T-01 채팅방 안 읽은 수 단일 집계 | #260 (`3fb7194`) | high | claude-opus-5-5 / gpt-6.1-sol | 9 (+extra 4) | 2 |
| T-02 게시글 상세 커넥션 1개 | #261 (`fa85a77`) | high | claude-opus-5-5 / gpt-6.1-sol | 9 (+extra 4) | 2 |
| T-03 브로드캐스트 일괄 조회·실패 처리·dedup | #262 (`9063f0a`) | high | claude-opus-5-5 / gpt-6.1-sol | 10 (+extra 4) | 2 |
| T-04 재발송 API·MySQL 경쟁 테스트 | #264 (`edec972`) | high | claude-opus-5-5 / gpt-6.1-sol | 3 | 2 |

- 리뷰 라운드: T-01~03 1라운드 승인, T-04 2라운드(CI 실패 수정 후 재리뷰) 승인
- judge: 4개 모두 `merge`, 후속 티켓 없음
- 수용 테스트: gpt-6.1-terra 작성 12개 파일, spec 비평 1라운드(C-r1-01~08, 수용 6·기각 2), 구현자 지시서 점검 1라운드(B-r1-01·02 수용)
- 관련 PR: intent #255, 보호 테스트 detekt 검사 `test_lint` #263

## 핵심 결정과 기각한 대안

- D1 브로드캐스트 = 단일 REQUIRES_NEW 트랜잭션 유지 + 실패 알림 + 재발송 API
  - 기각: 청크별 독립 트랜잭션 — 부분 발송 상태·"빠진 사람만" 재발송 로직 필요
- D2 운영 부하 테스트 = 배포 후 사람 작업 (범위 제외)
- D3 재발송 API = 이번 태스크에서 구현
- 중복 발송 방지 = 원천 row `PESSIMISTIC_WRITE` + dedup 접두 존재 확인 + 비유니크 인덱스 V43
  - 기각: `dedup_key` 유니크 인덱스 — 운영 기존 중복 row가 있으면 migration 실패로 기동 차단
- 약관 발송 진입점 = `broadcastOnPublish(UUID)`·`resend(UUID)` 두 public 메서드 (B-r1-01)
  - 기각: 단일 `broadcast(UUID, validateApplicability)` flag — 보호 테스트 계약과 불일치, 호환 메서드 추가 필요
- `TermsService.publishTerms` 기본 `effectiveAt` = `Instant.now().truncatedTo(SECONDS)` (T-04 triage)
  - 원인: `terms.effective_at` `TIMESTAMP`(소수 초 없음) + MySQL 반올림 → 게시 직후 최대 0.5초 미래로 저장, 재발송 적격성 검사가 간헐 409 `TERMS_RECONSENT_NOT_APPLICABLE`
  - 기각: 재발송의 적격성·dedup 검사 순서 변경 — 티켓 계약이 잠금 직후 적격성 판정을 고정
- 1차 취소 vs 엔진 우회(사람 직접 리뷰·머지) — 취소 후 엔진 수정·재착수 선택, 이번에도 엔진 결함은 우회 대신 수정

## 보호 테스트 수정 (설계 쪽, 단언 불변)

- 1차: detekt 62건, `@DirtiesContext` 제거, AT-002-3 spy 실제 호출 위임
- 재착수: AT-005-3·4 `race` 헬퍼 분리(30줄 상한), AT-005-1 Kotest 6 `extensions` 등록 방식, AT-005-2 forEach `it` 가림 — 상세 spec-changelog.md
- 교훈: T-04 보호 테스트는 대상 API 부재로 설계 때 detekt만 통과, 컴파일 미검증 → 구현 첫 배정에서 컴파일 오류로 막힘

## 엔진 결함 (damiannlee/agent-pipeline)

| 이슈 | 증상 | 처리 |
|---|---|---|
| #50 | 재시도가 최초 Orca task 지시서 재사용 → 낡은 보호 테스트 hash로 워커 자기 차단 | PR #52 수정 |
| #51 | `ci_state`가 같은 체크의 옛 실패 실행까지 집계 | 미수정, 옛 run `gh run rerun` 후 `unblock --resume` 우회 |
| #53 | strict 브랜치 보호에서 BEHIND PR 머지 거부 | PR #54 수정 — `gh pr update-branch` 후 base-only 머지 커밋만 인정 |
| #55 | judge가 첫 배정 때의 produces로 판정해 정상 구현 reject, rejected는 복구·알림 경로 없음 | PR #57 수정 — 매 배정 계약 재로딩, `unblock --resume` 재판정 |

- `--extra-runs N`은 누적 상한에 더해짐(남은 횟수 기준 아님) — T-01·02는 4, T-03은 4 필요
- 감시 스크립트가 rejected를 종료 조건에서 빠뜨려 T-03 reject 후 약 7시간 정지 — 이후 rejected·정체 90분 감지 추가

## spec 대비 검증

- 4개 PR 모두 CI `./gradlew build dockerTest` 통과 후 coordinator가 머지 (gate `./gradlew test build`, separate-session 리뷰, judge)
- T-04 CI 1차 실패(AT-005-4 dockerTest)는 구현 버그로 판정·수정, 같은 테스트 재실행 통과
- T-04 보호 테스트 컴파일 수정은 triage가 `compileTestKotlin compileDockerTestKotlin detekt` 및 해당 테스트 6개로 확인

## 남은 후속 작업

- 엔진 #51 수정
- `ChatAccessRevocationIntegrationTest` cleanup flake (T-01 triage가 발견, 이번 범위 밖)
- D2 운영 부하 테스트 (배포 후 사람 작업)
- 노션 보드 태스크 상태 변경 — 사람 확인 후
