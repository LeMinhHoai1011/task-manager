[CmdletBinding()]
param([ValidateRange(1024,65535)][int]$DatabasePort=55432)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$output=Join-Path $root '.run\local-demo'
New-Item -ItemType Directory -Force -Path $output|Out-Null
$config=@{}
foreach($line in Get-Content -LiteralPath (Join-Path $root 'infra\.env')) {
  if($line -match '^([A-Z_]+)=(.*)$'){$config[$Matches[1]]=$Matches[2]}
}
foreach($name in @('POSTGRES_DB','POSTGRES_USER','POSTGRES_PASSWORD')) {if(-not $config[$name]){throw "Missing $name in infra/.env"}}
# Verify the already-running dev container; never fall back to a different DB.
$env:POSTGRES_PORT="$DatabasePort"
$published=& docker compose --project-directory (Join-Path $root 'infra') -f (Join-Path $root 'infra\docker-compose.dev.yml') port postgres 5432
if($LASTEXITCODE -ne 0 -or $published.Trim() -notin @("127.0.0.1:$DatabasePort","[::1]:$DatabasePort")){throw 'Expected loopback smarttask-dev PostgreSQL port is not running'}
if(-not $env:JAVA_HOME){throw 'Set JAVA_HOME to JDK 21 first'}
$java=Join-Path $env:JAVA_HOME 'bin\java.exe'
$classpath=Join-Path $output 'runtime-classpath.txt'
Push-Location (Join-Path $root 'backend')
try {
  if(-not (Test-Path 'app\target\classes\vn\smarttask\SmartTaskApplication.class')) {
    & .\mvnw.cmd -B -pl app compile
    if($LASTEXITCODE -ne 0){throw 'Backend compile failed'}
  }
  & .\mvnw.cmd -B -o -pl app dependency:build-classpath "-Dmdep.outputFile=$classpath" -DincludeScope=runtime *> (Join-Path $output 'classpath.log')
  if($LASTEXITCODE -ne 0){throw "Classpath preparation failed. See $output\classpath.log"}
} finally {Pop-Location}
$seedVariables=@('SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','SMARTTASK_LOCAL_DEMO_CONFIRM','SMARTTASK_LOCAL_DEMO_PORT','SMARTTASK_LOCAL_DEMO_OUTPUT')
$saved=@{}
foreach($name in $seedVariables){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try {
  $env:SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$DatabasePort/$($config.POSTGRES_DB)"
  $env:SPRING_DATASOURCE_USERNAME=$config.POSTGRES_USER
  $env:SPRING_DATASOURCE_PASSWORD=$config.POSTGRES_PASSWORD
  $env:SMARTTASK_LOCAL_DEMO_CONFIRM='1'
  $env:SMARTTASK_LOCAL_DEMO_PORT="$DatabasePort"
  $env:SMARTTASK_LOCAL_DEMO_OUTPUT=Join-Path $output 'fixtures.json'
  $cp=(Join-Path $root 'backend\app\target\classes')+';'+(Get-Content -LiteralPath $classpath -Raw).Trim()
  & $java --class-path $cp (Join-Path $root 'scripts\demo\SeedLocalDemo.java') *> (Join-Path $output 'seed.log')
  if($LASTEXITCODE -ne 0){throw "Demo seed failed; see $output\seed.log. No existing account/password is reset."}
} finally {foreach($name in $seedVariables){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}}
Write-Host 'Demo ready at http://localhost:5173 (4 accounts, 4 project tasks, 4 subtasks, 4 private tasks/account).'
Write-Host "Fixtures: $output\fixtures.json"
