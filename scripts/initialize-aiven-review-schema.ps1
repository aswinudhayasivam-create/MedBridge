# MedBridge: guarded one-time schema initialization for Aiven review database.
# Requires Docker Desktop, Aiven MySQL CA PEM and PowerShell.
# Example:
# .\scripts\initialize-aiven-review-schema.ps1 -AivenHostName "HOST_FROM_AIVEN" -AivenPort 12920 -CaCertificatePath "C:\private\ca.pem" -Apply
# This never creates a database, database user or application account.
[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string]$AivenHostName,
  [Parameter(Mandatory=$true)][ValidateRange(1,65535)][int]$AivenPort,
  [Parameter(Mandatory=$true)][string]$CaCertificatePath,
  [string]$AivenAdminUser='avnadmin',
  [switch]$Apply
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$database='medbridgereview20261026'
$repo=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$resources=Join-Path $repo 'MedBridge-Phase-1\medbridge\backend\src\main\resources'
$files=@('schema.sql','demo-schema.sql','review-schema.sql')
$tables=@('users','ngo_profiles','medicines','requests','notifications',
          'demo_medicine_details','demo_photos','demo_request_details','demo_events',
          'demo_ngo_applications','review_environment')

if ($AivenHostName -cnotmatch '^medbridge-mysql-aswin-582c\.[a-z0-9-]+\.aivencloud\.com$') {
  throw 'Unknown Aiven host: not the approved MedBridge service.'
}
if ($AivenAdminUser -notmatch '^[a-zA-Z0-9_]+$') { throw 'Invalid admin user name.' }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker not installed or not on PATH.' }
foreach ($f in $files) {
  if (-not (Test-Path -LiteralPath (Join-Path $resources $f) -PathType Leaf)) {
    throw "Missing approved SQL file: $f"
  }
}
$ca=(Resolve-Path -LiteralPath $CaCertificatePath -ErrorAction Stop).Path
if ((Get-Item -LiteralPath $ca).Length -eq 0) { throw 'Empty CA certificate.' }
Write-Host "Database: $database; scripts: $($files -join ', ')"
Write-Host 'TLS mode: VERIFY_IDENTITY. defaultdb will not be used or modified.'
if (-not $Apply) {
  Write-Host 'Dry run only: add -Apply after checking private Aiven host, port and CA file.'
  exit 0
}

$secret=Read-Host 'Enter Aiven admin password (not echoed)' -AsSecureString
$handle=[IntPtr]::Zero
$old=[Environment]::GetEnvironmentVariable('MYSQL_PWD','Process')
try {
  $handle=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
  $password=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($handle)
  [Environment]::SetEnvironmentVariable('MYSQL_PWD',$password,'Process')
  $password=$null
  # Transient Docker container inherits MYSQL_PWD without exposing its value in CLI arguments.
  # Docker administrators can inspect container environment while it is running.
  $argsMysql=@('run','--rm','-i','--env','MYSQL_PWD',
    '--mount',"type=bind,source=$ca,target=/ca.pem,readonly",
    'mysql:8.4','mysql',
    "--host=$AivenHostName","--port=$AivenPort","--user=$AivenAdminUser",
    '--ssl-mode=VERIFY_IDENTITY','--ssl-ca=/ca.pem',
    "--database=$database",'--batch','--skip-column-names')
  function SqlQuery([string]$statement) {
    $response=& docker @argsMysql "--execute=$statement"
    if ($LASTEXITCODE -ne 0) { throw 'The secure Aiven query failed. No automatic retry.' }
    return ($response | Out-String).Trim()
  }
  if ((SqlQuery 'SELECT DATABASE()') -cne $database) {
    throw 'Connected to the wrong database.'
  }
  $before=SqlQuery "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='defaultdb'"
  if ((SqlQuery "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$database'") -ne '0') {
    throw 'Target review database is not empty; do not run DDL or retry after partial failure.'
  }
  Write-Host 'Database identity, empty schema and TLS checks passed.'
  foreach ($file in $files) {
    Write-Host "Applying $file ..."
    Get-Content -LiteralPath (Join-Path $resources $file) -Raw -Encoding UTF8 | & docker @argsMysql
    if ($LASTEXITCODE -ne 0) {
      throw "Applying $file failed. Inspect partial review schema privately before any retry."
    }
  }
  $names=SqlQuery "SELECT table_name FROM information_schema.tables WHERE table_schema='$database' ORDER BY table_name"
  $actual=@($names -split "\r?\n" | Where-Object { $_ -ne '' } | Sort-Object)
  $expected=@($tables | Sort-Object)
  if (@(Compare-Object -ReferenceObject $expected -DifferenceObject $actual).Count -ne 0) {
    throw 'Review table-name inventory differs from the 11 expected tables.'
  }
  $marker=SqlQuery "SELECT COUNT(*) FROM review_environment WHERE environment_id='medbridge-review-20261026' AND purpose='FICTIONAL_COLLEGE_REVIEW_ONLY' AND schema_version=1 AND expires_at='2026-10-26T18:30:00Z'"
  if ($marker -ne '1') { throw 'Review security marker mismatch.' }
  if ((SqlQuery 'SELECT COUNT(*) FROM users') -ne '0') { throw 'Unexpected account data in review schema.' }
  if ((SqlQuery "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='defaultdb'") -ne $before) {
    throw 'Existing defaultdb table inventory unexpectedly changed.'
  }
  Write-Host 'SUCCESS: 11 review tables, security marker, zero accounts, defaultdb unchanged by this script.'
  Write-Host 'NEXT: create a separately restricted DML-only runtime user; verify grants before Render.'
}
finally {
  [Environment]::SetEnvironmentVariable('MYSQL_PWD',$old,'Process')
  if ($handle -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($handle) }
  $secret=$null
}
