[CmdletBinding()]
param(
  [ValidateRange(1024,65535)]
  [int]$BackendPort = 8081,
  [ValidateRange(1024,65535)]
  [int]$DatabasePort = 55432,
  [switch]$WithStorage,
  [switch]$WithMail,
  [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$infra = Join-Path $root 'infra'
$web = Join-Path $root 'web'
$backend = Join-Path $root 'backend'
$maven = Join-Path $backend 'mvnw.cmd'
$run = Join-Path $root '.run'
New-Item -ItemType Directory -Force -Path $run | Out-Null

function Fail([string]$message) { Write-Error $message; exit 1 }
function Invoke-Docker {
  $ErrorActionPreference = 'Continue'
  & docker @args 2>&1 | ForEach-Object { Write-Host "$_" }
  if ($LASTEXITCODE -ne 0) { throw "Docker command failed (exit $LASTEXITCODE)." }
}
function Need([string]$command, [string]$hint) { if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { Fail "Missing $command. $hint" } }
function Wait-Http([string]$url, [int]$seconds, [string]$name, [System.Diagnostics.Process]$startedProcess) {
  $until = (Get-Date).AddSeconds($seconds)
  while ((Get-Date) -lt $until) {
    if($startedProcess){$startedProcess.Refresh();if($startedProcess.HasExited){Fail "$name exited. Check .run/$($name.ToLower()).log and .run/$($name.ToLower()).err.log."}}
    try { if ((Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 3).StatusCode -eq 200) { return } } catch {}; Start-Sleep -Seconds 2
  }
  Fail "$name did not become ready: $url"
}
function Test-PortFree([int]$port) { if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { Fail "Port $port is already in use." } }
function Test-Backend([string]$url) { try { return (Invoke-RestMethod "$url/actuator/health" -TimeoutSec 3).data.status -eq 'UP' } catch { return $false } }
function Remember-Service([string]$name,[int]$port,[string]$pathMarker) {
  foreach($listener in Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){
    $info=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if($info.CommandLine -like "*$pathMarker*"){
      $info.ProcessId | Set-Content (Join-Path $run "$name.pid")
      break
    }
  }
}

Need docker 'Install/start Docker Desktop.'; Need java 'Install JDK 21.'; Need node 'Install Node.js 20+.'; Need pnpm 'Run: corepack enable; corepack prepare pnpm@9.15.4 --activate'
if (-not (Test-Path $maven)) { Fail 'Missing backend/mvnw.cmd.' }
Invoke-Docker info --format '{{.ServerVersion}}'

$devEnv = Join-Path $infra '.env'
if (-not (Test-Path $devEnv)) { Copy-Item (Join-Path $infra '.env.example') $devEnv; Fail "Created $devEnv. Replace the placeholder secrets, then run this command again." }
if ((Get-Content -Raw $devEnv) -match 'replace-with-a-local') { Fail "Replace placeholder secrets in $devEnv before starting." }
$env:POSTGRES_PORT = "$DatabasePort"

Push-Location $infra
try {
  Invoke-Docker compose -f docker-compose.dev.yml up -d --wait --wait-timeout 60 postgres
  if ($WithStorage) { Invoke-Docker compose -f docker-compose.dev.yml --profile storage up -d minio }
  if ($WithMail) { Invoke-Docker compose -f docker-compose.dev.yml --profile mail up -d mailhog }
  if ($LASTEXITCODE -ne 0) { Fail 'Unable to start local Docker dependencies.' }
  $dbName = ((Get-Content $devEnv | Where-Object { $_ -match '^POSTGRES_DB=' } | Select-Object -First 1) -split '=',2)[1]
  $dbUser = ((Get-Content $devEnv | Where-Object { $_ -match '^POSTGRES_USER=' } | Select-Object -First 1) -split '=',2)[1]
  $dbReady=$false
  $until=(Get-Date).AddSeconds(60); do { try { Invoke-Docker compose -f docker-compose.dev.yml exec -T postgres pg_isready -U $dbUser -d $dbName; $dbReady=$true; break } catch { Start-Sleep 2 } } while((Get-Date)-lt $until)
  if(-not $dbReady){ Fail 'PostgreSQL container is not ready.' }
} finally { Pop-Location }

# A running backend may be unhealthy solely because Docker/Postgres stopped.
# Start its database before deciding whether the listening port is a conflict.
$reuseBackend=Test-Backend "http://127.0.0.1:$BackendPort"
if(-not $reuseBackend){
  $listeners=Get-NetTCPConnection -State Listen -LocalPort $BackendPort -ErrorAction SilentlyContinue
  foreach($listener in $listeners){
    $info=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if($info.CommandLine -like "*$backend\app*"){
      Write-Host "Waiting for existing SmartTask backend on $BackendPort to reconnect to PostgreSQL..."
      Wait-Http "http://127.0.0.1:$BackendPort/actuator/health" 90 'Backend'
      $reuseBackend=Test-Backend "http://127.0.0.1:$BackendPort"
      break
    }
  }
  if(-not $reuseBackend){Test-PortFree $BackendPort}
}
$reuseWeb=$false
try{
  $page=Invoke-WebRequest -UseBasicParsing http://127.0.0.1:5173 -TimeoutSec 3
  $reuseWeb=$page.Content -match 'Smart Task Manager' -and $page.Content -match '/@vite/client'
  $live=Invoke-RestMethod http://127.0.0.1:5173/api/v1/health/liveness -TimeoutSec 3
  if($reuseBackend){$reuseWeb=$reuseWeb -and $live.data.alive -eq $true}
}catch{}
if(-not $reuseWeb){Test-PortFree 5173}

if (-not $SkipInstall -and -not (Test-Path (Join-Path $web 'node_modules'))) { Push-Location $web; try { & pnpm.cmd install --frozen-lockfile; if ($LASTEXITCODE -ne 0) { Fail 'pnpm install failed.' } } finally { Pop-Location } }

$backendLog = Join-Path $run 'backend.log'; $backendErr = Join-Path $run 'backend.err.log'
$webLog = Join-Path $run 'web.log'; $webErr = Join-Path $run 'web.err.log'
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$DatabasePort/$dbName"
$env:SPRING_DATASOURCE_USERNAME = $dbUser
$env:SERVER_PORT = "$BackendPort"
$env:VITE_API_PROXY_TARGET = "http://127.0.0.1:$BackendPort"
$env:SPRING_DATASOURCE_PASSWORD = ((Get-Content $devEnv | Where-Object { $_ -match '^POSTGRES_PASSWORD=' } | Select-Object -First 1) -split '=',2)[1]
if(-not $reuseBackend){
$backendProcess = Start-Process -FilePath $maven -ArgumentList '-pl','app','spring-boot:run' -WorkingDirectory $backend -WindowStyle Hidden -RedirectStandardOutput $backendLog -RedirectStandardError $backendErr -PassThru
$backendProcess.Id | Set-Content (Join-Path $run 'backend.pid')
Wait-Http "http://127.0.0.1:$BackendPort/actuator/health" 120 'Backend' $backendProcess
}else{Write-Host "Reusing healthy SmartTask backend on $BackendPort."}
if(-not $reuseWeb){
$webProcess = Start-Process -FilePath 'pnpm.cmd' -ArgumentList 'dev','--host','127.0.0.1','--strictPort' -WorkingDirectory $web -WindowStyle Hidden -RedirectStandardOutput $webLog -RedirectStandardError $webErr -PassThru
$webProcess.Id | Set-Content (Join-Path $run 'web.pid')
Wait-Http 'http://127.0.0.1:5173' 45 'Vite' $webProcess
}else{Write-Host 'Reusing healthy SmartTask Vite on 5173.'}
$proxyLive=Invoke-RestMethod http://127.0.0.1:5173/api/v1/health/liveness -TimeoutSec 5
if($proxyLive.data.alive -ne $true){Fail 'Vite API proxy is not healthy. Restart Vite for the selected backend port.'}
Remember-Service 'backend' $BackendPort "$backend\app"
Remember-Service 'web' 5173 "$web\node_modules"
@{backendPort=$BackendPort;databasePort=$DatabasePort} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $run 'ports.json')
Write-Host 'SmartTask local development is ready:' -ForegroundColor Green
Write-Host '  Web:     http://localhost:5173'; Write-Host "  API:     http://localhost:$BackendPort/api/v1"; Write-Host "  Health:  http://localhost:$BackendPort/actuator/health"; Write-Host "Logs: $run"; Write-Host 'Stop safely: .\scripts\stop-dev.ps1'
