#!/bin/sh
# Runs once inside the MinIO image (which bundles the `mc` client).
# Creates the private bucket and a dedicated application user.
set -eu

MC=/opt/bitnami/minio-client/bin/mc

: "${MINIO_ROOT_USER:?MINIO_ROOT_USER is required}"
: "${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD is required}"
: "${MINIO_BUCKET:?MINIO_BUCKET is required}"
: "${MINIO_APP_USER:?MINIO_APP_USER is required}"
: "${MINIO_APP_PASSWORD:?MINIO_APP_PASSWORD is required}"

i=0
until "$MC" alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1 \
      && "$MC" ready local >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 60 ]; then
    echo "MinIO did not become ready in time" >&2
    exit 1
  fi
  sleep 2
done

"$MC" mb --ignore-existing "local/$MINIO_BUCKET" >/dev/null
"$MC" anonymous set none "local/$MINIO_BUCKET" >/dev/null 2>&1 || true

if [ "$MINIO_APP_USER" != "$MINIO_ROOT_USER" ]; then
  if ! "$MC" admin user info local "$MINIO_APP_USER" >/dev/null 2>&1; then
    "$MC" admin user add local "$MINIO_APP_USER" "$MINIO_APP_PASSWORD" >/dev/null
  fi
  # Idempotent: attaching an already-attached policy is harmless.
  "$MC" admin policy attach local readwrite --user "$MINIO_APP_USER" >/dev/null 2>&1 || true
fi

echo "MinIO ready: bucket=$MINIO_BUCKET app_user=$MINIO_APP_USER"
