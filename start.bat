@echo off
setlocal
cd /d "%~dp0"

echo ========================================
echo   Herb Manager - Java Swing Application
echo ========================================

where java >nul 2>nul
if errorlevel 1 goto NO_JAVA

if exist "target\herb-manager.jar" goto RUN_APP

echo JAR not found. Building with Maven...
set "MVN_CMD=mvn"
where mvn >nul 2>nul
if errorlevel 1 set "MVN_CMD=C:\Users\xushen\tools\apache-maven-3.9.15\bin\mvn.cmd"
if not exist "%MVN_CMD%" goto NO_MAVEN
call "%MVN_CMD%" package -q
if errorlevel 1 goto BUILD_FAILED

:RUN_APP
echo Starting application...
java -Dfile.encoding=UTF-8 -jar "target\herb-manager.jar"
if errorlevel 1 goto APP_FAILED
goto END

:NO_JAVA
echo ERROR: Java 17 or newer was not found.
pause
exit /b 1

:NO_MAVEN
echo ERROR: Maven was not found. Run "mvn package" manually first.
pause
exit /b 1

:BUILD_FAILED
echo ERROR: Maven build failed.
pause
exit /b 1

:APP_FAILED
echo ERROR: Application exited unexpectedly. Check config.properties and MySQL.
pause
exit /b 1

:END
endlocal
