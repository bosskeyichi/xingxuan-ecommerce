#Requires -Version 5.1
$ErrorActionPreference = "Stop"

$env:JAVA_HOME = [Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine")
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = [Environment]::GetEnvironmentVariable("JAVA_HOME", "User") }
$mp = [Environment]::GetEnvironmentVariable("PATH", "Machine")
$up = [Environment]::GetEnvironmentVariable("PATH", "User")
$env:Path = "$mp;$up"

function Write-Step { param([string]$m) Write-Host ""; Write-Host "========================================" -ForegroundColor Cyan; Write-Host "  $m" -ForegroundColor Cyan; Write-Host "========================================" -ForegroundColor Cyan }
function Write-OK   { param([string]$m) Write-Host "  [OK] $m" -ForegroundColor Green }
function Write-Skip { param([string]$m) Write-Host "  [SKIP] $m" -ForegroundColor DarkGray }
function Write-Warn { param([string]$m) Write-Host "  [WARN] $m" -ForegroundColor Yellow }
function Write-Fail { param([string]$m) Write-Host "  [FAIL] $m" -ForegroundColor Red }

function Test-IsAdmin {
    $cu = [Security.Principal.WindowsIdentity]::GetCurrent()
    $pr = New-Object Security.Principal.WindowsPrincipal($cu)
    return $pr.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-JavaMajor {
    try {
        $old = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $o = & java -version 2>&1
        $ErrorActionPreference = $old
        foreach ($l in $o) {
            if ($l -match 'version "(\d+)(?:\.(\d+))?') {
                $v = [int]$Matches[1]
                if ($v -eq 1 -and $Matches[2]) { return [int]$Matches[2] }
                return $v
            }
        }
    } catch {}
    return 0
}

function Get-NodeMajor {
    try {
        $old = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $o = & node -v 2>&1
        $ErrorActionPreference = $old
        if ($o -match 'v?(\d+)') { return [int]$Matches[1] }
    } catch {}
    return 0
}

function Download-File {
    param(
        [Parameter(Mandatory)] [string]$Url,
        [Parameter(Mandatory)] [string]$OutFile,
        [Parameter(Mandatory)] [string]$Description
    )
    Write-Host "  Downloading $Description ..."
    Write-Host "  URL: $Url"
    try {
        Invoke-WebRequest -Uri $Url -OutFile $OutFile -UseBasicParsing -TimeoutSec 300
        Write-OK "Downloaded $Description"
        return $true
    } catch {
        Write-Warn "Download failed: $($_.Exception.Message)"
        return $false
    }
}

function Add-ToUserPath {
    param([string]$PathToAdd)
    $currentUserPath = [Environment]::GetEnvironmentVariable("PATH", "User")
    if (-not $currentUserPath) { $currentUserPath = "" }
    $parts = $currentUserPath -split ';' | Where-Object { $_ -and $_.Trim() -ne '' }
    if ($parts -contains $PathToAdd) { return }
    $newPath = @($PathToAdd) + $parts
    [Environment]::SetEnvironmentVariable("PATH", ($newPath -join ';'), "User")
    $env:Path = [Environment]::GetEnvironmentVariable("PATH", "Machine") + ";" + [Environment]::GetEnvironmentVariable("PATH", "User")
}

function Install-JdkViaWinget {
    Write-Host "  Trying winget ..."
    try {
        winget install --id Microsoft.OpenJDK.17 -e --accept-source-agreements --accept-package-agreements 2>&1 | ForEach-Object { Write-Host "    $_" }
        if ($LASTEXITCODE -eq 0) { Write-OK "winget JDK installed"; return $true }
    } catch {}
    Write-Warn "winget failed or unavailable"
    return $false
}

function Install-JdkZipPortable {
    $toolsDir = Join-Path $env:USERPROFILE "tools"
    if (-not (Test-Path $toolsDir)) { New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null }

    $jdkVersion = "17.0.20.1"
    $jdkBuild   = "1"
    $jdkZipName = "OpenJDK17U-jdk_x64_windows_hotspot_${jdkVersion}_${jdkBuild}.zip"
    $jdkDirName = "jdk-${jdkVersion}+${jdkBuild}"

    $urls = @(
        "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/$jdkZipName",
        "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-${jdkVersion}%2B${jdkBuild}/$jdkZipName",
        "https://aka.ms/jdk-17-windows-x64-zip"
    )

    $zipPath = Join-Path $env:TEMP $jdkZipName
    $ok = $false
    foreach ($u in $urls) {
        if (Download-File -Url $u -OutFile $zipPath -Description "JDK 17") { $ok = $true; break }
    }
    if (-not $ok) { Write-Fail "All JDK 17 download URLs failed"; return $false }

    Write-Host "  Extracting JDK 17 to $toolsDir ..."
    try {
        Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
        Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
    } catch { Write-Fail "Extraction failed: $($_.Exception.Message)"; return $false }

    $jdkHome = Join-Path $toolsDir $jdkDirName
    if (-not (Test-Path $jdkHome)) {
        $found = Get-ChildItem $toolsDir -Directory -Filter "jdk-17*" | Select-Object -First 1
        if ($found) { $jdkHome = $found.FullName }
        else { Write-Fail "JDK folder not found after extraction"; return $false }
    }

    [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkHome, "User")
    $env:JAVA_HOME = $jdkHome
    Add-ToUserPath -PathToAdd (Join-Path $jdkHome "bin")
    Write-OK "JDK 17 installed portable at $jdkHome (User PATH + JAVA_HOME)"
    return $true
}

function Ensure-Java17 {
    Write-Step "Step 1/4: Checking JDK 17+"
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"

    $major = Get-JavaMajor
    if ($major -ge 17) {
        $v = (& java -version 2>&1 | Select-Object -First 1)
        Write-OK "JDK OK: $v"
        $ErrorActionPreference = $old
        return $true
    }

    Write-Warn "Current JDK major: $major, need 17+"
    $ErrorActionPreference = $old

    $hasWinget = Get-Command winget -ErrorAction SilentlyContinue
    $hasChoco  = Get-Command choco  -ErrorAction SilentlyContinue

    if ($hasWinget) {
        if (Install-JdkViaWinget) { return $true }
    }

    if (Install-JdkZipPortable) { return $true }

    if ($hasChoco -and (Test-IsAdmin)) {
        Write-Host "  Falling back to Chocolatey ..."
        choco install microsoft-openjdk17 -y --no-progress 2>&1 | ForEach-Object { Write-Host "    $_" }
        if ($LASTEXITCODE -eq 0) {
            Write-OK "Chocolatey JDK installed"
            return $true
        }
    }

    Write-Fail "Could not install JDK 17 automatically."
    Write-Host "  Manual: https://adoptium.net/temurin/releases/?version=17"
    return $false
}

function Install-NodeViaWinget {
    Write-Host "  Trying winget ..."
    try {
        winget install --id OpenJS.NodeJS.LTS -e --accept-source-agreements --accept-package-agreements 2>&1 | ForEach-Object { Write-Host "    $_" }
        if ($LASTEXITCODE -eq 0) { Write-OK "winget Node.js installed"; return $true }
    } catch {}
    Write-Warn "winget failed or unavailable"
    return $false
}

function Install-NodeZipPortable {
    $toolsDir = Join-Path $env:USERPROFILE "tools"
    if (-not (Test-Path $toolsDir)) { New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null }

    $nodeVer = "v20.18.0"
    $nodeZip = "node-$nodeVer-win-x64.zip"
    $nodeDir = "node-$nodeVer-win-x64"

    $urls = @(
        "https://npmmirror.com/mirrors/node/$nodeVer/$nodeZip",
        "https://mirrors.tuna.tsinghua.edu.cn/nodejs-release/$nodeVer/$nodeZip",
        "https://nodejs.org/dist/$nodeVer/$nodeZip"
    )

    $zipPath = Join-Path $env:TEMP $nodeZip
    $ok = $false
    foreach ($u in $urls) {
        if (Download-File -Url $u -OutFile $zipPath -Description "Node.js $nodeVer") { $ok = $true; break }
    }
    if (-not $ok) { Write-Fail "All Node.js download URLs failed"; return $false }

    Write-Host "  Extracting Node.js to $toolsDir ..."
    try {
        Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
        Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
    } catch { Write-Fail "Extraction failed: $($_.Exception.Message)"; return $false }

    $nodeHome = Join-Path $toolsDir $nodeDir
    if (-not (Test-Path $nodeHome)) {
        $found = Get-ChildItem $toolsDir -Directory -Filter "node-*-win-x64" | Select-Object -First 1
        if ($found) { $nodeHome = $found.FullName }
        else { Write-Fail "Node.js folder not found after extraction"; return $false }
    }

    Add-ToUserPath -PathToAdd $nodeHome
    Write-OK "Node.js installed portable at $nodeHome (User PATH)"
    return $true
}

function Ensure-Node18 {
    Write-Step "Step 2/4: Checking Node.js 18+"
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"

    $major = Get-NodeMajor
    if ($major -ge 18) {
        Write-OK "Node.js OK: $((& node -v))"
        $ErrorActionPreference = $old
        return $true
    }

    Write-Warn "Node.js v$major, need v18+"
    $ErrorActionPreference = $old

    $hasWinget = Get-Command winget -ErrorAction SilentlyContinue
    $hasChoco  = Get-Command choco  -ErrorAction SilentlyContinue

    if ($hasWinget) {
        if (Install-NodeViaWinget) { return $true }
    }

    if (Install-NodeZipPortable) { return $true }

    if ($hasChoco -and (Test-IsAdmin)) {
        Write-Host "  Falling back to Chocolatey ..."
        choco install nodejs-lts -y --no-progress 2>&1 | ForEach-Object { Write-Host "    $_" }
        if ($LASTEXITCODE -eq 0) {
            Write-OK "Chocolatey Node.js installed"
            return $true
        }
    }

    Write-Fail "Could not install Node.js automatically."
    Write-Host "  Manual: https://nodejs.org/"
    return $false
}

function Install-Maven {
    Write-Step "Step 3/4: Downloading Maven dependencies (Aliyun mirror)"
    $mvn = Join-Path $PSScriptRoot "backend\maven\bin\mvn.cmd"
    if (-not (Test-Path $mvn)) { Write-Fail "mvn.cmd not found: $mvn"; return $false }

    Push-Location (Join-Path $PSScriptRoot "backend")
    try {
        & $mvn dependency:resolve -q 2>&1 | ForEach-Object { Write-Host "  $_" }
        if ($LASTEXITCODE -eq 0) { Write-OK "Maven dependencies ready"; return $true }
        Write-Fail "Maven failed (exit $LASTEXITCODE)"
        return $false
    } finally { Pop-Location }
}

function Install-Npm {
    Write-Step "Step 4/4: Installing npm dependencies (npmmirror.com)"

    $failed = $false
    foreach ($dirName in @("frontend-user", "frontend-admin")) {
        $dir = Join-Path $PSScriptRoot $dirName
        if (-not (Test-Path $dir)) { Write-Skip "$dirName not found"; continue }
        if (-not (Test-Path (Join-Path $dir "package.json"))) { Write-Skip "$dirName no package.json"; continue }

        $nm = Join-Path $dir "node_modules"
        if ((Test-Path $nm) -and ((Get-ChildItem $nm -ErrorAction SilentlyContinue).Count -gt 10)) {
            Write-Skip "$dirName/node_modules exists"
            continue
        }

        Write-Host "Installing $dirName ..."
        Push-Location $dir
        try {
            & npm.cmd install --no-audit --no-fund 2>&1 | ForEach-Object { Write-Host "  $_" }
            if ($LASTEXITCODE -eq 0) { Write-OK "$dirName done" } else { Write-Fail "$dirName failed"; $failed = $true }
        } finally { Pop-Location }
    }
    return -not $failed
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Ecommerce System - Fast Setup" -ForegroundColor Cyan
Write-Host "  (winget > portable zip > chocolatey)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-IsAdmin)) {
    Write-Warn "Not running as Administrator. Will try portable (zip) installs instead."
    Write-Host ""
}

$results = @()
$results += [PSCustomObject]@{ Step = "JDK 17+"; OK = (Ensure-Java17) }
$results += [PSCustomObject]@{ Step = "Node.js 18+"; OK = (Ensure-Node18) }

if (-not $results[0].OK -or -not $results[1].OK) {
    Write-Warn "Core env missing. Aborting dependency install."
    Write-Host ""
    $results | ForEach-Object { $s = if ($_.OK) {"OK"} else {"FAIL"}; Write-Host "  [$s] $($_.Step)" }
    exit 1
}

$results += [PSCustomObject]@{ Step = "Maven deps (Aliyun)"; OK = (Install-Maven) }
$results += [PSCustomObject]@{ Step = "npm deps (npmmirror)"; OK = (Install-Npm) }

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
foreach ($r in $results) {
    $icon = if ($r.OK) { "[OK]" } else { "[FAIL]" }
    $color = if ($r.OK) { "Green" } else { "Red" }
    Write-Host "  $icon $($r.Step)" -ForegroundColor $color
}

$allOk = ($results | Where-Object { -not $_.OK }).Count -eq 0
Write-Host ""
if ($allOk) {
    Write-Host "All done!" -ForegroundColor Green
    Write-Host ""
    Write-Host "To start:"
    Write-Host "  Window 1: cd backend     ; .\maven\bin\mvn.cmd spring-boot:run"
    Write-Host "  Window 2: cd frontend-user ; npm run dev"
    Write-Host "  Window 3: cd frontend-admin; npm run dev"
} else {
    Write-Warn "Some steps failed. Check logs above."
}
Write-Host ""
