# ADR-0001: `GET /api/v1/posts` N+1 쿼리 해소 방식

**Status:** Accepted
**Date:** 2026-07-10
**Deciders:** damiannlee

## Context

k6 부하 테스트(0→100 VU 램프업)에서 `GET /api/v1/posts`가 부하 시 가장 느린 엔드포인트(요청당 500ms+)로 확인됨. HikariCP `maximum-pool-size`를 5→20으로 올려 먼저 시도했으나 개선되지 않았고(오히려 p95가 908ms→1.12s로 소폭 악화), 처리량도 거의 그대로였음 — 이는 커넥션 풀 경합이 원인이 아니라는 강한 반증이었음.

코드 조사 결과 실제 원인은 두 가지였음:

1. `Post.author`, `Post.party`, `Post.plan`이 fetch 타입 명시 없는 `@ManyToOne`(JPA 기본값 EAGER)이라, `PostRepository.findAll(pageable)` 같은 리스트 조회 시 Hibernate가 각 연관관계를 **row당 별도 SELECT**로 채움(페이지당 최대 31쿼리).
2. `PostService.getAllPosts`/`getPostByAuthorUuid`에서 `loadPostContextMaps(...)`(comp_preference/marker/user_party/view_count를 배치 조회하는 헬퍼)가 `posts.map { }` **람다 안에서** 호출되고 있어, 원래 "페이지당 1번"이어야 할 배치 쿼리가 "게시글 개수만큼" 반복 실행됨. 이건 리뷰 중 우연히 발견한, 계획에 없던 별도 버그였음.

## Decision

두 문제를 다음과 같이 해결함:

1. **Hibernate `default_batch_fetch_size` 전역 설정 + `Post`의 세 연관관계를 `FetchType.LAZY`로 전환.**
2. `resolvePlanResponse`가 `planRepository`를 재조회하지 않고 이미 로드된 `post.plan`을 재사용하도록 변경.
3. `loadPostContextMaps` 호출을 `.map { }` 밖으로 이동해 페이지당 1회만 실행되도록 수정.
4. `post.created_at`에 인덱스 추가(Flyway `V27`).

실측 결과: 페이지당 쿼리 수 38→9(76% 감소), 100 VU 부하에서 p95 지연시간 908ms→21.64ms(약 42배), iteration 처리량 3,986→6,032(51% 증가).

## Options Considered

### 문제 1: N+1 해소 방식

#### Option A: `default_batch_fetch_size` 전역 설정 + LAZY 전환 (채택)

| 항목 | 평가 |
|---|---|
| 코드 변경량 | 낮음 (설정 1줄 + fetch 타입 3곳) |
| 위험도 | 낮음 (모든 접근 지점이 `@Transactional` 내부임을 grep으로 확인) |
| 효과 범위 | 앱 전체 (Post 외 다른 엔티티의 잠재적 N+1도 예방) |
| 성능 | 페이지당 쿼리 수를 `1 + 소수의 배치(IN절) 쿼리`로 수렴 |

**Pros:** Hibernate 표준 기능, 변경 범위가 작아 리뷰/롤백이 쉬움, 기존 `Party.plan`의 LAZY 관례와 일치.
**Cons:** 여전히 N+1이 아닌 "1+k" 형태(테이블 종류 수만큼)라 완전한 단일 쿼리는 아님. 전역 설정이라 다른 도메인의 지연 로딩 동작에도 영향을 미침(다만 이는 일반적으로 유익한 방향의 영향).

#### Option B: `Post.author`/`party`/`plan`에 개별 `@BatchSize` 어노테이션

**Pros:** 영향 범위가 `Post`로 한정되어 blast radius가 작음.
**Cons:** 다른 엔티티(예: `Plan.owner`도 EAGER 기본값)의 잠재적 N+1은 그대로 방치됨. 실제로 이번 조사에서 `Plan.owner`가 EAGER인 채로도 문제가 되지 않은 건 우연히 `Post.plan` 배치 조회에 편승해 JOIN으로 같이 실려왔기 때문 — 전역 설정이 이런 우연에 의존하지 않게 해줌.

#### Option C: `PostRepository`에 `JOIN FETCH` 커스텀 쿼리 작성

```kotlin
@Query(
    value = "SELECT p FROM Post p JOIN FETCH p.author JOIN FETCH p.party LEFT JOIN FETCH p.plan",
    countQuery = "SELECT COUNT(p) FROM Post p",
)
fun findAllWithAssociations(pageable: Pageable): Page<Post>
```

**Pros:** 이론적으로 가장 빠름 — 단일 SQL JOIN으로 페이지당 정확히 1쿼리.
**Cons:** `Page` + `JOIN FETCH` 조합은 count 쿼리를 별도로 명시해야 하는 등 Spring Data JPA의 알려진 함정이 있고, `findAll`/`findAllByAuthor_Uuid` 두 메서드 모두 다시 작성해야 해서 변경 범위가 큼. 단일 쿼리의 이득 대비 유지보수 비용이 이번 데이터 규모(페이지당 10~20건)에서는 배치 쿼리 방식과 체감 차이가 크지 않다고 판단.

#### Option D: DTO 프로젝션(JPQL constructor expression 또는 native query)으로 엔티티 로딩 자체를 우회

**Pros:** 엔티티/연관관계를 아예 안 거치므로 이론적 최댓값의 성능.
**Cons:** `CompanionSpec`, `GetPlanResponse`, `myApplicationStatus` 등 응답 조립 로직이 이미 `PostService`에 상당히 복잡하게 얽혀 있어(마커, 선호도, 신청 상태) 프로젝션으로 옮기면 이 서비스 레이어 전체를 다시 설계해야 함. 지금 병목 대비 과잉 엔지니어링으로 판단해 기각.

**선택 이유:** Option A가 변경 범위 대비 효과가 가장 크고 위험이 가장 낮음. Option C/D는 더 나은 이론적 한계를 제공하지만 이번 실측(9쿼리, p95 21ms)만으로도 임계치를 초과 달성했기 때문에 추가 복잡도를 정당화하기 어려움.

### 문제 2: `post.created_at` 인덱스

#### Option A: 단순 오름차순 인덱스 (채택)
```sql
CREATE INDEX idx_post_created_at ON post (created_at);
```
**Pros:** `getAllPosts`/`getAllByAuthor` 모두 상태/필터 조건 없이 정렬만 하므로 단일 컬럼으로 충분. InnoDB가 역방향 스캔으로 `ORDER BY ... DESC`도 처리 가능.
**Cons:** 없음 — 데이터가 커질수록 이득이 커지는 순수 이득 항목.

#### Option B: 복합 인덱스 `(status, created_at)`
**Cons:** 현재 두 조회 메서드 모두 `status` 필터가 없어 복합 인덱스의 이점이 없음 — 불필요한 인덱스 유지 비용만 늘어남. 향후 상태 필터가 추가되면 그때 재검토.

### (참고) 커넥션 풀 확장 — 기각된 첫 가설
HikariCP `maximum-pool-size`를 5→20으로 올려 먼저 검증했으나, 같은 100 VU 테스트에서 p95가 오히려 908ms→1.12s로 악화되고 처리량도 늘지 않아 기각. 병목이 "DB 커넥션 대기"가 아니라 "요청당 쿼리 수"였음을 반증하는 근거로 활용.

## Trade-off Analysis

| 기준 | Option A (배치 fetch, 채택) | Option C (JOIN FETCH) | Option D (DTO 프로젝션) |
|---|---|---|---|
| 페이지당 쿼리 수 | ~9 (실측) | ~1 | ~1 |
| 코드 변경 범위 | 작음 | 중간 (리포지토리 재작성) | 큼 (서비스 레이어 재설계) |
| 롤백 난이도 | 쉬움 | 중간 | 어려움 |
| 앱 전체 파급 효과 | 있음 (다른 엔티티도 혜택) | 없음 (이 쿼리만) | 없음 |
| 실측 p95 | 21.64ms | (미검증, 이론상 더 낮을 수 있음) | (미검증) |

실측 p95(21.64ms)가 이미 임계치(800ms)를 40배 가까이 여유 있게 통과했으므로, 이론적으로 더 빠른 Option C/D를 추가 검증하는 비용이 정당화되지 않는다고 결론.

## Consequences

- **쉬워지는 것:** 게시글 목록/작성자별 목록 조회가 트래픽 증가에 훨씬 안정적으로 대응. 향후 다른 엔티티에 유사한 EAGER 기본값 실수가 있어도 `default_batch_fetch_size`가 안전망 역할을 함.
- **어려워지는 것:** 전역 배치 설정이라, 특정 쿼리에서 의도적으로 즉시 실패시키고 싶은 lazy-loading 관련 테스트(있다면)는 동작이 달라질 수 있음 — 현재 테스트 스위트(208개)는 모두 통과 확인.
- **재검토가 필요한 시점:** 게시글 수가 수만~수십만 건 규모로 커지면 (a) `V27` 인덱스의 효과가 본격적으로 드러날 것이고, (b) 페이지당 9쿼리도 부담이 될 수 있어 그때는 Option C(JOIN FETCH) 또는 Option D(DTO 프로젝션)로의 전환을 재검토할 것.

## Action Items

1. [x] `application.yml`에 `hibernate.default_batch_fetch_size: 100` 추가
2. [x] `Post.author`/`party`/`plan`을 `FetchType.LAZY`로 전환
3. [x] `resolvePlanResponse`의 중복 `planRepository.findByUuid` 재조회 제거
4. [x] `loadPostContextMaps` 호출을 `.map { }` 밖으로 이동 (`getAllPosts`, `getPostByAuthorUuid`)
5. [x] `V27__add_post_created_at_index.sql` 마이그레이션 추가
6. [x] `GetAllPostsPlanResolutionTest` 회귀 테스트 추가
7. [x] `./gradlew test` 전체 208개 통과 확인
8. [x] k6 100 VU 부하 테스트로 p95 908ms→21.64ms 개선 실측
9. [ ] 게시글 수가 수만 건 이상으로 늘어나면 Option C(JOIN FETCH)/D(DTO 프로젝션) 재검토
