@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo ========================================
echo   Initialize Herb Manager Database
echo ========================================
echo.
echo [1] MySQL 8.0 / 8.4 (recommended for the course project)
echo [2] Local H2 database (no MySQL required)
choice /C 12 /N /M "Choose database mode [1/2]: "
if errorlevel 2 goto INIT_H2
goto INIT_MYSQL

:INIT_MYSQL
set "DB_HOST=127.0.0.1"
set "DB_PORT=3306"
set "DB_USER=root"
set /p "DB_HOST=MySQL host [127.0.0.1]: "
if "%DB_HOST%"=="" set "DB_HOST=127.0.0.1"
set /p "DB_PORT=MySQL port [3306]: "
if "%DB_PORT%"=="" set "DB_PORT=3306"
set /p "DB_USER=MySQL username [root]: "
if "%DB_USER%"=="" set "DB_USER=root"
set /p "DB_PASS=MySQL password: "
set "DB_NAME=herb_manager"
set /p "DB_NAME=Database name [herb_manager]: "
if "%DB_NAME%"=="" set "DB_NAME=herb_manager"
set "SMTP_USER=3856638905@qq.com"
set /p "SMTP_USER=QQ SMTP sender email [3856638905@qq.com]: "
if "%SMTP_USER%"=="" set "SMTP_USER=3856638905@qq.com"
set /p "SMTP_PASS=QQ SMTP authorization code: "

set "MYSQL_CMD=mysql"
where mysql >nul 2>nul
if errorlevel 1 set "MYSQL_CMD=C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
if not exist "%MYSQL_CMD%" (
    echo [ERROR] mysql.exe was not found. Install MySQL or choose H2 mode.
    pause
    exit /b 1
)

echo Creating database %DB_NAME%...
"%MYSQL_CMD%" --host="%DB_HOST%" --port="%DB_PORT%" --user="%DB_USER%" --password="%DB_PASS%" -e "CREATE DATABASE IF NOT EXISTS `%DB_NAME%` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if errorlevel 1 (
    echo [ERROR] MySQL connection or database creation failed.
    pause
    exit /b 1
)

set "DB_URL=jdbc:mysql://%DB_HOST%:%DB_PORT%/%DB_NAME%?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai^&useSSL=false^&allowPublicKeyRetrieval=true"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$lines=@('jdbc.url='+$env:DB_URL,'jdbc.username='+$env:DB_USER,'jdbc.password='+$env:DB_PASS,'smtp.host=smtp.qq.com','smtp.port=465','smtp.username='+$env:SMTP_USER,'smtp.password='+$env:SMTP_PASS,'app.default-email='+$env:SMTP_USER); [IO.File]::WriteAllLines((Join-Path (Get-Location) 'config.properties'),$lines,(New-Object Text.UTF8Encoding($false)))"
goto BUILD_AND_INIT

:INIT_H2
echo Using local H2 database. MySQL is not required on this device.
set "SMTP_USER=3856638905@qq.com"
set /p "SMTP_USER=QQ SMTP sender email [3856638905@qq.com]: "
if "%SMTP_USER%"=="" set "SMTP_USER=3856638905@qq.com"
set /p "SMTP_PASS=QQ SMTP authorization code: "
powershell -NoProfile -ExecutionPolicy Bypass -Command "$lines=@('jdbc.url=jdbc:h2:file:./data/herb-manager;MODE=MySQL;DB_CLOSE_DELAY=-1','jdbc.username=sa','jdbc.password=','smtp.host=smtp.qq.com','smtp.port=465','smtp.username='+$env:SMTP_USER,'smtp.password='+$env:SMTP_PASS,'app.default-email='+$env:SMTP_USER); New-Item -ItemType Directory -Force data | Out-Null; [IO.File]::WriteAllLines((Join-Path (Get-Location) 'config.properties'),$lines,(New-Object Text.UTF8Encoding($false)))"

:BUILD_AND_INIT
if not exist "target\herb-manager.jar" (
    set "MVN_CMD=mvn"
    where mvn >nul 2>nul
    if errorlevel 1 set "MVN_CMD=C:\Users\xushen\tools\apache-maven-3.9.15\bin\mvn.cmd"
    if not exist "%MVN_CMD%" (
        echo [ERROR] Maven was not found and the JAR is missing.
        pause
        exit /b 1
    )
    echo Building project...
    call "%MVN_CMD%" package -q
    if errorlevel 1 (
        echo [ERROR] Maven build failed.
        pause
        exit /b 1
    )
)

echo Creating tables, demo users and seed herbs...
java -cp "target\herb-manager.jar" cn.herb.InitApp config.properties
if errorlevel 1 (
    echo [ERROR] Database initialization failed. Check config.properties.
    pause
    exit /b 1
)
echo.
echo Initialization completed. Run start.bat to launch the application.
echo Demo accounts: admin / Admin123!, pharmacist / Pharm123!, guest / Guest123!
pause
endlocal
