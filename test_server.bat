@echo off
setlocal
cd /d "%~dp0"
set "CP=code/projectx-pcode.jar;code/commons-logging.jar;code/commons-beanutils.jar;code/commons-digester.jar;."
"jdk\bin\javac.exe" --release 17 -cp "%CP%" server\*.java portable\*.java
if errorlevel 1 exit /b 1
"jdk\bin\java.exe" -cp "%CP%" server.TestCombatSimulation
if errorlevel 1 exit /b 1
"jdk\bin\java.exe" -cp "%CP%" server.TestEmulatorProtocol
exit /b %ERRORLEVEL%
