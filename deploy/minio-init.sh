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
case "$MINIO_BUCKET" in *[!a-z0-9.-]*|'') echo 'Invalid bucket name' >&2; exit 1;; esac
if [ "$MINIO_APP_USER" = "$MINIO_ROOT_USER" ]; then
  echo 'Application must use a dedicated non-root MinIO user' >&2
  exit 1
fi

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
"$MC" anonymous set none "local/$MINIO_BUCKET" >/dev/null

if [ "$MINIO_APP_USER" != "$MINIO_ROOT_USER" ]; then
  if ! "$MC" admin user info local "$MINIO_APP_USER" >/dev/null 2>&1; then
    "$MC" admin user add local "$MINIO_APP_USER" "$MINIO_APP_PASSWORD" >/dev/null
  fi
  # Scope application credentials to this bucket, not every bucket on MinIO.
  POLICY_NAME="smarttask-$MINIO_BUCKET"
  cat > /tmp/smarttask-bucket-policy.json <<EOF
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:GetBucketLocation","s3:ListBucket"],"Resource":["arn:aws:s3:::$MINIO_BUCKET"]},{"Effect":"Allow","Action":["s3:GetObject","s3:PutObject","s3:DeleteObject"],"Resource":["arn:aws:s3:::$MINIO_BUCKET/*"]}]}
EOF
  "$MC" admin policy create local "$POLICY_NAME" /tmp/smarttask-bucket-policy.json >/dev/null
  "$MC" admin policy attach local "$POLICY_NAME" --user "$MINIO_APP_USER" >/dev/null
fi

# Verify the supplied application password, including on an existing install.
"$MC" alias set app http://minio:9000 "$MINIO_APP_USER" "$MINIO_APP_PASSWORD" >/dev/null
"$MC" ls "app/$MINIO_BUCKET" >/dev/null

echo "MinIO ready: bucket=$MINIO_BUCKET app_user=$MINIO_APP_USER"
