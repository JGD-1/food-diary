# The home update server (replaces GitHub Releases since 8 Nov 2026).
# Shares the release folder on the home network only, read-only:
#   http://<this PC's address>:8787/version.json   newest version number + APK name
#   http://<this PC's address>:8787/latest.apk      the newest APK
# The first time, Windows asks whether Python may use the network: tick "Private networks" only.
#
#   powershell -ExecutionPolicy Bypass -File tools\local\serve.ps1
param(
    [int]$Port = 8787,
    [string]$Releases = "$HOME\food-diary-releases"
)
$ErrorActionPreference = "Stop"
$python = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $python -or $python -like "*WindowsApps*") { $python = "$env:LOCALAPPDATA\Python\bin\python.exe" }
if (-not (Test-Path $python)) { throw "Python not found. Install it with: winget install Python.Python.3.13" }
New-Item -ItemType Directory -Force $Releases | Out-Null

$ip = (Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway } | Select-Object -First 1).IPv4Address.IPAddress
Write-Host "Serving $Releases"
Write-Host "On the phone: http://${ip}:$Port/latest.apk   (version: http://${ip}:$Port/version.json)"
Write-Host "Stop with Ctrl+C."
& $python -I "$PSScriptRoot\serve.py" $Releases $Port
