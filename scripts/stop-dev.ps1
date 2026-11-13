[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'; $root = Split-Path -Parent $PSScriptRoot; $run = Join-Path $root '.run'
function Stop-Tree([int]$processId){
  foreach($child in Get-CimInstance Win32_Process -Filter "ParentProcessId=$processId") { Stop-Tree $child.ProcessId }
  Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
}
foreach ($name in 'web','backend') {
  $pidFile=Join-Path $run "$name.pid"
  if(Test-Path $pidFile){
    $processId=[int](Get-Content $pidFile -Raw)
    $processInfo=Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
    if($processInfo -and $processInfo.CommandLine -and ($processInfo.CommandLine -like "*$root*")){
      Stop-Tree $processId
      Write-Host "Stopped $name (PID $processId and children)."
    }
    Remove-Item -LiteralPath $pidFile -Force
  }
}
$ErrorActionPreference='Continue'
Push-Location (Join-Path $root 'infra'); try { & docker compose -f docker-compose.dev.yml --profile storage --profile mail stop postgres minio mailhog 2>&1 | ForEach-Object {Write-Host "$_"}; if($LASTEXITCODE -ne 0){exit $LASTEXITCODE} } finally { Pop-Location }
Write-Host 'Local services stopped. Docker volumes and project data were kept.' -ForegroundColor Green
