#!/bin/sh
# Installed as a fixed SSH command. Read one release from standard input.
set -eu
umask 077
cd "$(dirname "$0")"

fail() { echo "Deploy failed: $*" >&2; exit 1; }

IFS= read -r revision
IFS= read -r candidate_backend
IFS= read -r candidate_web
if IFS= read -r extra || [ -n "$extra" ]; then fail 'unexpected input'; fi
printf '%s\n' "$revision" | grep -Eq '^[0-9a-f]{40}$' || fail 'invalid revision'
repository='ghcr[.]io/leminhhoai1011/task-manager'
printf '%s\n' "$candidate_backend" | grep -Eq "^$repository-backend@sha256:[0-9a-f]{64}$" || fail 'invalid backend digest'
printf '%s\n' "$candidate_web" | grep -Eq "^$repository-web@sha256:[0-9a-f]{64}$" || fail 'invalid web digest'

exec 9>.release.lock
flock -n 9 || fail 'another maintenance operation holds the lock'
[ ! -e .release.pending ] || fail 'interrupted release requires operator review'
# This file is provisioned on the host and contains only smoke settings.
set -a
. ./.env.smoke
set +a
: "${BASE_URL:?}" "${SMOKE_USER:?}" "${SMOKE_PASSWORD:?}" "${SMOKE_OUTSIDER_USER:?}" "${SMOKE_OUTSIDER_PASSWORD:?}" "${SMOKE_TASK_ID:?}"

current_image() {
  container=$(docker compose ps -aq "$1") || return 1
  [ -n "$container" ] || return 1
  docker inspect --format '{{.Image}}' "$container"
}

set_images() {
  export BACKEND_IMAGE="$1" WEB_IMAGE="$2"
}

release_compose() {
  docker compose -f docker-compose.yml -f compose.release.yml "$@"
}

start_apps() {
  release_compose up -d --no-deps --no-build --pull never --wait --wait-timeout 240 backend web
}

schema_history() {
  docker compose exec -T db sh -c \
    'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select version,script,checksum,success from flyway_schema_history order by installed_rank"'
}

migration_label() {
  docker image inspect --format '{{ index .Config.Labels "vn.smarttask.flyway-migrations" }}' "$1"
}

record_images() {
  printf 'BACKEND_IMAGE=%s\nWEB_IMAGE=%s\n' "$2" "$3" > "$1.tmp"
  mv "$1.tmp" "$1"
}

old_backend=$(current_image backend) || fail 'bootstrap backend first'
old_web=$(current_image web) || fail 'bootstrap web first'
old_migrations=$(migration_label "$old_backend")
stamp=$(date -u +%Y%m%dT%H%M%SZ)
rollback_backend="smarttask-backend:rollback-$stamp"
rollback_web="smarttask-web:rollback-$stamp"
docker tag "$old_backend" "$rollback_backend"
docker tag "$old_web" "$rollback_web"

set_images "$candidate_backend" "$candidate_web"
release_compose pull backend web
for image in "$candidate_backend" "$candidate_web"; do
  [ "$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image")" = "$revision" ] ||
    fail 'image revision mismatch'
done
new_migrations=$(migration_label "$candidate_backend")
printf '%s\n' "$new_migrations" | grep -Eq '^[0-9a-f]{64}$' || fail 'missing migration label'

printf 'revision=%s\nbackup=%s\nprevious_backend=%s\nprevious_web=%s\ncandidate_backend=%s\ncandidate_web=%s\n' \
  "$revision" "$stamp" "$rollback_backend" "$rollback_web" "$candidate_backend" "$candidate_web" > .release.pending
sync -f .release.pending
BACKUP_KEEP_APPS_STOPPED=1 sh ./backup.sh "$stamp"
sync -f "${BACKUP_DIR:-backups}"
schema_history > .release.schema
cmp -s "${BACKUP_DIR:-backups}/schema-$stamp.txt" .release.schema ||
  fail 'Flyway history changed after backup, application stopped'

if start_apps && SMOKE_REQUIRED=1 sh ./smoke.sh; then
  record_images previous.images "$rollback_backend" "$rollback_web"
  record_images release.images "$candidate_backend" "$candidate_web"
  rm .release.pending
  echo "Release $revision passed"
  exit 0
fi

docker compose stop -t 60 web backend
if [ "$old_migrations" = "$new_migrations" ] &&
   schema_history > .release.schema &&
   cmp -s "${BACKUP_DIR:-backups}/schema-$stamp.txt" .release.schema; then
  set_images "$rollback_backend" "$rollback_web"
  if start_apps && SMOKE_REQUIRED=1 sh ./smoke.sh; then
    record_images release.images "$rollback_backend" "$rollback_web"
    rm .release.pending
    fail 'candidate failed; previous applications restored'
  fi
  docker compose stop -t 60 web backend
  fail 'previous applications failed verification; operator action required'
fi
fail 'schema compatibility could not be established; application stopped, operator action required'
