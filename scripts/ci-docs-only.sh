#!/usr/bin/env bash
# Usage: scripts/ci-docs-only.sh <base> <head>
# Prints exactly one line, "true" or "false", and always exits 0.
# "true" only when every path changed between <base> and <head> is on the docs allowlist.
# Any doubt (bad ref, git failure, empty diff, path outside the allowlist) prints "false",
# so CI falls back to the full build.
set -u

DOCS_ONLY=true
NOT_DOCS_ONLY=false

is_allowed_path() {
  local path=$1
  case "$path" in
    docs/* | .pipeline/tasks/*) return 0 ;;
    # bash case "*" also matches "/", so this covers nested rule directories.
    .claude/rules/*.md) return 0 ;;
  esac
  # Markdown at the repository root only; nested *.md elsewhere may be build input.
  [[ "$path" != */* && "$path" == *.md ]]
}

classify() {
  local base=$1 head=$2 changed path count=0
  git rev-parse --verify --quiet "${base}^{commit}" > /dev/null || return 1
  git rev-parse --verify --quiet "${head}^{commit}" > /dev/null || return 1
  # --no-renames reports a move as delete + add, so a code file moved into docs/
  # still exposes its deleted code path. Unusual names stay quoted and never match.
  changed=$(git -c core.quotePath=false diff --no-renames --name-only "$base" "$head" --) || return 1
  while IFS= read -r path; do
    [[ -z "$path" ]] && continue
    is_allowed_path "$path" || return 1
    count=$((count + 1))
  done <<< "$changed"
  [[ "$count" -gt 0 ]]
}

main() {
  if [[ $# -ne 2 ]]; then
    echo "$NOT_DOCS_ONLY"
    return 0
  fi
  if classify "$1" "$2" 2> /dev/null; then
    echo "$DOCS_ONLY"
  else
    echo "$NOT_DOCS_ONLY"
  fi
  return 0
}

main "$@"
exit 0
