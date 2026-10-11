@echo off
title Spiral Knights - Cliente Servidor Privado
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0launch_client.ps1" %*
set EXIT_CODE=%ERRORLEVEL%
if not "%EXIT_CODE%"=="0" (
    echo [INFO] El cliente cerro con codigo: %EXIT_CODE%
    pause
)
exit /b %EXIT_CODE%
