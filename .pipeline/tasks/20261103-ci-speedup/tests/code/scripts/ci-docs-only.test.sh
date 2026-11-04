#!/usr/bin/env bash
# AT-001: exercise the classifier against independent real Git histories.
set -u
classifier="$(pwd)/scripts/ci-docs-only.sh"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
failures=0

write_file() {
  mkdir -p "$(dirname "$1")"
  printf '%s\n' "content for $1" > "$1"
}

run_case() (
  name=$1 expected=$2
  mkdir "$tmp/$name" && cd "$tmp/$name" || exit 1
  git init -q
  git config user.name 'CI Test'
  git config user.email 'ci-test@example.invalid'
  write_file docs/a.md
  write_file .pipeline/tasks/x/intent.md
  write_file README.md
  write_file .claude/rules/party.md
  write_file src/main/kotlin/A.kt
  if [ "$name" = rename_docs_to_code ]; then rm src/main/kotlin/A.kt; fi
  git add . && git commit -qm base
  base=$(git rev-parse HEAD)
  case "$name" in
    docs_allowlist|mixed_code)
      for path in docs/a.md .pipeline/tasks/x/intent.md README.md .claude/rules/party.md; do printf 'change\n' >> "$path"; done
      if [ "$name" = mixed_code ]; then printf 'change\n' >> src/main/kotlin/A.kt; fi ;;
    source_markdown) write_file src/main/resources/notes.md ;;
    tools_markdown) write_file tools/agent-pipeline/GUIDE.md ;;
    scripts_markdown) write_file scripts/notes.md ;;
    workflow_markdown) write_file .github/workflows/README.md ;;
    detekt_config) write_file config/detekt/baseline.xml ;;
    build_config) write_file build.gradle.kts ;;
    pipeline_config) write_file .pipeline/project.yaml ;;
    rename_code_to_docs) mv src/main/kotlin/A.kt docs/A.md ;;
    rename_docs_to_code) mv docs/a.md src/main/kotlin/A.kt ;;
    rename_docs_to_docs) mv docs/a.md docs/b.md ;;
    delete_code) rm src/main/kotlin/A.kt ;;
    empty_diff|invalid_ref) ;;
  esac
  if [ "$name" != empty_diff ] && [ "$name" != invalid_ref ]; then
    git add -A && git commit -qm change || exit 1
  fi
  head=$(git rev-parse HEAD)
  if [ "$name" = invalid_ref ]; then base=refs/heads/nonexistent-test-ref; fi
  bash "$classifier" "$base" "$head" > "$tmp/$name.out" 2> "$tmp/$name.err"
  status=$?
  printf '%s\n' "$expected" > "$tmp/$name.expected"
  if [ "$status" -ne 0 ] || ! cmp -s "$tmp/$name.expected" "$tmp/$name.out"; then
    printf 'FAIL %s (exit %s, stdout: %s)\n' "$name" "$status" "$(cat "$tmp/$name.out")" >&2
    exit 1
  fi
)

while read -r name expected; do
  if ! run_case "$name" "$expected"; then failures=$((failures + 1)); fi
done <<'CASES'
docs_allowlist true
mixed_code false
source_markdown false
tools_markdown false
scripts_markdown false
workflow_markdown false
detekt_config false
build_config false
pipeline_config false
rename_code_to_docs false
rename_docs_to_code false
rename_docs_to_docs true
delete_code false
empty_diff false
invalid_ref false
CASES
if [ "$failures" -ne 0 ]; then exit 1; fi
printf 'AT-001: all 15 cases passed\n'
