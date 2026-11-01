#!/usr/bin/env bash
set -euo pipefail
SOURCE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="${1:-$SOURCE}"
python3 -m unittest discover -s "$SOURCE/.pipeline/tests" -p 'test_*.py'
cd "$ROOT"
# build includes detekt, Konsist conventions, and the skipped-test guard.
./gradlew test build
