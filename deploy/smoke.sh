#!/bin/sh
# Health-only by default; SMOKE_REQUIRED=1 requires isolated RBAC/storage fixtures.
#   SMOKE_USER=admin SMOKE_PASSWORD='...' ./smoke.sh
set -eu
cd "$(dirname "$0")"

PORT=${HTTP_PORT:-}
if [ -z "$PORT" ] && [ -f .env ]; then
  PORT=$(grep -E '^HTTP_PORT=' .env 2>/dev/null | cut -d= -f2- || true)
fi
BASE_URL=${BASE_URL:-http://localhost:${PORT:-80}}

http() { curl --connect-timeout 5 --max-time 20 "$@"; }

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

echo "Health: $BASE_URL/actuator/health"
HEALTH=$(http -fsS "$BASE_URL/actuator/health") || fail "health endpoint unreachable"
printf '%s' "$HEALTH" | grep -q '"status":"UP"' || fail "health is not UP: $HEALTH"

if [ -z "${SMOKE_USER:-}" ] || [ -z "${SMOKE_PASSWORD:-}" ]; then
  [ "${SMOKE_REQUIRED:-0}" != 1 ] || fail "smoke credentials required"
  echo "OK: health. Set SMOKE_USER/SMOKE_PASSWORD to also test login + SSE."
  exit 0
fi

login() {
  LOGIN_USER="$1" LOGIN_PASSWORD="$2" jq -nc \
    '{username:env.LOGIN_USER,password:env.LOGIN_PASSWORD,client_type:"WEB"}' |
    http -fsS -X POST "$BASE_URL/api/v1/auth/login" \
      -H 'Content-Type: application/json' --data-binary @- |
    jq -er '.data.access_token | strings | select(length > 0)'
}

TOKEN=$(login "$SMOKE_USER" "$SMOKE_PASSWORD") || fail "login request failed"

echo "Authorized read: /api/v1/workspaces"
CODE=$(http -sS -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/v1/workspaces")
[ "$CODE" = "200" ] || fail "workspaces returned $CODE"

echo "Realtime ticket + single-use check"
TICKET_JSON=$(http -fsS -X POST \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{}' "$BASE_URL/api/v1/realtime/ticket") || fail "ticket request failed"
TICKET=$(printf '%s' "$TICKET_JSON" | jq -er '.data.ticket | strings | select(length > 0)')
[ -n "$TICKET" ] || fail "ticket response has no ticket"

SSE_CODE=$(http -sS -o /dev/null -w '%{http_code}' --max-time 2 \
  "$BASE_URL/api/v1/realtime/stream?ticket=$TICKET" 2>/dev/null || true)
[ "$SSE_CODE" = "200" ] || fail "first SSE handshake returned $SSE_CODE"

REUSE_CODE=$(http -sS -o /dev/null -w '%{http_code}' --max-time 2 \
  "$BASE_URL/api/v1/realtime/stream?ticket=$TICKET" 2>/dev/null || true)
[ "$REUSE_CODE" = "401" ] || fail "reused ticket returned $REUSE_CODE (expected 401)"

echo "OK: health + login + authorized read + SSE ticket single-use"

if [ "${SMOKE_REQUIRED:-0}" != 1 ]; then exit 0; fi

: "${SMOKE_TASK_ID:?}" "${SMOKE_OUTSIDER_USER:?}" "${SMOKE_OUTSIDER_PASSWORD:?}"
printf '%s\n' "$SMOKE_TASK_ID" | grep -Eq '^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$' ||
  fail "invalid fixture task ID"
api="$BASE_URL/api/v1"
http -fsS -o /dev/null "$BASE_URL/"
http -fsS -o /dev/null -H "Authorization: Bearer $TOKEN" "$api/tasks/$SMOKE_TASK_ID"
code=$(http -sS -o /dev/null -w '%{http_code}' "$api/workspaces")
[ "$code" = 401 ] || fail "anonymous API returned $code"
outsider=$(login "$SMOKE_OUTSIDER_USER" "$SMOKE_OUTSIDER_PASSWORD") || fail "outsider login failed"
code=$(http -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $outsider" "$api/tasks/$SMOKE_TASK_ID")
case "$code" in 403|404) ;; *) fail "outsider task access returned $code";; esac
http -fsS -o /dev/null -X POST -H "Authorization: Bearer $outsider" "$api/auth/logout"

temp=$(mktemp -d)
attachment=
post() {
  http -fsS -X POST -H "Authorization: Bearer $TOKEN" \
    -H "Idempotency-Key: $(cat /proc/sys/kernel/random/uuid)" "$@"
}
cleanup() {
  result=$?
  trap - EXIT
  if [ -n "$attachment" ]; then
    post -o /dev/null "$api/files/$attachment/delete" || result=1
  fi
  post -o /dev/null "$api/auth/logout" || result=1
  rm -f "$temp/probe.pdf" "$temp/download.pdf"
  rmdir "$temp"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM

printf '%%PDF-1.4\nSmartTask release smoke\n%%%%EOF\n' > "$temp/probe.pdf"
bytes=$(wc -c < "$temp/probe.pdf")
upload=$(jq -nc --arg task "$SMOKE_TASK_ID" --argjson bytes "$bytes" \
  '{task_id:$task,file_name:"release-smoke.pdf",mime_type:"application/pdf",declared_size:$bytes}')
session=$(post "$api/files/upload" -H 'Content-Type: application/json' -d "$upload" |
  jq -er '.data.upload_session_id | strings | select(length > 0)')
post -o /dev/null "$api/files/upload/$session/content" -F "file=@$temp/probe.pdf;type=application/pdf"
post -o /dev/null "$api/files/upload/$session/complete" -H 'Content-Type: application/json' -d "{\"actual_bytes\":$bytes}"
attachment=$(post "$api/files/upload/$session/commit" -H 'Content-Type: application/json' -d '{}' |
  jq -er '.data.attachment_id | strings | select(length > 0)')
ticket=$(http -fsS -H "Authorization: Bearer $TOKEN" "$api/files/$attachment/download" |
  jq -er '.data.ticket | strings | select(length > 0)')
http -fsS -o "$temp/download.pdf" "$api/files/stream/$ticket"
cmp "$temp/probe.pdf" "$temp/download.pdf"
echo "OK: SPA, anonymous and outsider denied, upload and byte-exact download"
