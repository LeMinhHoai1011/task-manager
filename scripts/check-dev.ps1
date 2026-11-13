[CmdletBinding()]
param([ValidateRange(1024,65535)][int]$BackendPort=8081)
$ErrorActionPreference='Continue'; $root=Split-Path -Parent $PSScriptRoot; $allOk=$true
if(-not $PSBoundParameters.ContainsKey('BackendPort') -and (Test-Path "$root\.run\ports.json")){$BackendPort=(Get-Content "$root\.run\ports.json" -Raw|ConvertFrom-Json).backendPort}
function Check([string]$name,[scriptblock]$probe){try{$ok=& $probe;if($ok -eq $true){Write-Host "[PASS] $name" -ForegroundColor Green;return}}catch{};Write-Host "[FAIL] $name" -ForegroundColor Red;$script:allOk=$false}
Check 'Docker daemon' { docker info 2>&1 | Out-Null; return $LASTEXITCODE -eq 0 }
Check 'PostgreSQL container' { docker inspect --format '{{.State.Health.Status}}' smarttask-dev-postgres-1 2>$null | ForEach-Object { $_ -eq 'healthy' } }
Check 'Backend health' { $r=Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$BackendPort/actuator/health" -TimeoutSec 5; $r.Content -match '"status":"UP"' }
Check 'Frontend Vite' { (Invoke-WebRequest -UseBasicParsing http://127.0.0.1:5173 -TimeoutSec 5).StatusCode -eq 200 }
Check 'API liveness' { (Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$BackendPort/api/v1/health/liveness" -TimeoutSec 5).StatusCode -eq 200 }
Check 'Vite API proxy' { (Invoke-RestMethod http://127.0.0.1:5173/api/v1/health/liveness -TimeoutSec 5).data.alive -eq $true }
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object LocalPort -in 5173,$BackendPort,55432 | Select-Object LocalAddress,LocalPort,OwningProcess | Format-Table
if($allOk){Write-Host 'Development environment is healthy.' -ForegroundColor Green;exit 0};Write-Host 'Development environment has failures. Inspect .run logs and Docker logs.' -ForegroundColor Yellow;exit 1
