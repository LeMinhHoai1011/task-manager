#!/bin/sh
# DESTRUCTIVE restore. Requires explicit --yes.
#   ./restore.sh --yes backups/db-YYYY....dump [backups/minio-YYYY....tgz]
set -eu
umask 077
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

# Validate all inputs before stopping services or replacing any database rows.
docker compose exec -T db pg_restore --list < "$DB_DUMP" >/dev/null
if [ -n "$MINIO_TAR" ]; then
  [ -f "$MINIO_TAR" ] || { echo 'MinIO archive not found' >&2; exit 2; }
  gzip -t "$MINIO_TAR"
  if tar tzf "$MINIO_TAR" | grep -Eq '(^/|(^|/)\.\.(/|$))'; then
    echo 'Unsafe path in MinIO archive' >&2
    exit 2
  fi
  MINIO_CONTAINER=$(docker compose ps -aq minio)
  [ -n "$MINIO_CONTAINER" ] || { echo 'MinIO container not found' >&2; exit 2; }
  MINIO_VOLUME=$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/bitnami/minio/data"}}{{.Name}}{{end}}{{end}}' "$MINIO_CONTAINER")
  [ -n "$MINIO_VOLUME" ] || { echo 'Expected named MinIO volume not found' >&2; exit 2; }
fi

echo "Stopping web + backend..."
docker compose stop web backend
if [ -n "$MINIO_TAR" ]; then docker compose stop minio; fi

echo "Restoring Postgres from $DB_DUMP ..."
docker compose exec -T db sh -c \
  'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --exit-on-error --single-transaction' \
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
    -v "$MINIO_VOLUME":/data \
    -v "$ABS_DIR_DOCKER":/backup:ro \
    alpine:3.20 sh -ec 'rm -rf /data/* /data/.[!.]* /data/..?*; tar xzf "/backup/$1" -C /data' sh "$BASE_TAR"
fi

echo "Starting backend + web..."
if [ -n "$MINIO_TAR" ]; then docker compose start minio; fi
docker compose start backend web
echo "Restore complete. Check: docker compose ps && ./smoke.sh"
