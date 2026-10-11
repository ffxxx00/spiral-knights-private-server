[CmdletBinding()]
param([Parameter(Position=0)][ValidateRange(1,65535)][int]$Port=47624)
$ErrorActionPreference='Stop'
$privateRoot=Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $privateRoot
try {
    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    if (@($listeners | Where-Object { $_.Port -eq $Port }).Count -gt 0) {
        throw "El puerto $Port esta ocupado. Cierra el otro servidor o utiliza start_server.bat 48000."
    }
    $cp='code/projectx-pcode.jar;code/commons-logging.jar;code/commons-beanutils.jar;code/commons-digester.jar;.'
    & '.\jdk\bin\javac.exe' --release 17 -cp $cp server/*.java
    if($LASTEXITCODE -ne 0){throw 'Fallo la compilacion del servidor.'}
    & '.\jdk\bin\java.exe' "-Duser.home=$privateRoot\profile" -cp $cp server.SpiralServer $Port
    exit $LASTEXITCODE
} finally { Pop-Location }
