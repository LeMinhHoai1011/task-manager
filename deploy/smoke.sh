#!/bin/sh
# Minimal production smoke test. Health is always checked; auth + SSE are
# checked when SMOKE_USER / SMOKE_PASSWORD are set.
#   SMOKE_USER=admin SMOKE_PASSWORD='...' ./smoke.sh
set -eu
cd "$(dirname "$0")"

PORT=${HTTP_PORT:-}
if [ -z "$PORT" ] && [ -f .env ]; then
  PORT=$(grep -E '^HTTP_PORT=' .env 2>/dev/null | cut -d= -f2- || true)
fi
BASE_URL=${BASE_URL:-http://localhost:${PORT:-80}}

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

echo "Health: $BASE_URL/actuator/health"
HEALTH=$(curl -fsS "$BASE_URL/actuator/health") || fail "health endpoint unreachable"
printf '%s' "$HEALTH" | grep -q '"status":"UP"' || fail "health is not UP: $HEALTH"

if [ -z "${SMOKE_USER:-}" ] || [ -z "${SMOKE_PASSWORD:-}" ]; then
  echo "OK: health. Set SMOKE_USER/SMOKE_PASSWORD to also test login + SSE."
  exit 0
fi

echo "Login: $SMOKE_USER"
LOGIN=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$SMOKE_USER\",\"password\":\"$SMOKE_PASSWORD\",\"client_type\":\"WEB\"}") \
  || fail "login request failed"
TOKEN=$(printf '%s' "$LOGIN" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || fail "login response has no access_token"

echo "Authorized read: /api/v1/workspaces"
CODE=$(curl -sS -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/v1/workspaces")
[ "$CODE" = "200" ] || fail "workspaces returned $CODE"

echo "Realtime ticket + single-use check"
TICKET_JSON=$(curl -fsS -X POST \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{}' "$BASE_URL/api/v1/realtime/ticket") || fail "ticket request failed"
TICKET=$(printf '%s' "$TICKET_JSON" | sed -n 's/.*"ticket":"\([^"]*\)".*/\1/p')
[ -n "$TICKET" ] || fail "ticket response has no ticket"

SSE_CODE=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 2 \
  "$BASE_URL/api/v1/realtime/stream?ticket=$TICKET" 2>/dev/null || true)
[ "$SSE_CODE" = "200" ] || fail "first SSE handshake returned $SSE_CODE"

REUSE_CODE=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 2 \
  "$BASE_URL/api/v1/realtime/stream?ticket=$TICKET" 2>/dev/null || true)
[ "$REUSE_CODE" = "401" ] || fail "reused ticket returned $REUSE_CODE (expected 401)"

echo "OK: health + login + authorized read + SSE ticket single-use"
