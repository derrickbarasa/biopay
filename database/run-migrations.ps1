<#
  run-migrations.ps1 -- applies every database/migrations/*.sql to your MSSQL, in
  order, using sqlcmd (which understands the `GO` batch separators these scripts use).
  All migrations are idempotent (guarded with existence checks), so re-running is safe.

  Usage (from the repo root or anywhere):
    powershell -ExecutionPolicy Bypass -File database\run-migrations.ps1 `
      -Server "localhost,1433" -Database "biopay" -User "sa" -Password "YOUR_PASSWORD"

  Add -IncludeSeed to also load database/seed/001_seed_data.sql (demo anchor/org/users).
  Requires sqlcmd (ships with the free "SQL Server Command Line Utilities", or SSMS).
#>
param(
  [Parameter(Mandatory = $true)][string]$Server,
  [Parameter(Mandatory = $true)][string]$Database,
  [Parameter(Mandatory = $true)][string]$User,
  [Parameter(Mandatory = $true)][string]$Password,
  [switch]$IncludeSeed
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not (Get-Command sqlcmd -ErrorAction SilentlyContinue)) {
  Write-Error "sqlcmd not found. Install the SQL Server Command Line Utilities, or run the .sql files in SSMS / Azure Data Studio instead."
  exit 1
}

$files = Get-ChildItem "$root\migrations\*.sql" | Sort-Object Name
foreach ($f in $files) {
  Write-Host "==> Applying $($f.Name)" -ForegroundColor Cyan
  sqlcmd -S $Server -d $Database -U $User -P $Password -C -b -i $f.FullName
  if ($LASTEXITCODE -ne 0) { Write-Error "Failed on $($f.Name) (exit $LASTEXITCODE)"; exit 1 }
}

if ($IncludeSeed) {
  $seed = "$root\seed\001_seed_data.sql"
  if (Test-Path $seed) {
    Write-Host "==> Applying seed 001_seed_data.sql" -ForegroundColor Cyan
    sqlcmd -S $Server -d $Database -U $User -P $Password -C -b -i $seed
    if ($LASTEXITCODE -ne 0) { Write-Error "Seed failed (exit $LASTEXITCODE)"; exit 1 }
  }
}

Write-Host "All migrations applied successfully." -ForegroundColor Green
