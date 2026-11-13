#!/bin/sh
# Back up Postgres + MinIO + local fallback files. Run on the server from
# the deploy directory:  ./backup.sh
set -eu
umask 077
cd "$(dirname "$0")"

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT="$(pwd)/${BACKUP_DIR:-backups}"
mkdir -p "$OUT"

# Git Bash/MSYS paths (/d/task/...) are not understood by Docker Desktop.
# cygpath is only present on Windows; on Linux this is a no-op.
to_docker_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1"
  else
    printf '%s' "$1"
  fi
}
OUT_DOCKER=$(to_docker_path "$OUT")

# Only snapshot the volumes actually mounted by this Compose project. Never
# silently create/back up an empty volume using a hardcoded project name.
MINIO_CONTAINER=$(docker compose ps -aq minio)
[ -n "$MINIO_CONTAINER" ] || { echo 'MinIO container not found' >&2; exit 1; }
MINIO_VOLUME=$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/bitnami/minio/data"}}{{.Name}}{{end}}{{end}}' "$MINIO_CONTAINER")
[ -n "$MINIO_VOLUME" ] || { echo 'Expected named MinIO volume not found; review mount configuration' >&2; exit 1; }
BACKEND_CONTAINER=$(docker compose ps -aq backend)
[ -n "$BACKEND_CONTAINER" ] || { echo 'Backend container not found' >&2; exit 1; }
FILES_VOLUME=$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/app/var/files"}}{{.Name}}{{end}}{{end}}' "$BACKEND_CONTAINER")
[ -n "$FILES_VOLUME" ] || { echo 'Backend files volume not found' >&2; exit 1; }

# A DB dump and live MinIO filesystem copy do not form a consistent backup.
# Quiesce every application writer and stop MinIO before copying its disk.
RUNNING=$(docker compose ps --status running --services)
RESUME=''
for service in minio backend web; do
  if printf '%s\n' "$RUNNING" | grep -qx "$service"; then RESUME="$RESUME $service"; fi
done
resume_services() {
  result=$?
  trap - EXIT HUP INT TERM
  if [ -n "$RESUME" ]; then docker compose start $RESUME || result=1; fi
  exit "$result"
}
trap resume_services EXIT HUP INT TERM
docker compose stop -t 60 web backend minio

echo "[1/3] Postgres -> $OUT/db-$STAMP.dump"
docker compose exec -T db sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$OUT/db-$STAMP.dump"

echo "[2/3] MinIO -> $OUT/minio-$STAMP.tgz"
docker run --rm \
  -v "$MINIO_VOLUME":/data:ro \
  -v "$OUT_DOCKER":/backup \
  alpine:3.20 sh -c "tar czf /backup/minio-$STAMP.tgz -C /data ."

if [ -n "$FILES_VOLUME" ]; then
  echo "[3/3] local files -> $OUT/files-$STAMP.tgz"
  docker run --rm \
    -v "$FILES_VOLUME":/data:ro \
    -v "$OUT_DOCKER":/backup \
    alpine:3.20 sh -c "tar czf /backup/files-$STAMP.tgz -C /data ."
else
  echo "[3/3] local files volume not present, skipped"
fi

# Record application images and schema alongside this consistent set. No .env
# or secret values are copied. Encrypt backups before off-host transfer.
docker compose images --format json > "$OUT/images-$STAMP.json"
docker compose exec -T db sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select version,script,checksum,success from flyway_schema_history order by installed_rank"' > "$OUT/schema-$STAMP.txt"
(cd "$OUT" && sha256sum "db-$STAMP.dump" "minio-$STAMP.tgz" "files-$STAMP.tgz" "images-$STAMP.json" "schema-$STAMP.txt" > "manifest-$STAMP.sha256")
# Retention is deliberately a separate operation against verified complete sets;
# never delete unrelated files from an operator-supplied BACKUP_DIR.
ls -lh "$OUT" | tail -n 10
echo "Backup complete: $STAMP"
