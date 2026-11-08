# Remakes the bundled Open Food Facts list (replaces .github/workflows/off-snapshot.yml since 8 Nov 2026).
# Downloads the full Open Food Facts export (about 1 GB, takes a while), keeps the NL/BE products with
# calories, and writes feature/food/src/main/assets/off_nl.tsv.gz. Then commit it on a branch.
#
#   powershell -ExecutionPolicy Bypass -File tools\local\off-snapshot.ps1
$ErrorActionPreference = "Stop"
$repo = Resolve-Path "$PSScriptRoot\..\.."
$python = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $python -or $python -like "*WindowsApps*") { $python = "$env:LOCALAPPDATA\Python\bin\python.exe" }

$download = Join-Path ([IO.Path]::GetTempPath()) "off-export"
New-Item -ItemType Directory -Force $download | Out-Null
$csv = Join-Path $download "en.openfoodfacts.org.products.csv.gz"
Write-Host "== Downloading the Open Food Facts export to $csv"
& curl.exe -fSL --retry 4 -A "FoodDiary-snapshot/1.0 (local build)" -o $csv `
    https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz
if ($LASTEXITCODE -ne 0) { throw "Download failed" }

$out = Join-Path $repo "feature\food\src\main\assets\off_nl.tsv.gz"
Write-Host "== Filtering"
& $python -I (Join-Path $repo "feature\food\tools\off\make_snapshot.py") $csv $out
if ($LASTEXITCODE -ne 0) { throw "make_snapshot.py failed" }
Get-Item $out | Select-Object Name, Length
Remove-Item $csv
Write-Host "== Done. Commit feature/food/src/main/assets/off_nl.tsv.gz on a branch."
