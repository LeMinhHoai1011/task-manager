#!/bin/bash
# =============================================================================
# SmartTask V3.0 — Chạy toàn hệ thống qua Docker Compose (Ubuntu)
# =============================================================================
# Services: PostgreSQL, MinIO, Backend (Spring Boot), Web (Caddy + Vite build)
# Truy cập giao diện: http://localhost:80  (hoặc port HTTP_PORT trong .env)
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${DEPLOY_DIR}/.env"
ENV_EXAMPLE="${DEPLOY_DIR}/.env.example"

# Mau thong bao
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

ok()   { echo -e "${GREEN}[OK]${NC}  $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
err()  { echo -e "${RED}[ERR]${NC}  $1"; }
info() { echo -e "${CYAN}[INFO]${NC} $1"; }

header() {
  echo ""
  echo "=========================================="
  echo "  $1"
  echo "=========================================="
}

# -----------------------------------------------------------------------------
# 1. Kiem tra Docker
# -----------------------------------------------------------------------------
header "KIEM TRA DOCKER"

if ! command -v docker &> /dev/null; then
  err "Docker khong duoc tim thay. Vui loi cai Docker Engine."
  exit 1
fi
ok "Tim thay Docker: $(docker --version)"

if ! docker info &> /dev/null; then
  err "Docker daemon khong dang chay. Hay khoi dong Docker roi chay lai."
  exit 1
fi
ok "Docker daemon dang chay"

# -----------------------------------------------------------------------------
# 2. Tao .env neu chua co
# -----------------------------------------------------------------------------
header "CHUAN BI ENV"

if [ ! -f "$ENV_FILE" ]; then
  info "File .env chua co. Tao tu .env.example..."
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  ok "Da tao $ENV_FILE"
  warn "VUI LONG MO $ENV_FILE VA DIEN DAY DU CAC GIA TRI MAT (POSTGRES_PASSWORD, MINIO_ROOT_PASSWORD, ...)"
  warn "Sau khi sua xong, chay lai script nay."
else
  ok "Da ton tai .env"
fi

# Doc HTTP_PORT tu .env neu co
HTTP_PORT="80"
if [ -f "$ENV_FILE" ]; then
  HP=$(grep -E '^HTTP_PORT=' "$ENV_FILE" 2>/dev/null | cut -d= -f2- || true)
  if [ -n "$HP" ]; then
    HTTP_PORT="$HP"
  fi
fi
BASE_URL="http://localhost:${HTTP_PORT}"

# -----------------------------------------------------------------------------
# 3. Chay Docker Compose
# -----------------------------------------------------------------------------
header "CHAY DOCKER COMPOSE"

info "Khoi dong cac service (co the gian dau tien vi can build image)..."
docker compose -f "${DEPLOY_DIR}/docker-compose.yml" up -d --build
ok "Cac service da duoc khoi dong"

# -----------------------------------------------------------------------------
# 4. Doi Backend san sang (health check)
# -----------------------------------------------------------------------------
header "DOI BACKEND SAN SANG"

BACKEND_READY=false
for i in $(seq 1 60); do
  sleep 5
  if curl -fsS "${BASE_URL}/actuator/health" > /dev/null 2>&1; then
    STATUS=$(curl -fsS "${BASE_URL}/actuator/health" 2>/dev/null | grep -o '"status":"[^"]*"' | cut -d'"' -f4 || true)
    if [ "$STATUS" = "UP" ]; then
      BACKEND_READY=true
      break
    fi
  fi
  info "   lan ${i}/60 - doi backend..."
done

if [ "$BACKEND_READY" = true ]; then
  ok "Backend san sang tai ${BASE_URL}"
else
  warn "Backend chua phat hien san sang sau 5 phut. Vui loi kiem tra log: docker compose logs backend"
fi

# -----------------------------------------------------------------------------
# 5. Kiem tra Web (Caddy)
# -----------------------------------------------------------------------------
header "KIEM TRA GIAO DIEN WEB"

WEB_READY=false
for i in $(seq 1 20); do
  sleep 3
  if curl -fsS "${BASE_URL}" > /dev/null 2>&1; then
    WEB_READY=true
    break
  fi
  info "   lan ${i}/20 - doi web..."
done

if [ "$WEB_READY" = true ]; then
  ok "Giao dien web san sang tai ${BASE_URL}"
else
  warn "Web chua phat hien san sang. Vui loi kiem tra: docker compose logs web"
fi

# -----------------------------------------------------------------------------
# 6. Mo trinh duyet (neu co)
# -----------------------------------------------------------------------------
header "MO GIAO DIEN KIEM TRA"

if [ "$WEB_READY" = true ]; then
  if command -v xdg-open &> /dev/null; then
    xdg-open "${BASE_URL}" || true
  elif command -v gnome-open &> /dev/null; then
    gnome-open "${BASE_URL}" || true
  fi
  ok "Da mo trinh duyet: ${BASE_URL}"
else
  warn "Web chua san sang, vui loi mo thu cong: ${BASE_URL}"
fi

# -----------------------------------------------------------------------------
# 7. Tom tat
# -----------------------------------------------------------------------------
header "TOM TAT"
info "Giao dien Web:  ${BASE_URL}"
info "Backend API:    ${BASE_URL}/api/v1"
info "Health check:   ${BASE_URL}/actuator/health"
info "Lenh xem log:   docker compose -f ${DEPLOY_DIR}/docker-compose.yml logs -f"
info "Lenh dung:      docker compose -f ${DEPLOY_DIR}/docker-compose.yml down"
echo ""
info "He thong dang chay o che mode nen (detached)."
