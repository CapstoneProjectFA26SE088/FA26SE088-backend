# scripts/guard-env.ps1
# Multi-layered .env protection: calculates sha256 hashes of .env* files without ever printing contents.
param(
  [Parameter(Mandatory=$true)]
  [ValidateSet('snapshot', 'check')]
  [string]$Mode
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/..").Path
$snap = Join-Path $env:TEMP 'filmlink-env.sha'

$files = Get-ChildItem -Path $root -Force -File -Filter '.env*' |
  Where-Object { $_.Name -ne '.env.example' } | Sort-Object Name

$now = ($files | ForEach-Object { "$($_.Name):$((Get-FileHash $_.FullName).Hash)" }) -join "`n"
if (-not $now) { $now = 'NO-ENV-FILES' }

if ($Mode -eq 'snapshot') {
  Set-Content -Path $snap -Value $now
  Write-Host "[guard-env] Snapshot recorded." -ForegroundColor Cyan
  exit 0
}

if ($Mode -eq 'check') {
  if (-not (Test-Path $snap)) {
    Write-Host "[guard-env] ERROR: No snapshot found. Run guard-env.ps1 -Mode snapshot first." -ForegroundColor Red
    exit 1
  }
  $recorded = (Get-Content $snap -Raw).Trim()
  if ($recorded -ne $now.Trim()) {
    Write-Host "[guard-env] CRITICAL: .env file was modified or created during execution! ABORTING." -ForegroundColor Red
    exit 1
  }
  Write-Host "[guard-env] Check PASSED: .env integrity intact." -ForegroundColor Green
  exit 0
}
