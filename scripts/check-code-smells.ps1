# scripts/check-code-smells.ps1
# Deterministic anti-pattern and smell validator for FilmLink Backend
param(
  [switch]$UpdateBaseline
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/..").Path
$src = Join-Path $root 'src/main'
$baselineFile = Join-Path $PSScriptRoot 'smell-baseline.txt'

$baseline = @()
if (Test-Path $baselineFile) {
  $baseline = @(Get-Content $baselineFile)
}

$rules = @(
  @{ Id = 'S01-DATA-ON-ENTITY';        Path = '[\\/]entity[\\/]';     Pattern = '@Data\b' },
  @{ Id = 'S02-EQUALS-TOSTRING';       Path = '[\\/]entity[\\/]';     Pattern = '@(EqualsAndHashCode|ToString)\b' },
  @{ Id = 'S03-TRY-IN-CONTROLLER';     Path = '[\\/]controller[\\/]'; Pattern = '\btry\s*[\{\(]' },
  @{ Id = 'S04-ASIA-SAIGON';           Path = '\.(java|ya?ml|sql)$';  Pattern = 'Asia/Saigon' },
  @{ Id = 'S05-WORKAROUND-COMMENT';    Path = '\.java$';              Pattern = '(//|/\*).*\b(TODO|FIXME|HACK|XXX|workaround|temporary)\b' },
  @{ Id = 'S06-JAVAX';                 Path = '\.java$';              Pattern = 'import\s+javax\.(persistence|validation|servlet)' },
  @{ Id = 'S07-SCHEDULED-NO-ZONE';     Path = '[\\/]scheduler[\\/]'; Pattern = '@Scheduled\((?![^)]*zone)' },
  @{ Id = 'S08-PRINT';                 Path = '\.java$';              Pattern = 'System\.out|printStackTrace' },
  @{ Id = 'S09-HARDCODED-SECRET';      Path = '\.(ya?ml|properties)$'; Pattern = '(?i)(password|secret|api[-_]?key)\s*[:=]\s*(?!\$\{)[^\s#]+' },
  @{ Id = 'S10-RAW-URL-IN-MIGRATION';  Path = 'db[\\/]migration';     Pattern = "(?i)'https?://" }
)

$files = Get-ChildItem $src -Recurse -File -Include *.java, *.yml, *.yaml, *.properties, *.sql -ErrorAction SilentlyContinue
$found = foreach ($f in $files) {
  $rel = ($f.FullName.Substring($root.Length + 1)) -replace '\\', '/'
  foreach ($r in $rules) {
    if ($f.FullName -notmatch $r.Path) { continue }
    $hit = Select-String -Path $f.FullName -Pattern $r.Pattern | Select-Object -First 1
    if ($hit) {
      [pscustomobject]@{
        Key  = "$($r.Id)|$rel"
        Rule = $r.Id
        File = $rel
        Line = $hit.LineNumber
      }
    }
  }
}
$found = @($found)

if ($UpdateBaseline) {
  $found.Key | Sort-Object -Unique | Set-Content $baselineFile
  Write-Host "Baseline updated: $($found.Count) existing baseline smells recorded in $baselineFile." -ForegroundColor Yellow
  exit 0
}

$new = @($found | Where-Object { $baseline -notcontains $_.Key })
if ($new.Count -gt 0) {
  Write-Host "`nFAILED: Found $($new.Count) NEW code smell violations:" -ForegroundColor Red
  $new | ForEach-Object {
    Write-Host "  [$($_.Rule)] $($_.File):$($_.Line)" -ForegroundColor Red
  }
  exit 1
}

Write-Host "Code smell check PASSED: No new violations." -ForegroundColor Green
exit 0
