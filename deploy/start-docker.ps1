# =============================================================================
# SmartTask V3.0 — Chạy toàn hệ thống qua Docker Compose (Windows PowerShell)
# =============================================================================
# Services: PostgreSQL, MinIO, Backend (Spring Boot), Web (Caddy + Vite build)
# Truy cập giao diện: http://localhost:80  (hoặc port HTTP_PORT trong .env)
# =============================================================================

$ErrorActionPreference = "Continue"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$deployDir = Join-Path $scriptDir "deploy"
$envFile = Join-Path $deployDir ".env"
$envExample = Join-Path $deployDir ".env.example"

# Mau thong bao
function Write-Header($text) { Write-Host "`n==========================================" -ForegroundColor Cyan; Write-Host "  $text" -ForegroundColor Cyan; Write-Host "==========================================" -ForegroundColor Cyan }
function Write-Ok($text)  { Write-Host "  [OK] $text" -ForegroundColor Green }
function Write-Warn($text){ Write-Host "  [WARN] $text" -ForegroundColor Yellow }
function Write-Err($text) { Write-Host "  [ERR] $text" -ForegroundColor Red }
function Write-Info($text){ Write-Host "  [INFO] $text" -ForegroundColor Gray }

# -----------------------------------------------------------------------------
# 1. Kiem tra Docker
# -----------------------------------------------------------------------------
Write-Header "KIEM TRA DOCKER"

$dockerExe = Get-Command "docker.exe" -ErrorAction SilentlyContinue
if (-not $dockerExe) {
  $dockerExe = Get-Command "docker" -ErrorAction SilentlyContinue
}
if (-not $dockerExe) {
  Write-Err "Docker khong duoc tim thay. Vui loi cai Docker Desktop hoac Docker Engine."
  exit 1
}
Write-Ok "Tim thay Docker: $($dockerExe.Source)"

# Kiem tra daemon chay
try {
  $res = & docker ps 2>$null
  if ($LASTEXITCODE -ne 0) {
    Write-Err "Docker daemon khong dang chay. Hay khoi dong Docker Desktop roi chay lai."
    exit 1
  }
  Write-Ok "Docker daemon dang chay"
} catch {
  Write-Err "Khong the ket noi Docker daemon: $_"
  exit 1
}

# -----------------------------------------------------------------------------
# 2. Tao .env neu chua co
# -----------------------------------------------------------------------------
Write-Header "CHUAN BI ENV"

if (-not (Test-Path $envFile)) {
  Write-Info "File .env chua co. Tao tu .env.example..."
  Copy-Item $envExample $envFile
  Write-Ok "Da tao $envFile"
  Write-Warn "VUI LONG MO $envFile VA DIEN DAY DU CAC GIA TRI MAT (POSTGRES_PASSWORD, MINIO_ROOT_PASSWORD, ...)"
  Write-Warn "Sau khi sua xong, chay lai script nay."
} else {
  Write-Ok "Da ton tai .env"
}

# Doc HTTP_PORT tu .env neu co
$httpPort = "80"
if (Test-Path $envFile) {
  $line = Get-Content $envFile | Where-Object { $_ -match "^HTTP_PORT=" } | Select-Object -First 1
  if ($line) {
    $httpPort = ($line -split "=", 2)[1].Trim()
  }
}
$baseUrl = "http://localhost:$httpPort"

# -----------------------------------------------------------------------------
# 3. Chay Docker Compose
# -----------------------------------------------------------------------------
Write-Header "CHAY DOCKER COMPOSE"

$composeExe = Get-Command "docker-compose.exe" -ErrorAction SilentlyContinue
if (-not $composeExe) {
  $composeExe = Get-Command "docker" -ErrorAction SilentlyContinue
  # docker compose (plugin)
}

Write-Info "Khoi dong cac service (co the gian dau tien vi can build image)..."
& docker compose -f "$deployDir\docker-compose.yml" up -d --build 2>&1
if ($LASTEXITCODE -ne 0) {
  Write-Err "Docker compose up that bai. Xem log chi tren."
  exit 1
}
Write-Ok "Cac service da duoc khoi dong"

# -----------------------------------------------------------------------------
# 4. Doi Backend san sang (health check)
# -----------------------------------------------------------------------------
Write-Header "DOI BACKEND SAN SANG"

$backendReady = $false
for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 5
  try {
    $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -TimeoutSec 5 -ErrorAction Stop
    if ($health.status -eq "UP") { $backendReady = $true; break }
  } catch {
    # Backend chua san sang, tiep tuc doi
  }
  Write-Info "   lan $(($i+1))/60 - doi backend..."
}

if ($backendReady) {
  Write-Ok "Backend san sang tai $baseUrl"
} else {
  Write-Warn "Backend chua phat hien san sang sau 5 phut. Vui loi kiem tra log: docker compose logs backend"
}

# -----------------------------------------------------------------------------
# 5. Kiem tra Web (Caddy)
# -----------------------------------------------------------------------------
Write-Header "KIEM TRA GIAO DIEN WEB"

$webReady = $false
for ($i = 0; $i -lt 20; $i++) {
  Start-Sleep -Seconds 3
  try {
    $res = Invoke-WebRequest -Uri $baseUrl -TimeoutSec 5 -ErrorAction Stop
    if ($res.StatusCode -eq 200) { $webReady = $true; break }
  } catch {}
  Write-Info "   lan $(($i+1))/20 - doi web..."
}

if ($webReady) {
  Write-Ok "Giao dien web san sang tai $baseUrl"
} else {
  Write-Warn "Web chua phat hien san sang. Vui loi kiem tra: docker compose logs web"
}

# -----------------------------------------------------------------------------
# 6. Mo trinh duyet
# -----------------------------------------------------------------------------
Write-Header "MO GIAO DIỂN KIEM TRA"

if ($webReady) {
  Start-Process $baseUrl
  Write-Ok "Da mo trinh duyet: $baseUrl"
} else {
  Write-Warn "Web chua san sang, khong mo trinh tu dong. Vui loi mo thu cong: $baseUrl"
}

# -----------------------------------------------------------------------------
# 7. Tom tat
# -----------------------------------------------------------------------------
Write-Header "TOM TAT"
Write-Info "Giao dien Web:  $baseUrl"
Write-Info "Backend API:    $baseUrl/api/v1"
Write-Info "Health check:   $baseUrl/actuator/health"
Write-Info "Lenh xem log:   docker compose -f $deployDir\docker-compose.yml logs -f"
Write-Info "Lenh dung:      docker compose -f $deployDir\docker-compose.yml down"
Write-Host ""
Write-Host "He thong dang chay o che mode nen (detached). Bao mat cua so nay de he thong tiep tuc chay." -ForegroundColor Gray
