@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run_server.ps1" %*
exit /b %ERRORLEVEL%