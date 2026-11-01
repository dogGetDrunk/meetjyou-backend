# 멀티에이전트 개발 파이프라인 명세

> 이 문서는 Claude Pro + ChatGPT Plus + Orca로 구성하는 **자동화된 개발 파이프라인**의 명세다.
> 읽는 사람(Claude Code)은 §12 "구축 과제"에 따라 이 파이프라인을 저장소에 세팅한다.
> 결정된 사항은 그대로 따르고, §13 "미결 사항·검증 필요"에 있는 항목은 임의로 정하지 말고 확인하거나 보고한다.

---

## 1. 목적과 범위

- 사람(민석)의 역할은 **메인 설계 에이전트와 문제·의도를 정의하고 intent.md를 승인하는 것까지**다.
- 그 이후의 요구사항 설계, 구현 계획, 태스크 분배, 구현, 테스트, 리뷰, 머지, 마일스톤 검증은 **모두 에이전트가 자동으로** 수행한다.
- 사람에게 다시 올라오는 경우는 다음 두 가지뿐이다.
  1. **intent 자체를 바꿔야 하는 상황** (결정 게이트)
  2. 최초 1회 `agents.yaml` 기본값 승인

## 2. 설계 원칙

1. **교차 검증:** 코드 리뷰는 항상 구현자와 **다른 진영**(Claude ↔ OpenAI)이 한다. 같은 모델끼리는 사각지대를 공유하기 때문이다.
2. **검증은 테스트로:** LLM 리뷰는 의견일 뿐 검증이 아니다. 요구사항 충족 여부는 수용 기준에서 만든 테스트로 판정한다.
3. **결정적인 작업에는 LLM을 쓰지 않는다:** 테스트 실행, 빌드, 린트는 스크립트로 처리한다.
4. **최소 컨텍스트:** 에이전트에게는 전체 문서가 아니라 역할에 필요한 문서만 넘긴다. 구현자에게는 티켓 카드만 준다. 문서는 많이 쓰는 것이 아니라 짧고 역할별로 쪼개는 것이 목표다.
5. **한도 분산:** Claude Pro와 ChatGPT Plus는 각각 5시간 한도와 주간 한도가 있다. Claude Pro 한도는 claude.ai 채팅과 Claude Code가 함께 쓴다. 한쪽 진영에 일이 몰리지 않게 배정한다.
6. **되돌릴 수 없는 행동 금지:** §10 가드레일 참조.

## 3. 작업 크기별 경로

메인 설계 에이전트가 intent 단계에서 작업 크기를 정한다.

| 크기 | 예 | 경로 |
|---|---|---|
| **S** | 버그 수정, 작은 변경 | 티켓 1개 → 구현 → 게이트 → 교차 리뷰 → (등급별 머지 규칙) |
| **M** | 기능 하나 | 간략 spec → 티켓 분할 → 이하 전체 흐름 |
| **L** | 새 기능군 | §5 전체 파이프라인 |

## 4. 역할과 모델 배정

| 역할 | 진영 | 모델 ID | 기본 effort |
|---|---|---|---|
| 메인 설계 (intent, spec, spec 변경 권한, 상 등급 머지 판단, 마일스톤 검증) | Claude | `claude-opus-5-5` | high |
| 보조 설계 (spec 비평, 구현 계획·티켓 작성, 수용 테스트 케이스 정의, escalation 1차 판단, 중 등급 머지 판단) | OpenAI | `gpt-6.1-sol` | high (머지 판단은 medium) |
| 수용 테스트 코드 작성 | OpenAI | `gpt-6.1-terra` | medium |
| 상 등급 구현 | Claude | `claude-sonnet-5-5` | high |
| 중 등급 구현 (선착순) | OpenAI / Claude | `gpt-6.1-terra` / `claude-sonnet-5-5` | medium |
| 하 등급 구현 (선착순) | Claude / OpenAI | `claude-haiku-5-5` / `gpt-6-luna` | low |
| 상 등급 코드 리뷰 | OpenAI | `gpt-6.1-sol` | high |
| 중 등급 코드 리뷰 | 반대 진영 | Terra가 짠 코드 → `claude-sonnet-5-5` / Sonnet이 짠 코드 → `gpt-6.1-terra` | medium |
| 하 등급 코드 리뷰 | 반대 진영 | Haiku가 짠 코드 → `gpt-6-luna` / Luna가 짠 코드 → `claude-haiku-5-5` | low |
| 테스트·빌드·린트 실행 | — | **LLM 없음** (스크립트) | — |
| 코디네이터 루프 | — | **Orca 런타임** (`orca orchestration run`) | — |

**리뷰어 배정 규칙:** 반대 진영의 같은 등급 모델이 리뷰한다. 단, 상 등급 코드는 `gpt-6.1-sol`이 리뷰한다.

**머지 판단 규칙**

| 등급 | 머지 판단자 |
|---|---|
| 상 | `claude-opus-5-5` (메인 설계) |
| 중 | `gpt-6.1-sol` (보조 설계) |
| 하 | **생략**. 게이트 통과 + 교차 리뷰 승인이면 머지. 마일스톤 검증 때 표본 감사로 보완 |

## 5. 파이프라인 흐름

```
[사람+메인설계] intent.md 작성 → 사람 승인
      ↓
[메인설계] spec.md 작성 (요구사항, 수용 기준, 인터페이스)
      ↓
[보조설계] spec 비평 ⇄ [메인설계] 수용/기각 결정 (최대 2라운드, decisions.md에 기록)
      ↓
[보조설계] 티켓 DAG 작성 + 티켓별 등급·effort 지정 + 수용 테스트 케이스(Given/When/Then) 정의
      ↓
[Terra] 수용 테스트 코드 작성 (구현보다 먼저, 별도 세션)
      ↓
[Orca 코디네이터] ready 상태 티켓을 구현 에이전트에 분배 (티켓 풀, 아래 방향 픽업만 허용)
      ↓
[구현 에이전트] 구현 → [스크립트] 테스트·빌드·린트 게이트
      ↓
[반대 진영 리뷰어] 교차 리뷰 (최대 3라운드)
      ↓
[등급별 머지 판단자] 머지 판단 (하 등급은 생략)
      ↓
[메인설계] 마일스톤마다 spec 대비 최종 검증 + 하 티켓 표본 감사 + effort 기본값 조정
```

### 5.1 intent.md

- 메인 설계 에이전트가 사람과 대화하며 작성하고, 사람이 승인한다.
- **사람과의 계약 문서다.** 승인 이후에는 에이전트가 수정할 수 없다. 바꿔야 하면 결정 게이트를 연다(§9).
- 내용: 해결할 문제, 의도, 범위와 범위 밖, 성공 기준, 작업 크기(S/M/L).

### 5.2 spec.md

- 작성자는 메인 설계 에이전트이고, **최종 결정권도 메인 설계 에이전트에게 있다.**
- 필수 포함 항목:
  - 요구사항마다 ID와 **수용 기준(acceptance criteria)**
  - 인터페이스 확정: API 시그니처, DTO, DB 스키마. 병렬 구현 시 의미 충돌을 막기 위해 구현 전에 확정한다.
- spec 변경은 메인 설계 에이전트만 할 수 있고, 변경할 때마다 `spec-changelog.md`에 기록한다.

### 5.3 spec 비평

- 보조 설계 에이전트가 비평하고, 메인 설계 에이전트가 항목마다 "수용 / 기각 + 근거"를 `decisions.md`에 남긴다.
- 최대 2라운드까지만 진행한다.

### 5.4 구현 계획과 티켓

- 담당자는 **보조 설계 에이전트로 고정**한다.
- 이유: (a) Claude 한도 분산. (b) spec을 쓰지 않은 쪽이 spec만 보고 계획을 세우다 막히는 지점이 곧 spec이 모호한 곳이라, spec 품질 검사 역할을 겸한다.
- spec에 없는 설계 결정이 필요해지면 보조 설계자가 직접 정하지 말고 메인 설계 에이전트에게 올린다.
- 티켓은 **결합도 기준**으로 나눈다. 서로 의존하지 않는 티켓만 병렬로 돌리고, 의존하는 티켓은 DAG로 순서를 건다.
- 같은 모듈의 하 등급 작업 여러 개는 **하나의 티켓으로 묶는다** (티켓당 고정 비용 절감).
- 티켓 형식은 §7.

### 5.5 수용 테스트

- 보조 설계자가 **무엇을 테스트할지**(Given/When/Then)를 정하고, Terra가 테스트 코드로 옮긴다.
- 테스트 코드는 **구현보다 먼저, 구현과 다른 세션에서** 작성한다.
- **구현 에이전트는 수용 테스트를 수정할 수 없다.** 테스트가 잘못됐다고 판단되면 escalation으로 보고만 한다.

### 5.6 구현과 티켓 풀

- 동시 구현 에이전트는 **2개로 시작**하고, 운영 로그를 보고 조정한다.
- 구현이 끝난 에이전트는 남은 ready 티켓 중 하나를 가져간다.
- **등급은 아래 방향으로만 픽업**할 수 있다. 상 담당은 상·중·하를, 중 담당은 중·하를, 하 담당은 하만 가져간다.
- 상 등급 티켓은 `claude-sonnet-5-5` 전용이다.

### 5.7 게이트 (테스트·빌드·린트)

- 스크립트로 실행한다. 예: `./gradlew test build` + 린트.
- 통과해야 리뷰로 넘어간다.
- 실패하면 로그를 원 구현 에이전트에게 그대로 돌려준다.

### 5.8 교차 리뷰

- 리뷰 지적은 **blocking과 nit으로 구분**한다. blocking만 다음 라운드를 만든다.
- 지적이 나오면 **원 구현 에이전트가 수정**하고, 다시 게이트를 거쳐 재리뷰한다.
- **최대 3라운드**까지 진행한다. 3라운드 후에도 blocking이 남으면:
  - 상·중 등급: 머지 판단자가 둘 중 하나를 결정한다.
    - **머지 + 후속 티켓 생성** (비차단 성격일 때)
    - **반려 + spec 또는 티켓 수정** (해석 충돌일 때)
  - 하 등급: 중 등급으로 승급한다(§8).
- 리뷰어에게 넘기는 입력: diff + 티켓 카드 + 관련 수용 기준. 저장소 전체를 넘기지 않는다.

### 5.9 머지 판단

- 상·중 등급만 해당한다(§4 표).
- 입력: **상세 diff**, 게이트 결과, 충족한 요구사항 ID 목록, 영향받는 엔드포인트와 스키마 변경 요약.
- 요약은 검증할 수 있는 항목으로만 구성한다. 구현 에이전트의 자기 평가 문장은 판단 근거로 쓰지 않는다.

### 5.10 마일스톤 검증

메인 설계 에이전트가 수행한다.
1. spec 대비 구현을 최종 검증한다.
2. **하 등급 머지 중 일부를 표본으로 뽑아 감사**한다. 문제가 나온 유형은 분류 기준에서 하 대상에서 뺀다.
3. 승급 기록과 재시도 기록을 근거로 **분류 기준과 effort 기본값을 조정**하고, 변경 이유를 기록한다.

## 6. 난이도 등급 분류 기준

보조 설계 에이전트가 티켓을 만들 때 적용한다.

| 기준 | 하 (기계적) | 상 |
|---|---|---|
| 판단 여지 | spec과 티켓만으로 구현이 사실상 하나로 정해짐 | spec에 없는 설계 선택이 필요함 |
| 선례 | 코드베이스에 따라 할 기존 패턴이 있음 | 이 프로젝트에서 처음 하는 종류의 작업 |
| 파급 범위 | 모듈 하나, 공유 인터페이스 안 건드림 | 여러 레이어, 공유 DTO/API/스키마 변경 |
| 상태·동시성 | 무상태 변환/조회 | 트랜잭션 경계, 락, 멱등성, 캐시 무효화 |
| 검증 가능성 | 수용 테스트로 정답 여부가 완전히 판별됨 | 테스트로 잡기 어려움 (경쟁 조건, 성능, 보안) |
| 실패 비용 | 틀려도 다음 티켓에서 고치면 됨 | 인증/권한, 데이터 마이그레이션, 삭제 |

**판정 규칙:** 상 조건이 하나라도 걸리면 **상**, 하 조건을 전부 만족하면 **하**, 나머지는 **중**.

**예시 (Kotlin/Spring/JPA 기준)**
- 하: 기존 패턴을 따르는 조회 API 추가, DTO ↔ 엔티티 매핑, 엔티티 필드 추가와 Flyway 스크립트, 설정값 추가, 예외 메시지 정리
- 중: 검증 로직이 있는 새 도메인 CRUD, QueryDSL 동적 검색과 페이징, 인터페이스가 정해진 외부 API 클라이언트
- 상: 선착순 정원 제어(동시성), 인증/인가, 여러 트랜잭션에 걸친 이벤트 처리, 스키마 변경과 기존 데이터 이전

## 7. 티켓 템플릿

`.pipeline/tickets/T-<번호>.md`

```markdown
---
id: T-012
title: 모임 참가 신청 API
tier: high            # high | medium | low
effort_override: null # 기본값 대비 ±1단계까지만. 예: +1, -1
depends_on: [T-009, T-010]
requirements: [REQ-4, REQ-5]
acceptance_tests: [AT-4-1, AT-4-2, AT-5-1]
touches:              # 건드릴 모듈/파일 (의존성 판단 근거, 필수)
  - meeting/application/JoinService.kt
  - meeting/api/JoinController.kt
consumes:             # 사용하는 인터페이스 (필수)
  - MeetingRepository.findByIdForUpdate
produces:             # 새로 만들거나 바꾸는 인터페이스 (필수)
  - POST /meetings/{id}/join
status: pending       # pending | ready | in_progress | review | merged | blocked
---

## 목표
(한두 문장)

## 구현 범위
- 할 것
- 하지 말 것

## 참고할 기존 패턴
(파일 경로)

## 관련 spec 발췌
(필요한 부분만 붙여넣기. spec 전체를 링크하지 않는다)
```

`touches`, `consumes`, `produces`는 필수 필드다. 이 세 필드가 있어야 코드베이스를 탐색하지 않고도 의존성을 기계적으로 계산할 수 있다.

## 8. 실패 처리: effort 상향과 등급 승급

**순서: 같은 모델에서 effort를 먼저 올리고, 그래도 안 되면 등급을 올린다.** 같은 모델로 재시도하면 프롬프트 캐시를 재사용할 수 있어서, 모델을 바꿔 컨텍스트를 처음부터 다시 읽히는 것보다 싸다.

| 조건 | 동작 |
|---|---|
| 테스트 실패 또는 blocking 리뷰 지적이 2회 누적 | 같은 모델에서 effort +1단계로 재시도 |
| 상향된 effort에서 다시 2회 실패 | 등급 승급: 하 → 중 → 상 |
| 하 등급 리뷰가 3라운드에 도달 | 중 등급으로 승급 |
| 상 등급이 상향(xhigh → max) 후에도 실패 | 메인 설계 에이전트에게 escalation (spec이나 티켓 재검토) |

- 승급된 티켓은 `logs/promotions.md`에 기록한다. 마일스톤마다 분류 기준을 보정하는 근거로 쓴다.
- 등급이 바뀌면 리뷰어와 머지 판단자도 새 등급 규칙을 따른다.

## 9. effort 정책

| 층 | 결정 주체 | 범위 | 변경 시점 |
|---|---|---|---|
| ① 기본값 | `agents.yaml`. 최초 1회 사람 승인 | 역할·등급별 | 마일스톤마다 메인 설계 에이전트가 로그 근거로 조정, 이유 기록 |
| ② 티켓별 조정 | 보조 설계 에이전트 | 기본값 ±1단계 | 티켓 생성 시 |
| ③ 자동 상향 | 규칙 (§8) | 실패한 티켓 | 실행 중 |

- **`max`는 상 등급의 자동 상향에서만 허용**한다. 기본값과 티켓별 조정으로는 쓸 수 없다.
- 에이전트가 기본값을 수시로 바꾸지 못하게 하는 이유: 에이전트 입장에서는 effort를 올리는 것이 항상 통과에 유리하고 그 비용은 한도가 치르기 때문에, 자유롭게 두면 전부 높은 쪽으로 쏠린다.
- 첫 마일스톤에서 재검토할 항목:
  - 상 구현을 처음부터 xhigh로 시작할지 (high에서 재시도가 잦으면)
  - 하 리뷰를 medium으로 올릴지 (표본 감사에서 놓친 결함이 자주 나오면)

## 10. 가드레일 (에이전트 권한에서 제외)

- 운영 환경 배포
- 실제(운영·공유) DB에 마이그레이션 실행. 로컬과 테스트 DB만 허용
- `git push --force`, 보호 브랜치 직접 push
- 시크릿·자격증명 파일 접근
- 수용 테스트 파일 수정 (구현 에이전트 한정)
- intent.md 수정 (모든 에이전트)

기본 브랜치는 보호하고, 게이트 통과를 머지의 필수 조건으로 건다.

## 11. Orca 오케스트레이션 운영

- 사용하는 Orca 기능: 태스크 DAG, `dispatch --inject`, `worker_done` / `escalation` / `decision_gate` 메시지, `orca orchestration run` 코디네이터 루프. 의존 대상이 모두 완료된 태스크는 런타임이 자동으로 ready로 올린다.
- 각 구현 에이전트는 자기 worktree에서 작업한다.
- **알려진 약점:** Orca는 완료 신호(`worker_done`)를 프롬프트로 주입하기 때문에, 워커가 신호를 빠뜨리면 코디네이터가 멈출 수 있다. 다음 장치를 반드시 둔다.
  - 디스패치마다 타임아웃
  - heartbeat 확인
  - 타임아웃이 나고 heartbeat도 없는 워커의 티켓은 다른 워커에게 재분배
- 결정 게이트(`decision_gate`)는 intent 변경이 필요할 때만 사람에게 연다.
- escalation 라우팅:
  - 구현과 리뷰 문제 → 보조 설계
  - spec 문제 → 메인 설계
  - intent 문제 → 사람

## 12. 구축 과제 (Claude Code가 할 일)

### 12.1 디렉터리 구조

```
.pipeline/
  agents.yaml            # 역할 → 모델, effort 기본값, 진영
  intent.md              # (템플릿)
  spec.md                # (템플릿: 요구사항 ID, 수용 기준, 인터페이스 섹션)
  spec-changelog.md
  decisions.md           # spec 비평 수용/기각 로그
  tickets/               # T-xxx.md (§7 템플릿)
  tests/cases/           # Given/When/Then 케이스 정의
  prompts/roles/         # 역할별 지시서
    main-designer.md
    sub-designer.md
    test-writer.md
    implementer.md
    reviewer.md
    merge-judge.md
  logs/
    promotions.md
    retries.md
    usage.md             # 역할별 한도 소모 기록
scripts/
  gate.sh                # 테스트·빌드·린트. 종료 코드로 통과/실패
  dispatch.sh            # 티켓 등급·작성자 → 모델·effort·리뷰어 결정 후 Orca로 디스패치
CLAUDE.md / AGENTS.md    # 진영별 공통 규칙 (가드레일, 수용 테스트 수정 금지 등)
```

### 12.2 agents.yaml 초안

```yaml
roles:
  main_designer:  { vendor: claude, model: claude-opus-5-5,   effort: high }
  sub_designer:   { vendor: openai, model: gpt-6.1-sol,       effort: high }
  test_writer:    { vendor: openai, model: gpt-6.1-terra,     effort: medium }

implement:
  high:   [{ vendor: claude, model: claude-sonnet-5-5, effort: high }]
  medium: [{ vendor: openai, model: gpt-6.1-terra,     effort: medium },
           { vendor: claude, model: claude-sonnet-5-5, effort: medium }]
  low:    [{ vendor: claude, model: claude-haiku-5-5,  effort: low },
           { vendor: openai, model: gpt-6-luna,        effort: low }]

review:            # key = 구현한 모델
  high:
    claude-sonnet-5-5: { model: gpt-6.1-sol,       effort: high }
  medium:
    gpt-6.1-terra:     { model: claude-sonnet-5-5, effort: medium }
    claude-sonnet-5-5: { model: gpt-6.1-terra,     effort: medium }
  low:
    claude-haiku-5-5:  { model: gpt-6-luna,        effort: low }
    gpt-6-luna:        { model: claude-haiku-5-5,  effort: low }

merge_judge:
  high:   { model: claude-opus-5-5, effort: high }
  medium: { model: gpt-6.1-sol,     effort: medium }
  low:    null     # 생략

limits:
  concurrent_implementers: 2
  spec_critique_rounds: 2
  review_rounds: 3
  failures_before_effort_up: 2
  failures_before_promotion: 2
  max_effort_only_on_escalation: true
  ticket_effort_override_range: 1
```

### 12.3 역할별 지시서에 반드시 들어갈 내용

- **공통:** 받은 문서 외의 파일을 탐색하지 않는다(필요하면 escalation). `worker_done`은 실패해도 정확히 1번, task ID와 dispatch ID를 포함해서 보낸다. 결과 요약은 정해진 형식과 길이 상한을 지킨다.
- **implementer:** 수용 테스트 수정 금지. 티켓의 "하지 말 것" 범위 준수. 완료 시 충족한 요구사항 ID 목록과 변경 요약을 제출한다.
- **reviewer:** 지적마다 blocking/nit을 표시한다. 같은 지적이 반복되면 그 사실을 명시한다.
- **merge-judge:** §5.9 입력만으로 판단한다. 3라운드 후 남은 지적은 "머지+후속 티켓" 또는 "반려+수정" 중 하나로 결론 낸다.
- **sub-designer:** §6 분류 기준과 §7 템플릿 필수 필드를 지킨다. spec에 없는 설계 결정은 메인 설계로 올린다.
- **main-designer:** intent.md 수정 금지. spec 변경 시 changelog 기록. 마일스톤 시 §5.10 수행.

### 12.4 구축 완료 기준

- [ ] 디렉터리 구조와 모든 템플릿 생성
- [ ] `scripts/gate.sh`가 성공·실패를 종료 코드로 구분
- [ ] `scripts/dispatch.sh`가 티켓의 등급과 구현 모델로부터 구현 모델, 리뷰어, 머지 판단자, effort를 `agents.yaml` 기준으로 결정
- [ ] 아래 방향 픽업 규칙과 승급 규칙(§8)이 dispatch 로직에 반영됨
- [ ] 더미 티켓 3개(상·중·하 각 1개)로 리허설해서 디스패치 → 게이트 → 교차 리뷰 → 머지 규칙이 의도대로 동작함을 확인
- [ ] 타임아웃·heartbeat·재분배 동작 확인

## 13. 미결 사항·검증 필요 (임의로 정하지 말 것)

| 항목 | 상태 |
|---|---|
| **한도 소진 시 동작** — Claude Pro나 ChatGPT Plus가 5시간 한도에 걸렸을 때 "대기 후 재개"로 할지 "반대 진영으로 우회"로 할지 | **사람 결정 필요.** 결정 전까지는 대기 후 재개를 기본으로 한다. 우회를 쓰게 되면 해당 티켓에 "교차 리뷰 미완" 표시를 남기고 나중에 보충한다 |
| Codex CLI에서 reasoning effort를 지정하는 정확한 설정 키와 모델별 지원 단계 (`gpt-6.1-sol`, `gpt-6.1-terra`, `gpt-6-luna`) | 공식 문서나 `codex --help`로 확인 |
| Claude Code에서 모델별로 지원하는 `--effort` 단계 (`claude-sonnet-5-5`, `claude-haiku-5-5`, `claude-opus-5-5`) | 확인. Haiku 5.5는 Claude Code v2.1.293 이상 필요 |
| Pro/Plus 모델 선택 목록에 위 모델들이 실제로 노출되는지 | `/model`로 확인 |
| Orca orchestration CLI의 정확한 명령과 옵션 | Orca 공식 문서(onorca.dev/docs/cli/orchestration)로 확인 |
| 역할별 실제 한도 소모량 | 운영 첫 1~2주 동안 `/usage`(Claude Code), `/status`(Codex)로 측정해서 `logs/usage.md`에 기록. 모델 배정과 effort 기본값 보정의 근거 |

## 14. 운영하며 조정할 값 (현재는 시작값)

- 동시 구현 에이전트 수 (2)
- effort 기본값 전체 (§9)
- 분류 기준의 경계 (§6). 승급 기록 기반
- 한쪽 진영 한도가 먼저 바닥날 때 조정 순서
  - Claude 쪽이 먼저 소진되면: 중 등급 리뷰를 Haiku로 내리는 것을 검토
  - Codex 쪽이 먼저 소진되면: 보조 설계자의 머지 판단 effort를 낮추는 것을 검토
