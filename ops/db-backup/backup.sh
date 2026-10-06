#!/usr/bin/env bash
# Daily logical backup of the production `meetjyou` schema (issue #142).
# Runs on the app host (EC2) so the copy lives outside OCI, where the DB runs.
set -euo pipefail
# Dumps contain user data; keep them readable by root only.
umask 077

CNF_FILE="${BACKUP_CNF_FILE:-/etc/meetjyou-backup/my.cnf}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/meetjyou}"
KEEP_COUNT="${BACKUP_KEEP_COUNT:-7}"
MYSQL_IMAGE="mysql:8.0.41"
SCHEMA="meetjyou"
DUMP_COMPLETED_MARKER="-- Dump completed"

notify_failure() {
  local exit_code=$?
  rm -f "${TMP_FILE:-}"
  echo "backup failed with exit code ${exit_code}" >&2
  if [[ -n "${DISCORD_WEBHOOK_URL:-}" ]]; then
    curl -fsS -m 10 -H "Content-Type: application/json" \
      -d "{\"content\":\"[db-backup] ${SCHEMA} backup FAILED on $(hostname) (exit ${exit_code})\"}" \
      "${DISCORD_WEBHOOK_URL}" >/dev/null || echo "discord notification failed" >&2
  fi
  exit "${exit_code}"
}
trap notify_failure ERR

mkdir -p "${BACKUP_DIR}"
FINAL_FILE="${BACKUP_DIR}/${SCHEMA}-$(date +%Y%m%d-%H%M).sql.gz"
TMP_FILE="${FINAL_FILE}.tmp"

# pipefail makes a mysqldump failure fail the pipeline; without it gzip's success would
# leave an empty archive that looks like a valid backup.
# --user 0 lets the container read the root-owned 600 credentials file.
docker run --rm --user 0 \
  ${BACKUP_DOCKER_NETWORK:+--network "${BACKUP_DOCKER_NETWORK}"} \
  -v "${CNF_FILE}:/backup.cnf:ro" "${MYSQL_IMAGE}" \
  mysqldump --defaults-extra-file=/backup.cnf \
    --single-transaction --no-tablespaces --set-gtid-purged=OFF "${SCHEMA}" \
  | gzip > "${TMP_FILE}"

# A truncated dump can still exit 0 in edge cases; the trailer proves mysqldump finished.
gzip -dc "${TMP_FILE}" | tail -n 1 | grep -q -- "${DUMP_COMPLETED_MARKER}"
mv "${TMP_FILE}" "${FINAL_FILE}"

# Count-based retention: age-based deletion would keep removing good backups while new
# runs fail, ending with none left. File names embed a sortable timestamp, newest first.
printf '%s\n' "${BACKUP_DIR}/${SCHEMA}"-*.sql.gz | sort -r | tail -n "+$((KEEP_COUNT + 1))" \
  | while read -r old_backup; do rm -f "${old_backup}"; done

echo "backup written: ${FINAL_FILE} ($(wc -c < "${FINAL_FILE}") bytes)"
