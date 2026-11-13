# =============================================================================
# SmartTask V3.0 - Chay Frontend + Backend local (Windows PowerShell)
# =============================================================================
# Yeucau: Java 21, Node.js + pnpm, PostgreSQL (co so hoac Docker)
# Maven duoc tu dong tai ve neu chua co tren may.
# Frontend: http://localhost:5173
# Backend:  http://localhost:8080/api/v1
# =============================================================================

$ErrorActionPreference = "Continue"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$webDir = Join-Path $scriptDir "web"
$backendDir = Join-Path $scriptDir "backend"
$mvnCmd = Join-Path $backendDir 'mvnw.cmd'

# Mau thong bao
function Write-Header($text) { Write-Host "`n==========================================" -ForegroundColor Cyan; Write-Host "  $text" -ForegroundColor Cyan; Write-Host "==========================================" -ForegroundColor Cyan }
function Write-Ok($text)  { Write-Host "  [OK] $text" -ForegroundColor Green }
function Write-Warn($text){ Write-Host "  [WARN] $text" -ForegroundColor Yellow }
function Write-Err($text) { Write-Host "  [ERR] $text" -ForegroundColor Red }
function Write-Info($text){ Write-Host "  [INFO] $text" -ForegroundColor Gray }

# -----------------------------------------------------------------------------
# 1. Tu dong chuan bi Maven (neu chua co tren may)
# -----------------------------------------------------------------------------
Write-Header "CHUAN BI MAVEN"

if (-not (Test-Path $mvnCmd)) { throw 'Missing backend/mvnw.cmd' }
Write-Ok "Maven Wrapper: $mvnCmd"

# -----------------------------------------------------------------------------
# 2. Kiem tra moi truong (khong bat buoc Maven nua)
# -----------------------------------------------------------------------------
Write-Header "KIEM TRA MOI TRUONG"

function Test-Cmd($name, $cmd) {
  try {
    & $cmd @() 2>$null | Out-Null
    Write-Ok "$name da cai dat"
    return $true
  } catch {
    Write-Err "$name KHONG tim thay ($cmd)"
    return $false
  }
}

$hasJava = Test-Cmd "Java" "java"
$hasNode = Test-Cmd "Node.js" "node"
$hasPnpm = Test-Cmd "pnpm" "pnpm"

if (-not ($hasJava -and $hasNode -and $hasPnpm)) {
  Write-Err "Thieu yeu cau cau truc. Vui long cai Java 21, Node.js, pnpm roi chay lai."
  exit 1
}

# Kiem tra PostgreSQL (co goi pg_isready neu co)
$pgReady = $false
try {
  $res = & pg_isready -h localhost -p 5432 2>$null
  if ($LASTEXITCODE -eq 0) { $pgReady = $true; Write-Ok "PostgreSQL san sang (localhost:5432)" }
  else { Write-Warn "PostgreSQL chua phan hoi tren localhost:5432" }
} catch {
  Write-Warn "Khong tim thay pg_isready. Hay dam bao PostgreSQL dang chay tren port 5432."
}

if (-not $pgReady) {
  Write-Warn "Neu chua co PostgreSQL, hay khoi dong qua Docker Compose (deploy/docker-compose.yml) hoac cai PostgreSQL cu the."
  Write-Info "Backend can PostgreSQL de chay test va phuc vu API."
}

# -----------------------------------------------------------------------------
# 3. Chay test
# -----------------------------------------------------------------------------
Write-Header "CHAY TEST"

Write-Host "--> Backend test (khong can DB)" -ForegroundColor Cyan
$backendTestLog = Join-Path $scriptDir "backend-test.log"
& $mvnCmd test -pl $backendDir -Dtest='DevicePushServiceTest,MetricDictionaryTest,ExportRenderTest,ExportRevokeTest' -Dsurefire.failIfNoSpecifiedTests=false -q > $backendTestLog 2>&1
if ($LASTEXITCODE -eq 0) {
  Write-Ok "Backend test PASS (xem chi tiet: $backendTestLog)"
} else {
  Write-Warn "Backend test co loi (xem chi tiet: $backendTestLog)"
}

Write-Host "--> Frontend test (Vitest)" -ForegroundColor Cyan
$frontendTestLog = Join-Path $scriptDir "frontend-test.log"
& pnpm test > $frontendTestLog 2>&1
if ($LASTEXITCODE -eq 0) {
  Write-Ok "Frontend test PASS (xem chi tiet: $frontendTestLog)"
} else {
  Write-Warn "Frontend test co loi (xem chi tiet: $frontendTestLog)"
}

# -----------------------------------------------------------------------------
# 4. Build production (tuy chon)
# -----------------------------------------------------------------------------
Write-Header "BUILD PRODUCTION (Frontend)"
& pnpm build -c $webDir > $null 2>&1
if ($LASTEXITCODE -eq 0) {
  Write-Ok "Frontend build OK (thu muc: $webDir\dist)"
} else {
  Write-Warn "Frontend build loi - van chay dev server."
}

# -----------------------------------------------------------------------------
# 5. Khoi dong Backend (Spring Boot)
# -----------------------------------------------------------------------------
Write-Header "KHOI DONG BACKEND (Spring Boot :8080)"
$backendLog = Join-Path $scriptDir "backend-run.log"
if (Test-Path $backendLog) { Remove-Item $backendLog -Force }

Start-Process -FilePath $mvnCmd -ArgumentList "spring-boot:run", "-pl", $backendDir -WorkingDirectory $scriptDir -RedirectStandardOutput $backendLog -RedirectStandardError $backendLog

# Doi backend san sang (poll health)
Write-Info "Doi backend khoi dong..."
$backendReady = $false
for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 2
  try {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 3 -ErrorAction Stop
    if ($health.status -eq "UP") { $backendReady = $true; break }
  } catch {}
  Write-Info "   lan $(($i+1))/60..."
}

if ($backendReady) {
  Write-Ok "Backend san sang tai http://localhost:8080"
} else {
  Write-Err "Backend khong khoi dong sau 120 giay. Xem log: $backendLog"
}

# -----------------------------------------------------------------------------
# 6. Khoi dong Frontend (Vite dev server)
# -----------------------------------------------------------------------------
Write-Header "KHOI DONG FRONTEND (Vite :5173)"
$frontendLog = Join-Path $scriptDir "frontend-run.log"
if (Test-Path $frontendLog) { Remove-Item $frontendLog -Force }

Start-Process -FilePath "pnpm.cmd" -ArgumentList "dev" -WorkingDirectory $webDir -RedirectStandardOutput $frontendLog -RedirectStandardError $frontendLog

# Doi frontend san sang
Write-Info "Doi frontend khoi dong..."
$frontendReady = $false
for ($i = 0; $i -lt 30; $i++) {
  Start-Sleep -Seconds 2
  try {
    $res = Invoke-WebRequest -Uri "http://localhost:5173" -TimeoutSec 3 -ErrorAction Stop
    if ($res.StatusCode -eq 200) { $frontendReady = $true; break }
  } catch {}
  Write-Info "   lan $(($i+1))/30..."
}

if ($frontendReady) {
  Write-Ok "Frontend san sang tai http://localhost:5173"
} else {
  Write-Err "Frontend khong khoi dong sau 60 giay. Xem log: $frontendLog"
}

# -----------------------------------------------------------------------------
# 7. Mo trinh duyet
# -----------------------------------------------------------------------------
Write-Header "MO TRINH DUYET"
if ($frontendReady) {
  Start-Process "http://localhost:5173"
  Write-Ok "Da mo trinh duyet: http://localhost:5173"
} else {
  Write-Warn "Frontend chua san sang, khong mo trinh duyet."
}

# -----------------------------------------------------------------------------
# 8. Tom tat
# -----------------------------------------------------------------------------
Write-Header "TOM TAT"
Write-Info "Frontend (UI): http://localhost:5173"
Write-Info "Backend API:   http://localhost:8080/api/v1"
Write-Info "Maven:         $mvnCmd"
Write-Info "Backend log:   $backendLog"
Write-Info "Frontend log:  $frontendLog"
Write-Info "Backend test:  $backendTestLog"
Write-Info "Frontend test: $frontendTestLog"
Write-Host ""
Write-Host "Nhan Ctrl+C de dung tat ca." -ForegroundColor Gray

# Giu session mo (khong thoat script ngay) de nguoi dung thay logs
while ($true) { Start-Sleep -Seconds 60 }
