param(
    [ValidateSet("up", "down", "restart", "status", "logs")]
    [string]$Action = "up",

    [switch]$ResetData
)

$ErrorActionPreference = "Stop"
$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$composeDir = Join-Path $rootDir "deploy"
$composeFile = Join-Path $composeDir "docker-compose.yml"
$envFile = Join-Path $composeDir ".env"

function Write-Step {
    param([string]$Message)
    Write-Host "`n[SmartTask] $Message" -ForegroundColor Cyan
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker chua duoc cai dat hoac chua co trong PATH."
    exit 1
}

docker compose version *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker Compose v2 khong kha dung. Hay cai Docker Desktop va bat Compose v2."
    exit 1
}

if (-not (Test-Path $envFile)) {
    $envExample = Join-Path $composeDir ".env.example"
    if (-not (Test-Path $envExample)) {
        Write-Error "Khong tim thay deploy/.env.example."
        exit 1
    }

    Write-Step "Tao deploy/.env tu deploy/.env.example"
    Copy-Item $envExample $envFile
}

Push-Location $composeDir
try {
    switch ($Action) {
        "up" {
            Write-Step "Build va khoi dong toan bo he thong"
            if ($ResetData) {
                Write-Step "Xoa du lieu Docker local de tao lai PostgreSQL va MinIO"
                docker compose -f $composeFile down -v
                if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            }
            docker compose -f $composeFile up -d --build
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

            Write-Step "Trang thai cac container"
            docker compose -f $composeFile ps

            Write-Step "Cho cac service san sang"
            $ready = $false
            for ($attempt = 1; $attempt -le 60; $attempt++) {
                try {
                    docker compose -f $composeFile exec -T backend curl -fsS http://localhost:8080/actuator/health *> $null
                    if ($LASTEXITCODE -eq 0) {
                        $ready = $true
                        break
                    }
                } catch {}

                Start-Sleep -Seconds 2
            }

            if ($ready) {
                Write-Host "`nSmartTask da san sang: http://localhost" -ForegroundColor Green
            } else {
                Write-Warning "Web da khoi dong nhung backend chua tra loi health check."
                Write-Host "Xem log bang: .\run-all.ps1 logs"
            }
        }
        "down" {
            Write-Step "Dung va xoa cac container"
            docker compose -f $composeFile down
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
        "restart" {
            Write-Step "Khoi dong lai toan bo he thong"
            docker compose -f $composeFile restart
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
            docker compose -f $composeFile ps
        }
        "status" {
            docker compose -f $composeFile ps
        }
        "logs" {
            docker compose -f $composeFile logs -f --tail=200
        }
    }
} finally {
    Pop-Location
}
