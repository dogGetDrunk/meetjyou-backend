#!/usr/bin/env bash
set -euo pipefail
SOURCE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 "$SOURCE/tools/agent-pipeline/scripts/pipeline_cli.py" self-test
cd "${1:-$SOURCE}"
exec python3 "$SOURCE/tools/agent-pipeline/scripts/pipeline_cli.py" gate
