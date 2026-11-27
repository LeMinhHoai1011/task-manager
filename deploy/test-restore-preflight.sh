#!/bin/sh
set -eu

restore="$(cd "$(dirname "$0")" && pwd)/restore.sh"
fixture=$(mktemp -d)
trap 'rm -f "$fixture/db-test.dump" "$fixture/minio-test.tgz" "$fixture/files-test.tgz" "$fixture/other.tgz" "$fixture/manifest-test.sha256" "$fixture/docker"; rmdir "$fixture"' EXIT
cd "$fixture"
printf '%s\n' db > db-test.dump
printf '%s\n' objects > minio-test.tgz
printf '%s\n' files > files-test.tgz
cp minio-test.tgz other.tgz
sha256sum db-test.dump minio-test.tgz files-test.tgz > manifest-test.sha256

printf '%s\n' '#!/bin/sh' 'echo unexpected-docker-call >&2; exit 99' > docker
chmod +x docker
export PATH="$fixture:$PATH"

reject() {
  status=0
  output=$(sh "$restore" --yes "$fixture/db-test.dump" "$@" 2>&1) || status=$?
  [ "$status" -eq 2 ]
  case "$output" in
    *"same verified backup set"*) ;;
    *) printf '%s\n' "$output" >&2; exit 1 ;;
  esac
}

reject "$fixture/other.tgz" "$fixture/files-test.tgz"
reject "$fixture/minio-test.tgz"

status=0
output=$(sh "$restore" --yes "$fixture/db-test.dump" "$fixture/minio-test.tgz" "$fixture/files-test.tgz" 2>&1) || status=$?
[ "$status" -eq 99 ]
case "$output" in
  *unexpected-docker-call*) ;;
  *) printf '%s\n' "$output" >&2; exit 1 ;;
esac
echo 'Restore rejects mismatched and incomplete sets, and accepts the matching manifest.'
