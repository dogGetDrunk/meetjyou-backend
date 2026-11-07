# AT-001: 수용 테스트 케이스

- 요구사항: REQ-001
- Given:
- When:
- Then:
- 작성자: 보조 설계
- 코드 작성자: 별도 세션의 Terra
- 기준 실행: red  <!-- red = base에서 실패(기본) | keep = 유지할 동작, base에서 통과 | no-compile = base에 없는 인터페이스 사용, 실행 제외. project.yaml test_red가 있으면 check-tests가 검사 -->
- 보호 대상 테스트 파일:
  - `src/test/<경로>/ExampleTest.kt`

<!-- 보호 대상 테스트 파일: 소스는 태스크 폴더 tests/code/<경로>. 엔진이 구현 워커에 복사 명령을 주고 gate 전 sha256 바이트 일치를 검사 -->
