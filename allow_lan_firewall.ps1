[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 47624
)

$ErrorActionPreference = 'Stop'
$ruleName = "SpiralKnightsPrivateServer-TCP-$Port"
$displayName = "Spiral Knights Private Server TCP $Port (LAN)"

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Ejecuta este script desde PowerShell como Administrador.'
}

$existing = Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
if ($null -eq $existing) {
    New-NetFirewallRule `
        -Name $ruleName `
        -DisplayName $displayName `
        -Direction Inbound `
        -Action Allow `
        -Enabled True `
        -Profile Private `
        -Protocol TCP `
        -LocalPort $Port `
        -RemoteAddress LocalSubnet | Out-Null
    Write-Host "Regla creada: $displayName" -ForegroundColor Green
} else {
    Enable-NetFirewallRule -Name $ruleName
    Write-Host "Regla ya existente y habilitada: $displayName" -ForegroundColor Green
}

Write-Host 'Acceso limitado al perfil de red Privado y a la subred local.' -ForegroundColor Cyan
