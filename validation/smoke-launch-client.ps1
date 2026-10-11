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

    [switch]$DebugProtocol
)

$ErrorActionPreference = 'Stop'

$gameRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$javaPath = Join-Path $gameRoot 'java_vm\bin\java.exe'
$sourceConfig = Join-Path $gameRoot 'server\local-config\deployment.properties'
$runtimeConfig = Join-Path $gameRoot 'server\runtime-client-config'
$runtimeDeployment = Join-Path $runtimeConfig 'deployment.properties'

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

$javaArgs = @(
    "-Duser.home=$gameRoot\profile",
    '-Djava.util.prefs.PreferencesFactory=portable.LocalPreferencesFactory',
    "-Dprivate.preferences.dir=$gameRoot\profile\preferences",
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
    '-cp',
    $classPath,
    'com.threerings.projectx.client.ProjectXApp',
    $KnightName,
    'password',
    'HeroKnight'
)

if ($DebugProtocol) {
    $javaArgs = @(
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
