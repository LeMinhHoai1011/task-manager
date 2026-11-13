[CmdletBinding()]
param([switch]$SkipBackendIntegration)
$ErrorActionPreference='Stop';$root=Split-Path -Parent $PSScriptRoot;$report=Join-Path $root ('.run\test-local-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.md');New-Item -ItemType Directory -Force -Path (Split-Path $report)|Out-Null;$results=@('# SmartTask local validation','',"Generated: $(Get-Date -Format o)",'');$failed=$false
function Run([string]$label,[string]$command,[string]$workdir){Push-Location $workdir;try{Invoke-Expression $command;$ok=$LASTEXITCODE -eq 0}finally{Pop-Location};$script:results += "- ${label}: "+$(if($ok){'PASS'}else{'FAIL'});if(-not $ok){$script:failed=$true}}
if (-not $env:SMARTTASK_F21_DATABASE_URL) { throw 'Set SMARTTASK_F21_DATABASE_URL to an isolated TEST database before running this script.' }
if (-not $SkipBackendIntegration -and -not $env:SMARTTASK_IT_DATABASE_URL) { throw 'Set SMARTTASK_IT_DATABASE_URL to an isolated TEST database.' }
Run 'Web TypeScript typecheck' 'pnpm typecheck' "$root\web"
Run 'Web ESLint' 'pnpm lint' "$root\web"
Run 'Web test' 'pnpm test' "$root\web"
Run 'Web production build' 'pnpm build' "$root\web"
Run 'Backend tests' '.\mvnw.cmd -pl app test' "$root\backend"
if (-not $SkipBackendIntegration) {
  Run 'Backend integration tests (PostgreSQL, MinIO, Docker required)' '.\mvnw.cmd -pl app -Dspring.test.context.cache.maxSize=4 "-Dtest=*IT" test' "$root\backend"
} else {
  $results += '- Backend integration tests: NOT_RUN (explicit SkipBackendIntegration)'
}
$results|Set-Content -Encoding utf8 $report
Write-Host "Report: $report"
if($failed){exit 1}
