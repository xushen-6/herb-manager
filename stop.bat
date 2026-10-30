@echo off
setlocal
cd /d "%~dp0"

echo ========================================
echo   Stop Herb Manager
echo ========================================
echo.

set "FOUND=0"
for /f "usebackq delims=" %%P in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$items=Get-CimInstance Win32_Process; foreach($item in $items){if($item.CommandLine -like '*target\herb-manager.jar*'){Write-Output $item.ProcessId}}"`) do (
    set "FOUND=1"
    echo Stopping project process %%P...
    taskkill /PID %%P /T /F >nul 2>nul
)

if "%FOUND%"=="0" echo No running Herb Manager process was found.
echo.

choice /C YN /N /M "Also stop the MySQL80 service? [Y/N] "
if errorlevel 2 goto DONE

net stop MySQL80
if errorlevel 1 echo MySQL80 was not stopped. Try running this script as Administrator.

:DONE
echo.
echo Project shutdown completed.
pause
endlocal
