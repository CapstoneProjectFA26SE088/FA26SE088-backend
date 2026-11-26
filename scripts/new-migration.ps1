# scripts/new-migration.ps1
# Generates timestamped Flyway migration files: V<yyyyMMdd>_<HHmm>__<domain>_<description>.sql
param(
  [Parameter(Mandatory=$true)][string]$Domain,
  [Parameter(Mandatory=$true)][ValidatePattern('^[a-z][a-z0-9_]*$')][string]$Description,
  [switch]$DryRun,
  [switch]$Force
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/..").Path
$dir = Join-Path $root 'src/main/resources/db/migration'

# Check domains.txt
$domainsFile = Join-Path $PSScriptRoot 'domains.txt'
$domains = Get-Content $domainsFile | Where-Object { $_ -and -not $_.StartsWith('#') }
if ($domains -notcontains $Domain) {
  Write-Host "ERROR: Domain '$Domain' is not in $domainsFile." -ForegroundColor Red
  Write-Host "Allowed domains: $($domains -join ', ')" -ForegroundColor Yellow
  exit 1
}

# Note: In FilmLink FA26SE088, only the leader creates migrations unless specifically instructed by task spec.
Write-Host "NOTICE: Per AGENTS.md, only the leader creates migrations unless authorized by SPEC-00 §6." -ForegroundColor Yellow

$existing = @(Get-ChildItem $dir -Filter 'V*.sql' -ErrorAction SilentlyContinue)
$now = Get-Date

do {
  $version = $now.ToString('yyyyMMdd_HHmm')
  $clash = $existing | Where-Object { $_.Name -like "V${version}__*" }
  if ($clash) { $now = $now.AddMinutes(1) }
} while ($clash)

$latest = $existing | ForEach-Object {
  if ($_.Name -match '^V(\d{8}_\d{4})__') { $Matches[1] }
} | Sort-Object | Select-Object -Last 1

if ($latest -and $latest -gt $version -and -not $Force) {
  Write-Host "ERROR: Existing migration ($latest) has a later timestamp than current ($version). Creating will cause Flyway ordering issues." -ForegroundColor Red
  exit 1
}

$filename = "V${version}__${Domain}_${Description}.sql"
$path = Join-Path $dir $filename

if ($DryRun) {
  Write-Host "[DRY-RUN] Would create: $filename" -ForegroundColor Cyan
  exit 0
}

$header = @"
-- ============================================================================
-- Migration: $filename
-- Domain:    $Domain
-- Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
-- Note:      PostgreSQL 18, timestamptz for Instant, bigint for money (_vnd).
-- ============================================================================

"@

Set-Content -Path $path -Value $header -Encoding UTF8
Write-Host "Created migration file: $path" -ForegroundColor Green
exit 0
