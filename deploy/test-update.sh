#!/bin/sh
# Test the update control flow with fake Docker, backup and smoke commands.
set -eu
fixture=$(mktemp -d)
cleanup() {
  rm -f "$fixture"/backups/schema-*.txt
  rmdir "$fixture/backups" 2>/dev/null || :
  rm -f "$fixture"/update.sh "$fixture"/docker "$fixture"/backup.sh "$fixture"/smoke.sh \
    "$fixture"/flock "$fixture"/sync "$fixture"/calls "$fixture"/.env.smoke \
    "$fixture"/.release.* "$fixture"/release.images "$fixture"/previous.images
  rmdir "$fixture"
}
trap cleanup EXIT
cp "$(dirname "$0")/update.sh" "$fixture/"
cd "$fixture"
export PATH="$fixture:$PATH"
export revision=1234567890123456789012345678901234567890
export migration=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
export alternate_migration=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
cat > .env.smoke <<'EOF'
BASE_URL=https://smoke.example.test
SMOKE_USER=fixture
SMOKE_PASSWORD=fixture
SMOKE_OUTSIDER_USER=other
SMOKE_OUTSIDER_PASSWORD=fixture
SMOKE_TASK_ID=fixture
EOF
cat > docker <<'EOF'
#!/bin/sh
set -eu
case "$1 $2" in
  'compose ps') echo "$4" ;;
  'inspect --format') echo "old-$4" ;;
  'image inspect')
    case "$4/$5" in
      *revision*) echo "$revision" ;;
      *old-backend) echo "$migration" ;;
      *) if [ "$scenario" = migration ]; then echo "$alternate_migration"; else echo "$migration"; fi ;;
    esac ;;
  'compose -f')
    case "$6" in
      pull) echo pull >> calls ;;
      up)
        case "$BACKEND_IMAGE" in
          *rollback*) echo rollback >> calls ;;
          *) echo candidate >> calls ;;
        esac ;;
      *) exit 99 ;;
    esac ;;
  'compose stop') echo stop >> calls ;;
  'compose exec')
    if [ "$scenario" = history ] && grep -qx candidate calls; then
      echo changed-schema
    else
      echo schema
    fi ;;

  'tag old-backend'|'tag old-web') ;;
  *) exit 99 ;;
esac
EOF
cat > backup.sh <<'EOF'
set -eu
echo backup >> calls
[ "$scenario" != backup ]
mkdir -p backups
echo schema > "backups/schema-$1.txt"
EOF
cat > smoke.sh <<'EOF'
set -eu
test "$SMOKE_REQUIRED" = 1
case "$BACKEND_IMAGE" in
  *rollback*) exit 0 ;;
esac
[ "$scenario" = success ]
EOF
chmod +x docker
# Git Bash can lack flock/sync -f; the mock covers update logic, while the
# production host uses Ubuntu util-linux/coreutils.
printf '#!/bin/sh\nexit 0\n' > flock
cp flock sync
chmod +x flock sync

run() {
  export scenario="$1"
  : > calls
  result=0
  printf '%s\n' "$revision" "ghcr.io/leminhhoai1011/task-manager-backend@sha256:$alternate_migration" \
    "ghcr.io/leminhhoai1011/task-manager-web@sha256:$alternate_migration" |
    sh ./update.sh || result=$?
  [ "$result" = "$2" ]
}

run success 0
test ! -e .release.pending
grep -Fq "backend@sha256:$alternate_migration" release.images
run compatible 1
grep -qx rollback calls
grep -q 'backend:rollback-' release.images
test ! -e .release.pending
run migration 1
if grep -qx rollback calls; then exit 1; fi
test -f .release.pending
rm .release.pending
run history 1
if grep -qx rollback calls; then exit 1; fi
test -f .release.pending
rm .release.pending
run backup 1
if grep -Eq '^(candidate|rollback)$' calls; then exit 1; fi
test -f .release.pending
echo 'Update passed success, backup failure, rollback and migration/history hold checks'
