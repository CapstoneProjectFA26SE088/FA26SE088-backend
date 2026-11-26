# scripts/verify.ps1
# Multi-stage verification runner for FilmLink Backend (FA26SE088)
# Generates verification artifact target/verification/summary.txt

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/..").Path
Set-Location $root

$outDir = Join-Path $root 'target/verification'
New-Item -ItemType Directory -Force $outDir | Out-Null
$summary = Join-Path $outDir 'summary.txt'

"============================================================" | Set-Content $summary
"FilmLink Backend Verification - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" | Add-Content $summary
"============================================================" | Add-Content $summary

function Step([string]$name, [scriptblock]$body) {
  Write-Host "`n>>> Running: $name" -ForegroundColor Cyan
  $global:LASTEXITCODE = 0
  try {
    & $body
  } catch {
    Write-Host "Error executing $name: $_" -ForegroundColor Red
    $global:LASTEXITCODE = 1
  }

  $ok = ($LASTEXITCODE -eq 0)
  $statusText = if ($ok) { 'PASS' } else { 'FAIL' }
  $color = if ($ok) { 'Green' } else { 'Red' }
  Write-Host ">>> Result: $name -> $statusText" -ForegroundColor $color

  ("{0,-45} {1}" -f $name, $statusText) | Add-Content $summary
  if (-not $ok) {
    Write-Host "`nVERIFICATION FAILED at stage: $name" -ForegroundColor Red
    Write-Host "Review detailed log and $summary" -ForegroundColor Red
    exit 1
  }
}

# Step R0: .env snapshot
Step 'R0: Guard .env Snapshot' {
  & "$PSScriptRoot/guard-env.ps1" -Mode snapshot
}

# Step R1: Compile & Checkstyle
Step 'R1: Compile & Checkstyle' {
  & ./mvnw.cmd -B -q compile checkstyle:check
}

# Step R2: Anti-pattern & Code Smell Scanner
Step 'R2: Code Smells & Anti-patterns' {
  & "$PSScriptRoot/check-code-smells.ps1"
}

# Step R3: Unit Tests
Step 'R3: Unit Tests' {
  & ./mvnw.cmd -B test
}

# Step R4: Integration Tests & Verification
Step 'R4: Integration Tests & Verify' {
  & ./mvnw.cmd -B verify
}

# Step R5: .env Integrity Verification
Step 'R5: Guard .env Check' {
  & "$PSScriptRoot/guard-env.ps1" -Mode check
}

# Summary details
"" | Add-Content $summary
"--- Git Status ---" | Add-Content $summary
git status --short | Add-Content $summary
"" | Add-Content $summary
"STATUS: ALL VERIFICATION STAGES PASSED SUCCESSFULLY" | Add-Content $summary

Write-Host "`n============================================================" -ForegroundColor Green
Write-Host " VERIFICATION PASSED! Summary written to $summary" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
exit 0
