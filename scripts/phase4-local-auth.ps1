[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Prepare', 'Database', 'Backend', 'Frontend', 'UnitTests', 'IntegrationTests', 'StopDatabase')]
    [string]$Action,
    [switch]$EnableRegistration,
    [switch]$EnableSubmissions
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$privateDir = Join-Path $repo '.local-auth'
$privateFile = Join-Path $privateDir 'development.env'
$backend = Join-Path $repo 'MedBridge-Phase-1\medbridge\backend'
$frontend = Join-Path $repo 'MedBridge-Phase-1\medbridge\frontend'
$jdbc = 'jdbc:mysql://127.0.0.1:13306/medbridge_phase4_dev?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$dataDirectory = 'F:\Docker\MedBridge-MySQL'

function New-RandomHex([int]$Length) {
    $bytes = New-Object byte[] $Length
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}
function Require-Java17 {
    $java = Get-Command java -ErrorAction Stop
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $java.Source
    $start.Arguments = '-version'
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($start)
    $version = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0 -or $version -notmatch 'version "17\.') {
        throw 'Select a JDK 17 JAVA_HOME and put its bin directory first on PATH.'
    }
    $process.Dispose()
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Maven is required.' }
    $mavenVersion = (& mvn -version | Out-String)
    if ($LASTEXITCODE -ne 0 -or $mavenVersion -notmatch 'Java version:\s*17\.') { throw 'Maven must also report Java 17; check JAVA_HOME.' }
}
function Require-LocalDocker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Start Docker Desktop and add its CLI to PATH.' }
    if ($env:DOCKER_HOST -or $env:DOCKER_TLS_VERIFY) { throw 'DOCKER_HOST and DOCKER_TLS_VERIFY must be unset; only local Docker Desktop is allowed.' }
    $endpoint = & docker context inspect --format '{{.Endpoints.docker.Host}}'
    if ($LASTEXITCODE -ne 0 -or "$endpoint" -notin @('npipe:////./pipe/dockerDesktopLinuxEngine', 'npipe:////./pipe/docker_engine')) { throw 'Select a local Windows named-pipe Docker Desktop context.' }
    $osType = & docker info --format '{{.OSType}}'
    if ($LASTEXITCODE -ne 0 -or "$osType".Trim() -ne 'linux') { throw 'Docker Desktop Linux containers are not ready.' }
}
function Invoke-Compose([string[]]$Arguments) {
    & docker compose --env-file $privateFile -f (Join-Path $repo 'compose.local-auth.yml') @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'The isolated Docker Compose command failed.' }
}
function Require-DataDirectory {
    $marker = Join-Path $privateDir 'data-directory.txt'
    foreach ($path in @('F:\Docker', $dataDirectory)) {
        if ((Test-Path -LiteralPath $path) -and ((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'The MySQL data directory must not use a junction or symbolic link.'
        }
    }
    if (Test-Path -LiteralPath $marker) {
        if (([IO.File]::ReadAllText($marker)).Trim() -ne $dataDirectory) { throw 'Unexpected MySQL data-directory marker.' }
    } elseif ((Test-Path -LiteralPath $dataDirectory) -and @(Get-ChildItem -LiteralPath $dataDirectory -Force).Count) {
        throw 'The MySQL data directory contains unrecognized existing files. Review them before starting; nothing was deleted.'
    }
    New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
    if (-not (Test-Path -LiteralPath $marker)) { [IO.File]::WriteAllText($marker, $dataDirectory) }
}

if ($Action -eq 'Prepare') {
    if (Test-Path -LiteralPath $privateFile) { Write-Host 'Existing local configuration retained.'; return }
    New-Item -ItemType Directory -Path $privateDir -Force | Out-Null
    # Protect the directory before writing any secret; grant only the current user and SYSTEM.
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    $acl.SetOwner($identity)
    foreach ($sid in @($identity, (New-Object Security.Principal.SecurityIdentifier('S-1-5-18')))) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $privateDir -AclObject $acl
    $lines = @(
        "PHASE4_DB_USERNAME=phase4_$(New-RandomHex 6)"
        "PHASE4_DB_PASSWORD=$(New-RandomHex 32)"
        "PHASE4_DB_ROOT_PASSWORD=$(New-RandomHex 32)"
        "PHASE4_JWT_SECRET=$(New-RandomHex 48)"
    )
    [IO.File]::WriteAllLines($privateFile, $lines, (New-Object Text.UTF8Encoding($false)))
    Write-Host 'Generated private local configuration. No secret values were printed.'
    return
}

$saved = @{}
function Set-ChildEnvironment([string]$Name, [string]$Value) {
    if (-not $saved.ContainsKey($Name)) { $saved[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process') }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}
try {
    if ($Action -ne 'UnitTests') {
        if (-not (Test-Path -LiteralPath $privateFile)) { throw 'Run -Action Prepare first.' }
        foreach ($line in [IO.File]::ReadAllLines($privateFile)) {
            if ($line -notmatch '^(PHASE4_DB_USERNAME|PHASE4_DB_PASSWORD|PHASE4_DB_ROOT_PASSWORD|PHASE4_JWT_SECRET)=([a-z0-9_]+)$') {
                throw 'Local configuration has an unexpected entry. Do not source arbitrary environment files.'
            }
            Set-ChildEnvironment $Matches[1] $Matches[2]
        }
        foreach ($name in @('PHASE4_DB_USERNAME','PHASE4_DB_PASSWORD','PHASE4_DB_ROOT_PASSWORD','PHASE4_JWT_SECRET')) {
            if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) { throw 'Local configuration is incomplete.' }
        }
    }
    switch ($Action) {
        'Database' { Require-LocalDocker; Require-DataDirectory; Invoke-Compose @('up', '-d', '--wait', 'database') }
        'StopDatabase' { Require-LocalDocker; Invoke-Compose @('stop', 'database') }
        'Frontend' {
            Set-ChildEnvironment 'VITE_ENABLE_AUTH_SUBMISSIONS' $(if ($EnableSubmissions) { 'true' } else { 'false' })
            Set-ChildEnvironment 'VITE_API_BASE_URL' ''
            Push-Location $frontend
            try { & npm run dev:local-auth; if ($LASTEXITCODE -ne 0) { throw 'Vite failed.' } } finally { Pop-Location }
        }
        { $_ -in 'Backend','UnitTests','IntegrationTests' } {
            Require-Java17
            if ($Action -eq 'UnitTests') {
                Set-ChildEnvironment 'SPRING_PROFILES_ACTIVE' ''
                Set-ChildEnvironment 'APP_ALLOW_REGISTRATION' 'false'
                $mavenArgs = @('-B', '-Dtest=AuthServiceTest,JwtServiceTest,JwtAuthenticationFilterTest,LocalAuthSafetyConfigurationTest,RegistrationPolicyTest,AuthWebSecurityTest,JwtExpiryValidationTest', 'test')
            } else {
                Require-LocalDocker
                Set-ChildEnvironment 'SPRING_PROFILES_ACTIVE' 'local-auth'
                Set-ChildEnvironment 'SPRING_DATASOURCE_URL' $jdbc
                Set-ChildEnvironment 'APP_ALLOW_REGISTRATION' $(if ($EnableRegistration -or $Action -eq 'IntegrationTests') { 'true' } else { 'false' })
                if ($Action -eq 'IntegrationTests') {
                    # Explicit selection includes *IT, which is deliberately excluded from ordinary unit tests.
                    $mavenArgs = @('-B', '-Dtest=LocalAuthMySqlIT', 'test')
                } else { $mavenArgs = @('spring-boot:run') }
            }
            Push-Location $backend
            try {
                & mvn @mavenArgs
                if ($LASTEXITCODE -ne 0) { throw 'The Maven command failed.' }
                if ($Action -eq 'IntegrationTests') {
                    Set-ChildEnvironment 'SPRING_PROFILES_ACTIVE' ''
                    Set-ChildEnvironment 'APP_ALLOW_REGISTRATION' 'false'
                    & mvn -B '-Dtest=DatabaseSchemaTest' test
                    if ($LASTEXITCODE -ne 0) { throw 'The schema container test failed.' }
                }
            } finally { Pop-Location }
        }
    }
} finally {
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
}
