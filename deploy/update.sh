#!/bin/sh
# Safe update on a Linux VPS:
#   1. back up Postgres + MinIO,
#   2. rebuild backend/web images from the current source,
#   3. recreate only the changed containers.
# Run from anywhere:  ./update.sh
set -eu
cd "$(dirname "$0")"

echo "==> Backup before update"
./backup.sh

echo "==> Build images"
docker compose build

echo "==> Recreate containers"
docker compose up -d

echo "==> Status"
docker compose ps

if [ -n "${SMOKE_USER:-}" ] && [ -n "${SMOKE_PASSWORD:-}" ]; then
  echo "==> Smoke test"
  ./smoke.sh
else
  echo "Set SMOKE_USER/SMOKE_PASSWORD to run ./smoke.sh automatically."
fi

echo "Update complete."
