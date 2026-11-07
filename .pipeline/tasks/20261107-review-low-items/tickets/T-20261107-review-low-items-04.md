---
id: T-20261107-review-low-items-04
title: 앱 버전 요약 캐시를 computeIfAbsent로 채워 stale 재삽입 차단
tier: high
effort_override: null
depends_on: []
requirements: [REQ-006]
acceptance_tests: [AT-006-1]
touches:
  - src/main/java/com/dogGetDrunk/meetjyou/version/AppVersionService.kt
consumes:
  - AppVersionRepository.findAllByPlatform
  - java.util.concurrent.ConcurrentHashMap.computeIfAbsent
produces:
  - 'AppVersionService.checkVersion: summaryCache.computeIfAbsent(platform) { buildSummary(it) }'
status: merged
---

## 목표

- 버전 쓰기 직후 `/check`가 새 값을 반환, 쓰기 전에 시작한 조회가 이전 요약을 캐시에 남기지 못함

## 구현 범위

- 할 것
  - `checkVersion`의 `summaryCache.getOrPut(platform) { buildSummary(platform) }` → `summaryCache.computeIfAbsent(platform) { buildSummary(it) }`
  - 짧은 영어 주석: 계산 중 `remove`가 bin 잠금에서 기다렸다 지우므로 쓰기 이전 값이 남지 않음, 매핑 함수 안에서 캐시 재수정 금지
- 하지 말 것
  - `checkVersion`에 `@Transactional` 추가 금지 (계산 전에 스냅샷 고정 → stale 재발)
  - 쓰기 메서드(`addVersion`·`updateVersion`·`setForceUpdate`·`setStoreReleased`·`deleteVersion`)의 트랜잭션 구조·`remove` 위치 변경 금지
  - `buildSummary` 안에서 `summaryCache` 접근 금지, 요약 계산의 DB 읽기를 매핑 함수 밖으로 빼지 않음
  - 세대 번호 방식 금지(spec 기각), `/check` 응답 형식·캐시 키 변경 금지
  - 기존 `AppVersionServiceTest` 수정 금지

## 참고할 기존 패턴

- `src/main/java/com/dogGetDrunk/meetjyou/version/AppVersionService.kt` — 현재 `summaryCache` 사용
- `src/test/java/com/dogGetDrunk/meetjyou/version/AppVersionServiceTest.kt` — 리포지토리 mock 구성

## 참조 파일 (읽기 허용)

- `src/main/java/com/dogGetDrunk/meetjyou/version/AppVersion.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/version/AppVersionRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/version/PlatformStoreUrlRepository.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/version/Platform.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/version/AppVersionDto.kt`
- `src/main/java/com/dogGetDrunk/meetjyou/version/dto/VersionCheckResponse.kt`
- `src/test/java/com/dogGetDrunk/meetjyou/version/AppVersionServiceTest.kt`

## 관련 spec 발췌

- REQ-006: 쓰기(`summaryCache.remove` 부르는 모든 메서드)가 끝난 뒤 캐시에 쓰기 이전 DB 값 요약이 남지 않아야 함. `getOrPut` → `computeIfAbsent` 고정(진행 중 계산이 끝날 때까지 `remove`가 대기), 키 = 플랫폼 2개라 bin 잠금 비용 무시. 매핑 함수 안에서 DB 읽기 전부 실행·맵 재수정 금지. `checkVersion` 트랜잭션 없음 확인·추가 금지. 쓰기 트랜잭션 구조 유지(C-r1-10)
- 수용: 조회 스레드의 요약 계산이 mock 안 latch로 멈춘 상태(이전 목록 반환 예정) → 쓰기 스레드가 쓰기 호출(끝나거나 `remove`에서 BLOCKED 확인) → latch 해제 → 다음 `checkVersion`이 새 목록 기준. 순차 결과 기존과 같음

## 분류 근거

- 캐시 무효화·스레드 경합(State/concurrency high) → high. 변경 자체는 한 줄 수준
- effort_override 없음

## 실행 계획·DAG

- 선행 없음, 다른 티켓과 병렬. version 패키지는 T-04만 수정

## 검증·증거 담당

- AT-006-1: `./gradlew test --tests "*AppVersionCacheStaleTest"` + 기존 `*AppVersionServiceTest`
- 구현 워커: 보호 테스트 불변, 최종 SHA에서 `./gradlew test build` 실제 출력 제출
