# Prepared locally; applying this procedure requires separate cloud/database approval.
[CmdletBinding()]
param(
 [Parameter(Mandatory=$true)][string]$SettingsPath,
 [Parameter(Mandatory=$true)][string]$CaCertificatePath,
 [switch]$Apply
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$database='medbridgereview20261026'
$repo=Split-Path -Parent $PSScriptRoot
$resources=Join-Path $repo 'MedBridge-Phase-1\medbridge\backend\src\main\resources'
$pin=@{}
foreach($line in Get-Content -LiteralPath (Join-Path $resources 'tidb-review-destination.properties')) {
 if($line -match '^([a-z-]+)=(.*)$') { $pin[$Matches[1]]=$Matches[2] }
}
$settings=Get-Content -LiteralPath $SettingsPath -Raw | ConvertFrom-Json
if($pin.host -notmatch '^[a-z0-9-]+(?:\.[a-z0-9-]+)*\.tidbcloud\.com$' -or $pin.port -ne '4000' -or $pin['username-prefix'] -notmatch '^[A-Za-z0-9]{1,32}$') { throw 'No approved TiDB destination is pinned in this build. Stop.' }
if($settings.host -cne $pin.host -or $settings.port -ne 4000 -or $settings.database -cne $database -or $settings.usernamePrefix -cne $pin['username-prefix']) { throw 'Private settings differ from the approved instance.' }
if($settings.compatibility -cne 'MySQL' -or $settings.plan -cne 'Starter' -or $settings.monthlySpendingLimit -ne 0 -or $settings.paymentMethodAdded -ne $false) { throw 'Eligible no-card $0 Starter configuration has not been confirmed.' }
$userPrefix=[regex]::Escape($pin['username-prefix'])
if($settings.ownerUser -cnotmatch "^$userPrefix\.[A-Za-z0-9_]+$" -or $settings.runtimeUser -cnotmatch "^$userPrefix\.mb_review_[a-z0-9_]{1,32}$" -or $settings.ownerUser -ceq $settings.runtimeUser) { throw 'Separate schema-owner and restricted runtime identities are required.' }
$ca=(Resolve-Path -LiteralPath $CaCertificatePath).Path
if((Get-Item -LiteralPath $ca).Length -eq 0) { throw 'Empty public root CA bundle.' }
$files=@('schema.sql','demo-schema.sql','review-schema.sql')
foreach($file in $files) { if(-not(Test-Path -LiteralPath (Join-Path $resources $file))) { throw 'Missing approved SQL source.' } }
Write-Host 'TiDB review destination pin and local settings validated. TLS: VERIFY_IDENTITY.'
if(-not $Apply) { Write-Host 'DRY RUN: no network, credentials, SQL, users or deployments executed.'; return }
if([DateTime]::UtcNow -ge [DateTime]::Parse('2026-10-26T18:30:00Z').ToUniversalTime()) { throw 'Review deadline reached. Stop.' }
& docker info --format '{{.OSType}}' 2>$null | Out-Null
if($LASTEXITCODE -ne 0) { throw 'Docker Linux engine unavailable.' }
# Lock stays outside Git and survives failures. Never remove it automatically.
$stateDir=Join-Path $env:LOCALAPPDATA 'MedBridge\tidb-review-initialization'
New-Item -ItemType Directory -Force -Path $stateDir | Out-Null
$lockPath=Join-Path $stateDir ($pin['username-prefix']+'-20261026.attempt')
$lock=[IO.File]::Open($lockPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
$lock.Dispose()
$old=[Environment]::GetEnvironmentVariable('MYSQL_PWD','Process')
$handle=[IntPtr]::Zero
function Select-PrivatePassword([string]$label) {
 if($script:handle -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($script:handle); $script:handle=[IntPtr]::Zero }
 $secret=Read-Host $label -AsSecureString
 try {
  $script:handle=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
  [Environment]::SetEnvironmentVariable('MYSQL_PWD',[Runtime.InteropServices.Marshal]::PtrToStringBSTR($script:handle),'Process')
 } finally { $secret.Dispose() }
}
function Invoke-SecureQuery([string]$sql,[string]$user,[int]$ExpectedError=0) {
 $argsMysql=@('run','--rm','-i','--env','MYSQL_PWD','--mount',"type=bind,source=$ca,target=/ca.pem,readonly",'mysql:8.4','mysql',
  "--host=$($pin.host)",'--port=4000',"--user=$user",'--ssl-mode=VERIFY_IDENTITY','--ssl-ca=/ca.pem',"--database=$database",'--batch','--skip-column-names',
  "--init-command=SET SESSION foreign_key_checks=ON, tidb_txn_mode='pessimistic'", "--execute=$sql")
 $priorAction=$ErrorActionPreference
 try { $ErrorActionPreference='Continue';$output=@(& docker @argsMysql 2>&1);$code=$LASTEXITCODE }
 finally { $ErrorActionPreference=$priorAction }
 if($ExpectedError -ne 0) {
  if($code -eq 0 -or ($output | Out-String) -notmatch "ERROR $ExpectedError ") { throw 'Required constraint rejection was not demonstrated. Stop; no automatic retry.' }
  return
 }
 if($code -ne 0) { throw 'Secure TiDB query failed. Inspect privately; no automatic retry.' }
 return ($output | Out-String).Trim()
}
try {
 Select-PrivatePassword 'Enter restricted runtime password privately (not saved)'
 $grants=Invoke-SecureQuery 'SHOW GRANTS' $settings.runtimeUser
 $privileges=@{}
 $recipient='(?:''[A-Za-z0-9_.]+''|`[A-Za-z0-9_.]+`)@(?:''[^'']+''|`[^`]+`)'
 $usagePattern='(?i)^GRANT USAGE ON \*\.\* TO '+$recipient+'$'
 $dmlPattern='(?i)^GRANT ([A-Z, ]+) ON `?'+$database+'`?\.\* TO '+$recipient+'$'
 foreach($grant in ($grants -split '\r?\n')) {
  if($grant -match $usagePattern) { continue }
  if($grant -notmatch $dmlPattern) { throw 'Runtime grants are broader than database-scoped DML or unrecognized.' }
  foreach($privilege in ($Matches[1] -split '\s*,\s*')) { if($privilege.ToUpperInvariant() -notin @('SELECT','INSERT','UPDATE','DELETE')) { throw 'Runtime has non-DML privileges.' }; $privileges[$privilege.ToUpperInvariant()]=$true }
 }
 if($privileges.Count -ne 4) { throw 'Runtime requires all four DML privileges only.' }
 Select-PrivatePassword 'Enter separate schema-owner password privately (not saved)'
 if((Invoke-SecureQuery 'SELECT DATABASE()' $settings.ownerUser) -cne $database) { throw 'Wrong target database.' }
 if((Invoke-SecureQuery 'SELECT VERSION()' $settings.ownerUser) -notmatch '(?i)tidb') { throw 'Target is not TiDB.' }
 if((Invoke-SecureQuery "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()" $settings.ownerUser) -ne '0') { throw 'Destination is not empty. Do not run DDL.' }
 if((Invoke-SecureQuery 'SELECT @@GLOBAL.tidb_enable_foreign_key' $settings.ownerUser) -ne '1') { throw 'Global foreign key capability is disabled; no global change authorized.' }
 if((Invoke-SecureQuery "SELECT CONCAT(@@foreign_key_checks,':',@@GLOBAL.tidb_enable_check_constraint,':',@@tidb_txn_mode)" $settings.ownerUser) -ne '1:1:pessimistic') { throw 'Constraint or transaction settings mismatch.' }
 Write-Host 'Database identity, emptiness, runtime grants and VERIFY_IDENTITY passed.'
 foreach($file in $files) {
  Write-Host "Applying $file ..."
  $sql=Get-Content -LiteralPath (Join-Path $resources $file) -Raw -Encoding UTF8
  Invoke-SecureQuery $sql $settings.ownerUser | Out-Null
 }
 $expected=@('users','ngo_profiles','medicines','requests','notifications','demo_medicine_details','demo_photos','demo_request_details','demo_events','demo_ngo_applications','review_environment') | Sort-Object
 $actual=(Invoke-SecureQuery 'SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name' $settings.ownerUser) -split '\r?\n' | Sort-Object
 if(@(Compare-Object $expected $actual).Count -ne 0) { throw 'Expected 11-table inventory mismatch.' }
 if((Invoke-SecureQuery "SELECT COUNT(*) FROM information_schema.tidb_check_constraints WHERE constraint_schema=DATABASE()" $settings.ownerUser) -ne '4') { throw 'All four CHECK constraints must be enforced.' }
 foreach($table in @('users','medicines')) {
  $expectedNames=if($table -eq 'users'){@('chk_latitude','chk_longitude')}else{@('chk_medicine_quantity','chk_medicine_value')}
  $actualNames=(Invoke-SecureQuery "SELECT constraint_name FROM information_schema.tidb_check_constraints WHERE constraint_schema=DATABASE() AND table_name='$table' ORDER BY constraint_name" $settings.ownerUser) -split '\r?\n'
  if(@(Compare-Object $expectedNames $actualNames).Count -ne 0) { throw 'Named CHECK constraints mismatch.' }
  $definition=Invoke-SecureQuery "SHOW CREATE TABLE $table" $settings.ownerUser
  if($definition -notmatch '(?i)CHECK' -or $definition -match '(?i)\bNOT\s+ENFORCED\b') { throw 'CHECK constraints are not demonstrably enforced.' }
 }
 if((Invoke-SecureQuery "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()" $settings.ownerUser) -ne '11') { throw 'All eleven foreign keys must exist.' }
 # Only transient fictional records in transactions; failed statements disconnect/rollback.
 $fixture="INSERT INTO users(name,email,password_hash,role,latitude,longitude) VALUES('Constraint probe','probe@review.medbridge.invalid','not-an-authentication-password','DONOR',"
 foreach($coords in @('91,0','0,181')) { Invoke-SecureQuery ("BEGIN; "+$fixture+$coords+"); ROLLBACK;") $settings.ownerUser 3819 }
 $parent="BEGIN; "+$fixture+"0,0); SET @donor=LAST_INSERT_ID(); "
 foreach($values in @('0,0','1,-1')) {
  Invoke-SecureQuery ($parent+"INSERT INTO medicines(donor_id,name,batch_no,expiry_date,quantity,category,estimated_value) VALUES(@donor,'Constraint probe','FICTIONAL','2027-01-01',$($values.Split(',')[0]),'General',$($values.Split(',')[1])); ROLLBACK;") $settings.ownerUser 3819
 }
 Invoke-SecureQuery "BEGIN; INSERT INTO notifications(user_id,type,message) VALUES(-1,'PROBE','Fictional constraint probe'); ROLLBACK;" $settings.ownerUser 1452
 if((Invoke-SecureQuery "SELECT COUNT(*) FROM review_environment WHERE environment_id='medbridge-review-20261026' AND purpose='FICTIONAL_COLLEGE_REVIEW_ONLY' AND schema_version=1 AND expires_at='2026-10-26T18:30:00Z'" $settings.ownerUser) -ne '1') { throw 'Security marker mismatch.' }
 foreach($table in $expected | Where-Object {$_ -ne 'review_environment'}) { if((Invoke-SecureQuery "SELECT COUNT(*) FROM $table" $settings.ownerUser) -ne '0') { throw 'Unexpected seeded data.' } }
 Write-Host 'SUCCESS: 11 tables, 4 enforced CHECKs, 11 foreign keys, correct marker and zero seeded accounts. No other database was selected or written.'
 Write-Host 'Full TiDB application, concurrency, TLS-negative and restart tests remain mandatory before hosting.'
} finally {
 [Environment]::SetEnvironmentVariable('MYSQL_PWD',$old,'Process')
 if($handle -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($handle) }
}
