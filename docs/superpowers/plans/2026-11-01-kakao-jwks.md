# 카카오 JWKS 장애 내성 구현 계획

## 승인·범위
- 출처: 노션 #220, GitHub #179·#181, 사용자 스펙 카드 승인 “진행해”
- 카카오: 키 셋 전체 캐시, 갱신 실패 시 마지막 성공 조회 기준 24시간 허용, 30초당 최대 2회 조회
- 기존 RS256 서명·issuer·audience·만료·nonce 검증 유지
- 키 최초 조회 실패·장애 허용 기간 초과: 503, 미등록 kid·재조회 제한: 토큰 거절
- Google 커스텀 캐시 보류: 관측된 장애 영향 대비 SDK 검증·캐시 재구현 비용
- #181 세션 테이블 보류: 기기별 관리 요구·오탐 민원 부재, 기존 회전·폐기 동작 유지
- 운영 배포·DB 변경 제외

## 실행 예외
- 사용자 Claude 토큰 부족 설명 및 Codex 중심 실행 승인
- 이번 작업은 현재 Codex 세션에서 설계·수용 테스트·구현·검증 수행, 공통 모델 정책 변경 없음
- 자동 coordinator·교차 벤더 리뷰 미실행 사실을 완료 보고에 명시

## 선택 근거와 한계
- Apple 캐시를 공용 추상화로 리팩터링하는 대안 기각: 이번 변경과 무관한 Apple 인증 회귀 위험 증가
- Spring decoder 유지: 기존 claim 검증·예외 변환 보존, Nimbus 프로세서의 claim 검증만 Spring에 위임
- 장애 시 키 유지의 비용: 공급자가 철회한 키도 허용 기간 안에 로컬에 남을 수 있음, 토큰 서명·만료 검증은 계속 수행
- 30초 창에서 초기 조회와 추가 조회 총 2회 허용, 공격 중 새 키 회전은 다음 창까지 지연 가능
- main 머지 시 운영 자동 배포 발생: 이번 승인 범위의 운영 배포 제외에 따라 PR·CI까지 수행하고 머지 보류

## 구현 및 검증
- [x] KakaoOidcConfig의 실제 decoder에 로컬 HTTP JWKS 서버 연결 테스트 작성
- [x] 미등록 kid 반복 요청 시 기존 구현의 무제한 조회 재현
- [x] KakaoJwkSource에 JWKSourceBuilder 캐시·장애 허용·조회 제한 구성
- [x] DefaultJWTProcessor + RS256 JWSVerificationKeySelector로 NimbusJwtDecoder 구성
- [x] 장애 시 기존 키 사용·최초 실패·허용 초과·키 교체·재조회 제한 테스트
- [x] 서명·issuer·audience·만료 거절 및 정상 검증 확인
- [x] 전체 ./gradlew test build 및 자체 보안 리뷰
- [ ] PR·CI 확인

## 검증 증거
- RED: 기존 URI decoder로 전체 test 582개 중 1개 실패, 미등록 kid 조회 11회로 기준 2회 초과
- GREEN: JAVA_HOME=temurin-21 ./gradlew test build 성공, 테스트 582개·실패/오류/건너뜀 0개
- detekt 통과, 기존 서명·issuer·audience·만료·nonce 검증 및 provider 오류 매핑 테스트 포함
- 자체 리뷰: DB·트랜잭션·refresh/logout 흐름 변경 없음, 외부 HTTP 3초 timeout·50KiB 제한, RS256 유지
- 최초 --tests 실행은 기존 Kotest spec 필터 오류, 전체 테스트로 전환
- 테스트 헬퍼 LongParameterList 1건 수정 후 전체 빌드 재검증
