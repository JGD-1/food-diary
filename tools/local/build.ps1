# The local build robot (replaces .github/workflows/build.yml since 8 Nov 2026, GitHub is gone).
# Runs the tests, saves a changed Room database layout, and builds the installable APK.
# On main it also gives the build the next version number and puts it in the release folder
# that serve.ps1 shares on the home network (food-diary-0.1.N.apk + latest.apk + version.json).
#
#   powershell -ExecutionPolicy Bypass -File tools\local\build.ps1            # tests + APK
#   powershell -ExecutionPolicy Bypass -File tools\local\build.ps1 -NoTests   # APK only
param(
    [switch]$NoTests,
    [string]$Releases = "$HOME\food-diary-releases"
)
$ErrorActionPreference = "Stop"
$repo = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $repo
if ("$repo" -match '\s') { throw "The code folder '$repo' has a space in its path; the database tool (KSP) fails there. Use e.g. C:\Users\guido\food-diary." }

if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Select-Object -First 1
    if (-not $jdk) { throw "Java 17 not found. Install it with: winget install EclipseAdoptium.Temurin.17.JDK" }
    $env:JAVA_HOME = $jdk.FullName
}
$git = "git"
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { $git = "C:\Program Files\Git\cmd\git.exe" }

function Gradle([string[]]$tasks) {
    & .\gradlew.bat @tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $($tasks -join ' ')  (test reports: <module>\build\reports\tests\)" }
}

$branch = (& $git rev-parse --abbrev-ref HEAD).Trim()
$onMain = $branch -eq "main"

# Version number: one counter for all builds on this PC, kept next to the releases (not in git).
# GitHub's last build was about 0.1.19x, so local builds start at 0.1.300.
New-Item -ItemType Directory -Force $Releases | Out-Null
$counterFile = Join-Path $Releases "build-number.txt"
$last = if (Test-Path $counterFile) { [int](Get-Content $counterFile -Raw).Trim() } else { 299 }
$number = $last + 1
$env:GITHUB_RUN_NUMBER = "$number"   # app/build.gradle.kts reads this name for versionCode/versionName

if (-not $NoTests) {
    Write-Host "== Tests"
    Gradle @("testDebugUnitTest", ":core:model:test")

    # Same as the old "Save the database layout" step: commit a changed Room schema.
    & $git add core/data/schemas
    & $git diff --cached --quiet
    if ($LASTEXITCODE -ne 0) {
        & $git commit -q -m "Save Room database schema"
        Write-Host "== Saved a changed Room database schema on $branch"
    }
}

Write-Host "== APK 0.1.$number"
Gradle @(":app:assembleRelease")
$apk = "app\build\outputs\apk\release\app-release.apk"

if (-not $onMain) {
    New-Item -ItemType Directory -Force out | Out-Null
    Copy-Item $apk "out\food-diary-$branch.apk".Replace("/", "-") -Force
    Write-Host "== Built $branch (not released: only main goes to the phones)"
    exit 0
}

$name = "food-diary-0.1.$number.apk"
Copy-Item $apk (Join-Path $Releases $name) -Force
Copy-Item $apk (Join-Path $Releases "latest.apk") -Force
$sha = (Get-FileHash (Join-Path $Releases $name) -Algorithm SHA256).Hash.ToLower()
$commit = (& $git rev-parse --short HEAD).Trim()
$info = [ordered]@{
    versionCode = $number
    versionName = "0.1.$number"
    apk         = $name
    sha256      = $sha
    sizeBytes   = (Get-Item (Join-Path $Releases $name)).Length
    commit      = $commit
    builtAt     = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
}
# Write version.json without a BOM so the phone's JSON reader is happy.
[IO.File]::WriteAllText((Join-Path $Releases "version.json"), ($info | ConvertTo-Json), (New-Object Text.UTF8Encoding $false))
Set-Content $counterFile "$number" -Encoding ascii
& $git tag -f "build-$number" | Out-Null

# Keep the last 5 numbered APKs.
Get-ChildItem $Releases -Filter "food-diary-0.1.*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -Skip 5 | Remove-Item
Write-Host "== Released 0.1.$number ($commit) to $Releases"
