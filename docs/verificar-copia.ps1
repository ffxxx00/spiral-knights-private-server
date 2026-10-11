[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$copyRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$required = @('jdk/bin/java.exe','jdk/bin/javac.exe','java_vm/bin/java.exe',
    'code/lwjgl.jar','code/lwjgl-natives-windows.jar','native','rsrc','scenes',
    'server/server.properties','server/SpiralServer.java','server/ClientSession.java',
    'server/SceneNetworkManager.java','server/TestEmulatorProtocol.java',
    'server/local-config/deployment.properties','portable/LocalPreferencesFactory.java',
    'portable/BuildLocalClient.java','client-local/src/com/threerings/tudey/util/q.java',
    'start_server.bat','run_server.bat','run_server.ps1','launch_client.bat',
    'launch_client.ps1','test_server.bat','allow_lan_firewall.ps1',
    'frozen-version.json','getdown.txt','version.txt','full.dat')
foreach ($item in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $copyRoot $item))) {
        throw "Falta un archivo o carpeta de la entrega: $item"
    }
}
$copyManifest = Get-Content -LiteralPath (Join-Path $copyRoot 'frozen-version.json') -Raw | ConvertFrom-Json
if ($copyManifest.version -ne '20261006114613') { throw 'Esta guia requiere la version 20261006114613.' }
$copyVersion = [IO.File]::ReadAllText((Join-Path $copyRoot 'getdown.txt'))
if ($copyVersion -notmatch '(?m)^version\s*=\s*20261006114613\s*$') { throw 'getdown.txt no coincide con la version.' }
foreach ($item in $copyManifest.files) {
    $itemPath = Join-Path $copyRoot $item.path
    if (-not (Test-Path -LiteralPath $itemPath -PathType Leaf)) { throw "Falta: $($item.path)" }
    $itemStream = [IO.File]::OpenRead($itemPath)
    $itemHasher = [Security.Cryptography.SHA256]::Create()
    try { $itemHash = [BitConverter]::ToString($itemHasher.ComputeHash($itemStream)).Replace('-', '') }
    finally { $itemStream.Dispose(); $itemHasher.Dispose() }
    if ($itemHash -ne $item.sha256) { throw "Hash diferente: $($item.path)" }
    Write-Host "SHA256 OK: $($item.path)"
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$copyJar = [IO.Compression.ZipFile]::OpenRead((Join-Path $copyRoot 'code/lwjgl.jar'))
try {
    if ($null -eq $copyJar.GetEntry('META-INF/versions/25/org/lwjgl/system/MemoryBackendFFM.class')) {
        throw 'La biblioteca LWJGL no contiene el backend FFM esperado.'
    }
} finally { $copyJar.Dispose() }
$copyLauncher = [IO.File]::ReadAllText((Join-Path $copyRoot 'launch_client.ps1'))
if ($copyLauncher -notmatch 'org\.lwjgl\.system\.memoryBackend=ffm' -or
    $copyLauncher -match 'sun-misc-unsafe-memory-access=allow') {
    throw 'El lanzador no contiene la correccion de memoria FFM de esta entrega.'
}
Write-Host "COPIA VERIFICADA: $($copyManifest.version)" -ForegroundColor Green
Write-Host 'Ahora ejecuta test_server.bat para comprobar el protocolo. No se cambio ningun archivo ni regla de firewall.'
