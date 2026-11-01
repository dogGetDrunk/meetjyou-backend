#!/usr/bin/env bash
set -euo pipefail
SOURCE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -x "$HOME/.local/bin/agent-pipeline" ]]; then
  exec "$HOME/.local/bin/agent-pipeline" dispatch "$@"
fi
exec python3 "$SOURCE/tools/agent-pipeline/scripts/pipeline_cli.py" dispatch "$@"
