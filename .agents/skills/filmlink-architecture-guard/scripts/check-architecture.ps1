# check-architecture.ps1
# Deterministic Architecture & Anti-Pattern Validator for FilmLink Backend
# Based on FilmLink 10 Golden Rules (R1 - R10) and Lauren Tan's deterministic checks.

$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot/../../.."
$javaSrc = Join-Path $root "src/main/java/com/fa26se088/filmlink"

$violations = @()

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Running FilmLink Architecture & Anti-Pattern Guard Checks " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Rule R1: Controller must NOT inject Repository directly
$controllerFiles = Get-ChildItem -Path "$javaSrc/controller" -Recurse -Filter "*.java" -ErrorAction SilentlyContinue
foreach ($file in $controllerFiles) {
    $content = Get-Content $file.FullName -Raw
    if ($content -match 'import\s+com\.fa26se088\.filmlink\.repository\.') {
        $violations += "[R1 Violation] Controller '$($file.Name)' imports a Repository directly. Controllers must only interact with Services."
    }
    if ($content -match '\bprivate\s+(final\s+)?\w+Repository\b') {
        $violations += "[R1 Violation] Controller '$($file.Name)' declares a Repository field. Controllers must only inject Services."
    }
}

# 2. Rule R2 & Anti-Slop: JPA Entities must NOT use @Data
$entityFiles = Get-ChildItem -Path "$javaSrc/entity" -Recurse -Filter "*.java" -ErrorAction SilentlyContinue
foreach ($file in $entityFiles) {
    $content = Get-Content $file.FullName -Raw
    if ($content -match '@Entity\b' -and $content -match '@Data\b') {
        $violations += "[Anti-Slop Violation] Entity '$($file.Name)' uses @Data. Use @Getter and @Setter to prevent hashCode/equals recursion."
    }
}

# 3. Rule R6: Controller must NOT contain try/catch blocks
foreach ($file in $controllerFiles) {
    $lines = Get-Content $file.FullName
    $lineNum = 1
    foreach ($line in $lines) {
        if ($line -match '^\s*catch\s*\(') {
            $violations += "[R6 Violation] Controller '$($file.Name)' line $lineNum contains 'catch'. Let GlobalExceptionHandler handle errors."
        }
        $lineNum++
    }
}

# 4. Anti-Slop: Timezone check (ban Asia/Saigon)
$allJavaFiles = Get-ChildItem -Path "$javaSrc" -Recurse -Filter "*.java" -ErrorAction SilentlyContinue
foreach ($file in $allJavaFiles) {
    $content = Get-Content $file.FullName -Raw
    if ($content -match '"Asia/Saigon"') {
        $violations += "[Timezone Violation] '$($file.Name)' uses 'Asia/Saigon'. Use 'Asia/Ho_Chi_Minh' or UTC."
    }
}

# 5. Rule R10: utils must contain only static methods (no Spring @Component / @Service)
$utilFiles = Get-ChildItem -Path "$javaSrc/utils" -Recurse -Filter "*.java" -ErrorAction SilentlyContinue
foreach ($file in $utilFiles) {
    $content = Get-Content $file.FullName -Raw
    if ($content -match '@(Component|Service|Repository|Controller)\b') {
        $violations += "[R10 Violation] Utility class '$($file.Name)' is marked as a Spring bean. Utils must only contain pure static functions."
    }
}

# 6. Anti-Slop: Excuse / Workaround comments
$workaroundPatterns = @('//\s*bypass', '//\s*quick fix', '//\s*workaround', '//\s*hack')
foreach ($file in $allJavaFiles) {
    $lines = Get-Content $file.FullName
    $lineNum = 1
    foreach ($line in $lines) {
        foreach ($pat in $workaroundPatterns) {
            if ($line -match $pat) {
                $violations += "[Excuse Comment] '$($file.Name)' line $lineNum: '$($line.Trim())'. Replace workarounds with proper architectural fixes."
            }
        }
        $lineNum++
    }
}

# 7. Rule R4: Cross-domain Repository injection in Services
$serviceImplDirs = Get-ChildItem -Path "$javaSrc/service" -Directory -ErrorAction SilentlyContinue
foreach ($domainDir in $serviceImplDirs) {
    $domainName = $domainDir.Name
    $implFiles = Get-ChildItem -Path "$($domainDir.FullName)/impl" -Filter "*.java" -ErrorAction SilentlyContinue
    foreach ($file in $implFiles) {
        $content = Get-Content $file.FullName -Raw
        # Match imports like: import com.fa26se088.filmlink.repository.<otherDomain>.<Other>Repository;
        $matches = [regex]::Matches($content, 'import\s+com\.fa26se088\.filmlink\.repository\.(\w+)\.\w+Repository;')
        foreach ($match in $matches) {
            $importedDomain = $match.Groups[1].Value
            if ($importedDomain -ne $domainName) {
                $violations += "[R4 Violation] Service '$($file.Name)' in domain '$domainName' directly imports repository from domain '$importedDomain'. Use domain '$importedDomain' Service or ApplicationEvent instead."
            }
        }
    }
}

# Report Results
if ($violations.Count -gt 0) {
    Write-Host "`nFAILED: Found $($violations.Count) architecture violations:" -ForegroundColor Red
    foreach ($v in $violations) {
        Write-Host "  - $v" -ForegroundColor Red
    }
    exit 1
} else {
    Write-Host "`nSUCCESS: All architecture guard checks passed! No slop patterns detected." -ForegroundColor Green
    exit 0
}
