@echo off
setlocal
cd /d "%~dp0"
set "JAVA_CMD=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
"%JAVA_CMD%" -version >nul 2>nul
if errorlevel 1 goto NO_JAVA

set "APP_JAR=app\herb-manager.jar"
if exist "%APP_JAR%" goto RUN_APP
set "APP_JAR=target\herb-manager.pending.jar"
if exist "%APP_JAR%" goto RUN_APP
set "APP_JAR=target\herb-manager.jar"
if exist "%APP_JAR%" goto RUN_APP
echo ERROR: Application JAR is missing. Extract the complete source/demo ZIP.
goto FAILED

:RUN_APP
echo Herb Manager - requires Java 17 or newer and an internet connection.
"%JAVA_CMD%" -Dfile.encoding=UTF-8 -jar "%APP_JAR%" %*
if errorlevel 1 goto FAILED
exit /b 0

:NO_JAVA
echo ERROR: Java was not found. Install Java 17 or newer, or set JAVA_HOME.
:FAILED
echo If startup failed, check Java version and network access to lzhan.xyz.
if not "%~1"=="--check-connection" pause
exit /b 1
