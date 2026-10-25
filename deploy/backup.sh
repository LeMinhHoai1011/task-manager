#!/bin/sh
# Back up Postgres + MinIO + local fallback files. Run on the server from
# the deploy directory:  ./backup.sh
set -eu
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

echo "[1/3] Postgres -> $OUT/db-$STAMP.dump"
docker compose exec -T db sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$OUT/db-$STAMP.dump"

echo "[2/3] MinIO -> $OUT/minio-$STAMP.tgz"
docker run --rm \
  -v smarttask_minio-data:/data:ro \
  -v "$OUT_DOCKER":/backup \
  alpine:3.20 sh -c "tar czf /backup/minio-$STAMP.tgz -C /data ."

if docker volume inspect smarttask_backend-files >/dev/null 2>&1; then
  echo "[3/3] local files -> $OUT/files-$STAMP.tgz"
  docker run --rm \
    -v smarttask_backend-files:/data:ro \
    -v "$OUT_DOCKER":/backup \
    alpine:3.20 sh -c "tar czf /backup/files-$STAMP.tgz -C /data ."
else
  echo "[3/3] local files volume not present, skipped"
fi

# Keep two weeks by default.
find "$OUT" -type f -mtime +14 -delete
ls -lh "$OUT" | tail -n 10
echo "Backup complete: $STAMP"
