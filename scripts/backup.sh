#!/usr/bin/env bash
# ==============================================================================
# SiteFlow Database Backup Script (Bash)
# Performs a logical backup of SiteFlow MySQL database using mysqldump.
# Configurable via environment variables or parameter arguments.
# ==============================================================================
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-siteflow}"
DB_USER="${DB_USER:-${DB_USERNAME:-siteflow_admin}}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_SSL_MODE="${DB_SSL_MODE:-PREFERRED}"
BACKUP_DIR="${BACKUP_DIR:-backups}"

mkdir -p "${BACKUP_DIR}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/siteflow_backup_${DB_NAME}_${TIMESTAMP}.sql"

echo "[BACKUP] Starting backup for database '${DB_NAME}' on ${DB_HOST}:${DB_PORT} (User: ${DB_USER}, SSL: ${DB_SSL_MODE})..."

if [ -n "${DB_PASSWORD}" ]; then
  export MYSQL_PWD="${DB_PASSWORD}"
fi

trap 'unset MYSQL_PWD || true' EXIT

mysqldump \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USER}" \
  --ssl-mode="${DB_SSL_MODE}" \
  --single-transaction \
  --quick \
  --routines \
  --triggers \
  --set-gtid-purged=OFF \
  --result-file="${BACKUP_FILE}" \
  "${DB_NAME}"

FILE_SIZE=$(wc -c < "${BACKUP_FILE}" | tr -d ' ')
echo "[BACKUP SUCCESS] Backup created: ${BACKUP_FILE} (${FILE_SIZE} bytes)"
