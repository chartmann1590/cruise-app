#Requires -Version 5.1
<#
.SYNOPSIS
  Store Play Console service-account JSON as GitHub Secret PLAY_SERVICE_ACCOUNT_JSON.

.DESCRIPTION
  After you create the service account in Cloud Console (see play-store/PLAY_SERVICE_ACCOUNT_SETUP.md),
  you download a JSON key. This script uploads it securely via `gh`:

    .\scripts\set-play-service-account.ps1 -JsonPath .\play-service-account.json
    .\scripts\set-play-service-account.ps1 -JsonPath "C:\Users\you\Downloads\cruise-app-2026-abc123.json"

  The JSON is never committed (gitignored). The secret is masked in GitHub.

.PARAMETER JsonPath
  Path to the downloaded service-account JSON file.

.PARAMETER Repo
  GitHub repo slug (default chartmann1590/cruise-app).

.EXAMPLE
  .\scripts\set-play-service-account.ps1 -JsonPath .\play-service-account.json
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string]$JsonPath,
  [string]$Repo = "chartmann1590/cruise-app"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $JsonPath)) { throw "JSON not found: $JsonPath" }
$json = Get-Content -LiteralPath $JsonPath -Raw
if (-not $json.Trim().StartsWith("{")) { throw "File does not look like JSON: $JsonPath" }

# Validate required fields without logging secrets
try {
  $obj = $json | ConvertFrom-Json
  foreach ($k in @("type","project_id","private_key","client_email")) {
    if (-not $obj.PSObject.Properties[$k] -or [string]::IsNullOrWhiteSpace($obj.$k)) { throw "Missing JSON field: $k" }
  }
  if ($obj.type -ne "service_account") { throw "type is not service_account" }
  if ($obj.project_id -ne "cruise-app-2026") { Write-Warning "project_id is $($obj.project_id), expected cruise-app-2026 — continuing" }
  Write-Host "Validated service account: $($obj.client_email) (project $($obj.project_id))" -ForegroundColor Green
} catch {
  throw "JSON validation failed: $_"
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw "gh CLI not found. Install https://cli.github.com/ and run gh auth login" }

Write-Host "Setting secret PLAY_SERVICE_ACCOUNT_JSON on $Repo ..." -ForegroundColor Cyan
# Use stdin so JSON special chars don't need escaping
$json | gh secret set PLAY_SERVICE_ACCOUNT_JSON --repo $Repo
if ($LASTEXITCODE -ne 0) { throw "gh secret set failed (exit $LASTEXITCODE)" }

Write-Host "Verifying..." -ForegroundColor Cyan
gh secret list --repo $Repo | Out-String | Write-Host

Write-Host ""
Write-Host "Done. You can now delete the local JSON:" -ForegroundColor Green
Write-Host "  Remove-Item -LiteralPath `"$JsonPath`" -Force"
Write-Host ""
Write-Host "Test: GitHub → Actions → Deploy to Play Console (Internal Testing) → Run workflow (track=internal)"
