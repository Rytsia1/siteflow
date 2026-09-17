#!/usr/bin/env bash
# ==============================================================================
# SiteFlow Database Restore Script (Bash)
# Restores a SiteFlow MySQL database from a backup file using mysql client.
# Supports restoring into an isolated/clean test database.
# ==============================================================================
set -euo pipefail

BACKUP_FILE="${1:-}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
TARGET_DB="${DB_NAME:-siteflow}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-backups}"
CLEAN_DB="${CLEAN_DB:-false}"

if [ -z "${BACKUP_FILE}" ]; then
  # Pick the latest .sql in BACKUP_DIR
  BACKUP_FILE=$(ls -t "${BACKUP_DIR}"/*.sql 2>/dev/null | head -n 1 || true)
fi

if [ -z "${BACKUP_FILE}" ] || [ ! -f "${BACKUP_FILE}" ]; then
  echo "[RESTORE FAILED] Backup file not found: '${BACKUP_FILE}'" >&2
  exit 1
fi

echo "[RESTORE] Target Database: '${TARGET_DB}' on ${DB_HOST}:${DB_PORT}"
echo "[RESTORE] Source Backup  : '${BACKUP_FILE}'"

if [ -n "${DB_PASSWORD}" ]; then
  export MYSQL_PWD="${DB_PASSWORD}"
fi

trap 'unset MYSQL_PWD || true' EXIT

if [ "${CLEAN_DB}" = "true" ]; then
  echo "[RESTORE] Cleaning (dropping & recreating) target database '${TARGET_DB}'..."
  mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" \
    -e "DROP DATABASE IF EXISTS \`${TARGET_DB}\`; CREATE DATABASE \`${TARGET_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
else
  mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" \
    -e "CREATE DATABASE IF NOT EXISTS \`${TARGET_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
fi

echo "[RESTORE] Importing backup SQL into '${TARGET_DB}'..."
mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" --database="${TARGET_DB}" < "${BACKUP_FILE}"

echo "[RESTORE SUCCESS] Database '${TARGET_DB}' successfully restored from '${BACKUP_FILE}'."
