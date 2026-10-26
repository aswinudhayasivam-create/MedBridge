[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('Database','Backend','Frontend','Test','StopDatabase')][string]$Action)
$ErrorActionPreference='Stop'
$launcher=Join-Path $PSScriptRoot 'phase4-local-auth.ps1'
$dockerBin='F:\Docker\Docker\resources\bin'
$oldPath=$env:PATH
try {
    if(Test-Path -LiteralPath $dockerBin){$env:PATH=$dockerBin+';'+$env:PATH}
    switch($Action){
        'Database' { & $launcher -Action Database }
        'Backend' { & $launcher -Action Backend -EnableDemo }
        'Frontend' { & $launcher -Action Frontend -EnableSubmissions -EnableDemo }
        'Test' { & $launcher -Action IntegrationTests -EnableDemo }
        'StopDatabase' { & $launcher -Action StopDatabase }
    }
} finally {$env:PATH=$oldPath}
