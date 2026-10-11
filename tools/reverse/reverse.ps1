param(
    [ValidateSet('Setup','Decompile','Index','Query','CombatAudit')][string]$Action='Query',
    [string[]]$QueryArgs=@('stats'),
    [string]$Python='',
    [switch]$Force
)
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$toolchain=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'toolchain.json') -Raw | ConvertFrom-Json
$vf=$toolchain.vineflower
$toolJar=Join-Path $PSScriptRoot "vendor/vineflower-$($vf.version).jar"
$data=Get-Content -LiteralPath (Join-Path $taskRoot 'frozen-version.json') -Raw | ConvertFrom-Json
$output=Join-Path $taskRoot "analysis/client-$($data.version)"
$java=Join-Path $taskRoot 'jdk/bin/java.exe'

function Check-FrozenInputs {
    foreach($record in $data.files) {
        $file=Join-Path $taskRoot $record.path
        if((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $record.sha256) {
            throw "Client input changed: $($record.path). Analysis aborted."
        }
    }
}
function Install-Vineflower {
    if(!(Test-Path -LiteralPath $toolJar)) {
        New-Item -ItemType Directory -Force -Path (Split-Path $toolJar) | Out-Null
        $temporary="$toolJar.download"
        Invoke-WebRequest -Uri $vf.url -OutFile $temporary -UseBasicParsing
        if((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $vf.sha256) {
            Remove-Item -LiteralPath $temporary
            throw 'Vineflower SHA256 mismatch.'
        }
        Move-Item -LiteralPath $temporary -Destination $toolJar
    }
    if((Get-FileHash -LiteralPath $toolJar -Algorithm SHA256).Hash -ne $vf.sha256) {
        throw 'Installed Vineflower SHA256 mismatch.'
    }
    Write-Host "Vineflower $($vf.version), verified SHA256."
}
function Find-Python {
    if($Python) {return $Python}
    $bundled=Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
    if(Test-Path -LiteralPath $bundled) {return $bundled}
    $command=Get-Command python -ErrorAction SilentlyContinue
    if($command) {return $command.Source}
    throw 'Set -Python to a Python 3.10+ executable (standard library only).'
}
New-Item -ItemType Directory -Force -Path $output | Out-Null
Push-Location $taskRoot
try {
    switch($Action) {
        Setup { Install-Vineflower }
        Decompile {
            Check-FrozenInputs
            Install-Vineflower
            $interpreter=Find-Python
            $arguments=@((Join-Path $PSScriptRoot 'decompile_client.py'))
            if($Force) {$arguments+='--force'}
            & $interpreter @arguments
            if($LASTEXITCODE -ne 0) {throw "Decompiler failed: $LASTEXITCODE"}
        }
        Index {
            Check-FrozenInputs
            $interpreter=Find-Python
            & $interpreter (Join-Path $PSScriptRoot 'index_client.py') build --root $taskRoot
            if($LASTEXITCODE -ne 0) {throw "Indexer failed: $LASTEXITCODE"}
        }
        Query {
            $interpreter=Find-Python
            & $interpreter (Join-Path $PSScriptRoot 'index_client.py') @QueryArgs --root $taskRoot
            if($LASTEXITCODE -ne 0) {throw "Query failed: $LASTEXITCODE"}
        }
        CombatAudit {
            Check-FrozenInputs
            $classes=Join-Path $output 'audit-classes'
            New-Item -ItemType Directory -Force -Path $classes | Out-Null
            $classpath='code/projectx-pcode.jar;code/commons-logging.jar;code/commons-beanutils.jar;code/commons-digester.jar'
            & (Join-Path $taskRoot 'jdk/bin/javac.exe') --release 17 -cp $classpath -d $classes server/InstalledConfigManager.java server/ItemCatalog.java server/SceneManager.java (Join-Path $PSScriptRoot 'InspectCombatCatalog.java') -sourcepath .
            if($LASTEXITCODE -ne 0) {throw "Combat auditor compilation failed: $LASTEXITCODE"}
            & $java '-Xmx768m' -cp "$classes;$classpath" server.InspectCombatCatalog (Join-Path $output 'combat-catalog.json') 2> (Join-Path $output 'combat-import.log')
            if($LASTEXITCODE -ne 0) {throw "Combat auditor failed: $LASTEXITCODE"}
        }
    }
} finally {Pop-Location}
