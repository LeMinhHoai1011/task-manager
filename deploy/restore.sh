#!/bin/sh
# DESTRUCTIVE restore. Requires explicit --yes.
#   ./restore.sh --yes backups/db-YYYY....dump [backups/minio-YYYY....tgz]
set -eu
cd "$(dirname "$0")"

if [ "${1:-}" != "--yes" ]; then
  echo "Refusing to restore without --yes (this replaces the current database)." >&2
  echo "Usage: $0 --yes <db.dump> [minio.tgz]" >&2
  exit 2
fi

DB_DUMP=${2:?missing db dump path}
MINIO_TAR=${3:-}

to_docker_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1"
  else
    printf '%s' "$1"
  fi
}

if [ ! -f "$DB_DUMP" ]; then
  echo "DB dump not found: $DB_DUMP" >&2
  exit 2
fi

echo "Stopping web + backend..."
docker compose stop web backend

echo "Restoring Postgres from $DB_DUMP ..."
docker compose exec -T db sh -c \
  'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' \
  < "$DB_DUMP"

if [ -n "$MINIO_TAR" ]; then
  if [ ! -f "$MINIO_TAR" ]; then
    echo "MinIO archive not found: $MINIO_TAR" >&2
    exit 2
  fi
  ABS_TAR=$(cd "$(dirname "$MINIO_TAR")" && pwd)/$(basename "$MINIO_TAR")
  ABS_DIR=$(dirname "$ABS_TAR")
  BASE_TAR=$(basename "$ABS_TAR")
  ABS_DIR_DOCKER=$(to_docker_path "$ABS_DIR")
  echo "Restoring MinIO from $ABS_TAR ..."
  docker run --rm \
    -v smarttask_minio-data:/data \
    -v "$ABS_DIR_DOCKER":/backup:ro \
    alpine:3.20 sh -c "rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null || true; tar xzf /backup/$BASE_TAR -C /data"
fi

echo "Starting backend + web..."
docker compose start backend web
echo "Restore complete. Check: docker compose ps && ./smoke.sh"
