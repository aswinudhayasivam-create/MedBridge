[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('PrepareDatabase','Backend','Frontend')][string]$Action)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$backend=Join-Path $repo 'MedBridge-Phase-1\medbridge\backend'
$frontend=Join-Path $repo 'MedBridge-Phase-1\medbridge\frontend'
$privateDir=Join-Path $repo '.review'
$privateFile=Join-Path $privateDir 'local.env'
$container='medbridge-review-local-20261026-v2'
$prior=@{}
function Set-ReviewEnvironment([string]$Name,[string]$Value){
    if(-not $prior.ContainsKey($Name)){$prior[$Name]=[Environment]::GetEnvironmentVariable($Name,'Process')}
    [Environment]::SetEnvironmentVariable($Name,$Value,'Process')
}
function New-ReviewSecret { $bytes=New-Object byte[] 48;[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes);return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_') }
try {
    Set-ReviewEnvironment 'PATH' ('F:\Docker\Docker\resources\bin;'+$env:PATH)
    if($Action -eq 'PrepareDatabase' -and -not (Test-Path -LiteralPath $privateFile)){
        New-Item -ItemType Directory -Force -Path $privateDir | Out-Null
        $settings=@{
            REVIEW_ENABLED='true';REVIEW_PURPOSE='FICTIONAL_COLLEGE_REVIEW_ONLY';REVIEW_TRANSPORT='loopback-test'
            REVIEW_BIND_ADDRESS='127.0.0.1';REVIEW_FRONTEND_ORIGIN='http://127.0.0.1:5174'
            REVIEW_DB_URL='jdbc:mysql://127.0.0.1:13307/medbridgereview20261026?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC'
            REVIEW_DB_USERNAME='mb_review_local';REVIEW_EXPIRES_AT='2026-10-26T18:30:00Z'
            REVIEW_DB_PASSWORD=(New-ReviewSecret);REVIEW_ACCESS_KEY=(New-ReviewSecret);REVIEW_JWT_SECRET=(New-ReviewSecret)
            REVIEW_DONOR_PASSWORD=(New-ReviewSecret);REVIEW_NGO_PASSWORD=(New-ReviewSecret)
            REVIEW_PENDING_PASSWORD=(New-ReviewSecret);REVIEW_ADMIN_PASSWORD=(New-ReviewSecret);MYSQL_ROOT_PASSWORD=(New-ReviewSecret)
        }
        $settings.GetEnumerator() | Sort-Object Name | ForEach-Object { $_.Name+'='+$_.Value } | Set-Content -LiteralPath $privateFile -Encoding utf8
        $acl=Get-Acl -LiteralPath $privateFile;$acl.SetAccessRuleProtection($true,$false)
        $identity=[Security.Principal.WindowsIdentity]::GetCurrent().Name
        $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($identity,'FullControl','Allow')))
        Set-Acl -LiteralPath $privateFile -AclObject $acl
    }
    if(-not (Test-Path -LiteralPath $privateFile)){throw 'Run PrepareDatabase first. No hosted credentials are used.'}
    if($Action -eq 'Frontend'){
        Set-ReviewEnvironment 'VITE_ENABLE_REVIEW' 'true';Set-ReviewEnvironment 'VITE_REVIEW_API_ORIGIN' 'http://127.0.0.1:18080'
        Set-ReviewEnvironment 'VITE_ENABLE_AUTH_SUBMISSIONS' 'false';Set-ReviewEnvironment 'VITE_ENABLE_DEMO_WORKFLOWS' 'false'
        Push-Location $frontend
        try { & npm run dev -- --mode review --host 127.0.0.1 --port 5174;if($LASTEXITCODE -ne 0){throw 'Review frontend failed.'} } finally {Pop-Location}
    } else {
        foreach($line in Get-Content -LiteralPath $privateFile){if($line -match '^([A-Z_]+)=(.*)$'){Set-ReviewEnvironment $Matches[1] $Matches[2]}}
        if($Action -eq 'PrepareDatabase'){
            & docker info --format '{{.OSType}}' 2>$null | Out-Null;if($LASTEXITCODE -ne 0){throw 'Start the existing Docker Linux engine first.'}
            $existing=& docker ps -aq --filter "name=^/$container$"
            if(-not $existing){
                & docker create --name $container --label 'com.medbridge.review-test=20261026' --publish '127.0.0.1:13307:3306' --tmpfs '/var/lib/mysql:rw,nosuid,size=1g' --env MYSQL_ROOT_PASSWORD --env 'MYSQL_DATABASE=medbridgereview20261026' mysql:8.4 | Out-Null
                if($LASTEXITCODE -ne 0){throw 'Could not create isolated review test container; no existing containers changed.'}
            }
            $ownership=& docker inspect --format '{{index .Config.Labels "com.medbridge.review-test"}}' $container
            if($ownership -ne '20261026'){throw 'Container ownership is uncertain; leaving it untouched.'}
            & docker start $container | Out-Null
            $healthy=$false
            for($attempt=0;$attempt -lt 60;$attempt++){
                & docker exec $container sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -e "SELECT 1"' 2>$null | Out-Null
                if($LASTEXITCODE -eq 0){$healthy=$true;break};Start-Sleep -Seconds 2
            }
            if(-not $healthy){throw 'Review test MySQL did not become ready.'}
            $sql='USE medbridgereview20261026;'+[Environment]::NewLine
            foreach($file in 'schema.sql','demo-schema.sql','review-schema.sql'){$sql+=Get-Content -LiteralPath (Join-Path $backend "src\main\resources\$file") -Raw;$sql+=[Environment]::NewLine}
            $sql+="CREATE USER IF NOT EXISTS 'mb_review_local'@'%' IDENTIFIED BY '$env:REVIEW_DB_PASSWORD'; GRANT SELECT,INSERT,UPDATE,DELETE ON medbridgereview20261026.* TO 'mb_review_local'@'%';"
            $sql | & docker exec -i $container sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot' | Out-Null
            if($LASTEXITCODE -ne 0){throw 'Isolated review schema preparation failed.'}
            Write-Output 'Local review test database prepared on 127.0.0.1:13307. Private credentials remain in ignored .review/local.env. Container data is temporary; backend restart preserves it.'
        } else {
            $version=& java -version 2>&1 | Out-String;if($version -notmatch 'version "17\.'){throw 'Select the existing Java 17 installation in this terminal.'}
            Set-ReviewEnvironment 'SPRING_PROFILES_ACTIVE' 'review';Set-ReviewEnvironment 'APP_ALLOW_REGISTRATION' 'false';Set-ReviewEnvironment 'APP_DEMO_WORKFLOWS' 'false';Set-ReviewEnvironment 'PORT' '18080'
            Push-Location $backend
            try {& mvn -B -ntp spring-boot:run;if($LASTEXITCODE -ne 0){throw 'Review backend failed closed; inspect local diagnostics without sharing secrets.'}} finally {Pop-Location}
        }
    }
} finally {foreach($entry in $prior.GetEnumerator()){[Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')}}
