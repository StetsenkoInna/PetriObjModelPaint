@echo off
where java >nul 2>&1
if errorlevel 1 goto :needjava

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JVER=%%v
set JVER=%JVER:"=%
for /f "delims=. tokens=1" %%a in ("%JVER%") do set MAJOR=%%a
if "%MAJOR%"=="1" for /f "delims=. tokens=2" %%a in ("%JVER%") do set MAJOR=%%a
if %MAJOR% LSS 23 goto :needjava

start "" javaw -jar "%~dp0petri-swing-ui-windows.jar"
exit /b

:needjava
echo This app needs Java 23 or newer.
echo Download it here: https://www.oracle.com/java/technologies/downloads/
start https://www.oracle.com/java/technologies/downloads/
pause
