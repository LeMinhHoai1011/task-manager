#!/bin/sh
# Pull SmartTask backups from the VPS to a Linux/macOS machine.
# Add to cron on the local machine, e.g. daily at 03:00:
#   0 3 * * * cd /path/to/smarttask/deploy && ./pull-backups.sh
#
# Required: VPS_HOST. Optional: VPS_USER, REMOTE_DIR, LOCAL_DIR, KEEP_DAYS,
# SSH_KEY. Key-based SSH is required for unattended runs.
set -eu

: "${VPS_HOST:?set VPS_HOST (or export it in cron)}"
VPS_USER=${VPS_USER:-smarttask}
REMOTE_DIR=${REMOTE_DIR:-/opt/smarttask/deploy/backups}
LOCAL_DIR=${LOCAL_DIR:-$(pwd)/local-backups}
KEEP_DAYS=${KEEP_DAYS:-30}

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
DEST="$LOCAL_DIR/$STAMP"
mkdir -p "$DEST"

set -- -r -o BatchMode=yes -o StrictHostKeyChecking=accept-new
if [ -n "${SSH_KEY:-}" ]; then
  set -- "$@" -i "$SSH_KEY"
fi

scp "$@" "$VPS_USER@$VPS_HOST:$REMOTE_DIR/." "$DEST/"

# Keep local snapshots for KEEP_DAYS; only inside LOCAL_DIR.
find "$LOCAL_DIR" -mindepth 1 -maxdepth 1 -type d -mtime +"$KEEP_DAYS" -exec rm -rf {} +

echo "Pulled backups to $DEST"
