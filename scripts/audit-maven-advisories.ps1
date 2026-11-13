param(
  [Parameter(Mandatory=$true)][string]$Graph,
  [Parameter(Mandatory=$true)][string]$OutputFile
)
$ErrorActionPreference = 'Stop'
$queries = @()
foreach ($line in (Get-Content -LiteralPath $Graph)) {
  if ($line -eq '#') { break }
  if ($line -notmatch '^\d+ (\S+)$') { continue }
  $parts = $Matches[1].Split(':')
  if ($parts[-1] -notin @('compile', 'runtime')) { continue }
  $queries += @{
    package = @{ name = $parts[0] + ':' + $parts[1]; ecosystem = 'Maven' }
    version = $parts[-2]
  }
}
if ($queries.Count -eq 0) { throw 'No runtime dependencies parsed; refusing an empty successful scan' }
$body = @{ queries = $queries } | ConvertTo-Json -Depth 5
$response = Invoke-RestMethod -Uri 'https://api.osv.dev/v1/querybatch' -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 120
if ($response.results.Count -ne $queries.Count) { throw 'Incomplete advisory response' }
$findings = @(for ($i = 0; $i -lt $queries.Count; $i++) {
  foreach ($v in $response.results[$i].vulns) {
    [pscustomobject]@{ package = $queries[$i].package.name; version = $queries[$i].version; advisory = $v.id }
  }
})
@{ checked_at = (Get-Date).ToUniversalTime().ToString('o'); source = 'https://api.osv.dev/v1/querybatch'; dependencies = $queries.Count; findings = $findings } |
  ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutputFile -Encoding utf8
Write-Output "Scanned $($queries.Count) runtime dependencies; $($findings.Count) advisory matches. Review applicability before release."
if ($findings.Count -gt 0) { exit 1 }
