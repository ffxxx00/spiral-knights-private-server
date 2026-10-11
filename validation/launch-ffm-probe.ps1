[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidatePattern('^[A-Za-z0-9._:-]+$')]
    [string]$ServerHost = 'localhost',

    [Parameter(Position = 1)]
    [ValidateRange(1, 65535)]
    [int]$Port = 47624,

    [Parameter(Position = 2)]
    [ValidatePattern('^[A-Za-z0-9_-]{1,24}$')]
    [string]$KnightName = 'HeroKnight',

    [switch]$DebugProtocol,

    [switch]$NetworkMode
)

$ErrorActionPreference = 'Stop'

$gameRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$javaPath = Join-Path $gameRoot 'java_vm\bin\java.exe'
$sourceConfig = Join-Path $gameRoot 'server\local-config\deployment.properties'
$runtimeConfig = Join-Path $gameRoot 'validation\runtime-ffm-config'
$runtimeDeployment = Join-Path $runtimeConfig 'deployment.properties'
$localMode = -not $NetworkMode -and $ServerHost -in @('localhost', '127.0.0.1', '::1')
$localClasses = Join-Path $gameRoot 'client-local/classes'

if ($localMode) {
    # Only the loopback launcher uses this overlay. The client JAR stays intact.
    $lockedBuild = Get-Content -LiteralPath (Join-Path $gameRoot 'frozen-version.json') -Raw | ConvertFrom-Json
    $lockedJar = $lockedBuild.files | Where-Object { $_.path -eq 'code/projectx-pcode.jar' }
    $jarPath = Join-Path $gameRoot 'code/projectx-pcode.jar'
    $jarStream = [IO.File]::OpenRead($jarPath)
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { $jarHash = [BitConverter]::ToString($hasher.ComputeHash($jarStream)).Replace('-', '') }
    finally { $jarStream.Dispose(); $hasher.Dispose() }
    if ($jarHash -ne $lockedJar.sha256) {
        throw 'La biblioteca del cliente cambió. No se puede aplicar el modo local.'
    }
    $serverSettings = Get-Content -LiteralPath (Join-Path $gameRoot 'server/server.properties') -Raw
    $intervalMatch = [regex]::Match($serverSettings, '(?m)^movement\.deltaIntervalMs\s*=\s*(\d+)')
    if (-not $intervalMatch.Success) { throw 'Falta movement.deltaIntervalMs para el modo local.' }
    $localInterval = [int]$intervalMatch.Groups[1].Value
    & (Join-Path $gameRoot 'jdk/bin/javac.exe') --release 17 -d $localClasses `
        (Join-Path $gameRoot 'portable/BuildLocalClient.java') `
        (Join-Path $gameRoot 'client-local/src/com/threerings/tudey/util/q.java')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el modo local.' }
    & (Join-Path $gameRoot 'jdk/bin/java.exe') -cp $localClasses portable.BuildLocalClient `
        $jarPath $localClasses $localInterval
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar el intervalo del cliente local.' }
}

if (-not (Test-Path -LiteralPath $sourceConfig -PathType Leaf)) {
    throw "No se encontro la configuracion base: $sourceConfig"
}

[IO.Directory]::CreateDirectory($runtimeConfig) | Out-Null
$deployment = [IO.File]::ReadAllText($sourceConfig)
$deployment = [regex]::Replace(
    $deployment, '(?m)^server_host\s*=.*$', "server_host = $ServerHost")
$deployment = [regex]::Replace(
    $deployment, '(?m)^server_ports\s*=.*$', "server_ports = $Port")
$deployment = [regex]::Replace(
    $deployment, '(?m)^datagram_ports\s*=.*$', "datagram_ports = $Port")
[IO.File]::WriteAllText(
    $runtimeDeployment, $deployment, [Text.UTF8Encoding]::new($false))

$classPath = @(
    $runtimeConfig,
    'server/local-config',
    'code/config.jar',
    'code/projectx-config.jar',
    'code/projectx-pcode.jar',
    'code/lwjgl.jar',
    'code/lwjgl-opengl.jar',
    'code/lwjgl-openal.jar',
    'code/lwjgl-glfw.jar',
    'code/lwjgl-jawt.jar',
    'code/lwjgl3-awt.jar',
    'code/lwjgl-natives-windows.jar',
    'code/lwjgl-opengl-natives-windows.jar',
    'code/lwjgl-openal-natives-windows.jar',
    'code/lwjgl-glfw-natives-windows.jar',
    'code/commons-beanutils.jar',
    'code/commons-digester.jar',
    'code/commons-logging.jar',
    '.'
) -join ';'
if ($localMode) { $classPath = "$localClasses;$classPath" }

$javaArgs = @(
    '--sun-misc-unsafe-memory-access=deny',
    '-Dmeasurement.visitTraining=true',
    '-Dmeasurement.durationSeconds=65',
    '-Dmeasurement.trainingAfterSeconds=25',
    '-XX:+UseG1GC',
    '-XX:MaxGCPauseMillis=20',
    '-XX:+ExplicitGCInvokesConcurrent',
    "-Dspiral.local.lowLatency=$($localMode.ToString().ToLowerInvariant())",
    "-Duser.home=$gameRoot\validation\ffm-profile",
    '-Djava.util.prefs.PreferencesFactory=portable.LocalPreferencesFactory',
    "-Dprivate.preferences.dir=$gameRoot\validation\ffm-profile\preferences",
    '-Djava.library.path=native',
    '-Dorg.lwjgl.util.NoChecks=true',
    '-Dsun.java2d.d3d=false',
    "-Dappdir=$gameRoot\.",
    "-Dresource_dir=$gameRoot\rsrc",
    '-XX:-CreateCoredumpOnCrash',
    '-XX:+SuppressFatalErrorMessage',
    '--add-opens=java.base/java.lang=ALL-UNNAMED',
    '--add-opens=java.base/java.util=ALL-UNNAMED',
    '--enable-native-access=ALL-UNNAMED',
    # Use the FFM implementation already shipped in the client LWJGL JAR.
    # It replaces deprecated sun.misc.Unsafe memory calls; warnings stay enabled.
    '-Dorg.lwjgl.system.memoryBackend=ffm',
    '-cp',
    $classPath,
    'validation.MeasureClient',
    $KnightName,
    'password'
)

if ($DebugProtocol) {
    $javaArgs = @(
    '--sun-misc-unsafe-memory-access=deny',
    '-Dmeasurement.visitTraining=true',
    '-Dmeasurement.durationSeconds=65',
    '-Dmeasurement.trainingAfterSeconds=25',
        '-Dcom.threerings.presents.client.debug=true',
        '-Dcom.threerings.crowd.client.debug=true',
        '-Dcom.threerings.tudey.debug=true',
        '-Dcom.threerings.projectx.debug=true'
    ) + $javaArgs
}

Write-Host '==================================================================' -ForegroundColor Cyan
Write-Host '   Lanzando Spiral Knights - Servidor Privado' -ForegroundColor Cyan
Write-Host '==================================================================' -ForegroundColor Cyan
Write-Host "Destino TCP: $ServerHost`:$Port" -ForegroundColor Green
Write-Host "Caballero local: $KnightName" -ForegroundColor Green
Write-Host "Debug de protocolo: $($DebugProtocol.IsPresent)" -ForegroundColor DarkGray
Write-Host "Modo local de baja latencia: $localMode" -ForegroundColor Green

[IO.Directory]::CreateDirectory((Join-Path $gameRoot 'profile')) | Out-Null
# ProjectXApp.Ha() starts Getdown when this marker is absent. All resources
# are already installed in the client copy; create it before the first launch.
$fullDataMarker = Join-Path $gameRoot 'full.dat'
if (-not (Test-Path -LiteralPath $fullDataMarker)) {
    [IO.File]::WriteAllBytes($fullDataMarker, [byte[]]@())
}
if (-not (Test-Path -LiteralPath (Join-Path $gameRoot 'portable/LocalPreferencesFactory.class'))) {
    & (Join-Path $gameRoot 'jdk/bin/javac.exe') --release 17 -d $gameRoot (Join-Path $gameRoot 'portable/LocalPreferencesFactory.java')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el almacenamiento de preferencias local.' }
}
Push-Location $gameRoot
try {
    & $javaPath @javaArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
