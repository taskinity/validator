
@echo off
setlocal enabledelayedexpansion

REM Colors (limited support in cmd)
set "GREEN=[92m"
set "RED=[91m"
set "YELLOW=[93m"
set "BLUE=[94m"
set "NC=[0m"

REM Script configuration
set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%..\"
set "LOG_FILE=%PROJECT_DIR%logs\startup.log"

REM Create logs directory
if not exist "%PROJECT_DIR%logs" mkdir "%PROJECT_DIR%logs"

echo %GREEN%[%date% %time%]%NC% 🚀 Uruchamianie Apache Camel Groovy Validator...

REM Check prerequisites
echo %BLUE%[INFO]%NC% 🔍 Sprawdzanie wymagań systemowych...

REM Check Java
java -version >nul 2>&1
if errorlevel 1 (
    echo %RED%[ERROR]%NC% Java nie jest zainstalowana. Wymagana Java 11 lub nowsza.
    pause
    exit /b 1
)

for /f tokens^=2-5^ delims^=.-_+^" %%j in ('java -version 2^>^&1 ^| find "version"') do set "java_version=%%j.%%k"
echo %BLUE%[INFO]%NC% Java wersja: !java_version!

REM Check Groovy
groovy --version >nul 2>&1
if errorlevel 1 (
    echo %RED%[ERROR]%NC% Groovy nie jest zainstalowany.
    echo Zainstaluj Groovy z: https://groovy-lang.org/download.html
    pause
    exit /b 1
)

for /f "tokens=3" %%a in ('groovy --version 2^>^&1 ^| find "Groovy"') do set "groovy_version=%%a"
echo %BLUE%[INFO]%NC% Groovy wersja: !groovy_version!

REM Setup environment
echo %BLUE%[INFO]%NC% 🔧 Konfiguracja środowiska...

cd /d "%PROJECT_DIR%"

REM Create directories
if not exist "data" mkdir data
if not exist "data\input" mkdir data\input
if not exist "data\output" mkdir data\output
if not exist "data\error" mkdir data\error
if not exist "data\archive" mkdir data\archive

REM Load environment from .env file
if exist ".env" (
    echo %GREEN%[%time%]%NC% Ładowanie konfiguracji z .env
    for /f "usebackq tokens=1,2 delims==" %%a in (".env") do (
        if not "%%a"=="" if not "%%a:~0,1%"=="#" (
            set "%%a=%%b"
        )
    )
) else (
    if exist ".env.example" (
        echo %YELLOW%[WARNING]%NC% Plik .env nie istnieje. Kopiuję z .env.example
        copy ".env.example" ".env"
        echo %BLUE%[INFO]%NC% Edytuj plik .env przed ponownym uruchomieniem
    ) else (
        echo %YELLOW%[WARNING]%NC% Nie znaleziono pliku konfiguracyjnego .env
    )
)

REM Set default values
if not defined APP_NAME set "APP_NAME=CamelGroovyValidator"
if not defined ENVIRONMENT set "ENVIRONMENT=development"
if not defined HAWTIO_PORT set "HAWTIO_PORT=8080"
if not defined HEALTH_PORT set "HEALTH_PORT=9090"

echo %BLUE%[INFO]%NC% Środowisko: !ENVIRONMENT!
echo %BLUE%[INFO]%NC% Porty: Hawtio=!HAWTIO_PORT!, Health=!HEALTH_PORT!

REM Check if already running
tasklist /fi "imagename eq java.exe" /fo csv | find "groovy" >nul
if not errorlevel 1 (
    echo %YELLOW%[WARNING]%NC% Aplikacja może już działać. Sprawdź procesy Java.
)

REM Start application
echo %GREEN%[%time%]%NC% 🚀 Uruchamianie aplikacji...

if "%1"=="--background" goto start_background
if "%1"=="-d" goto start_background

REM Interactive mode
echo %BLUE%[INFO]%NC% Uruchamianie w trybie interaktywnym...
groovy run.groovy
goto end

:start_background
echo %BLUE%[INFO]%NC% Uruchamianie w tle...
start "CamelGroovyValidator" /min groovy run.groovy
timeout /t 5 >nul

REM Wait for startup and show URLs
echo %GREEN%[%time%]%NC% ⏳ Oczekiwanie na uruchomienie usług...
timeout /t 10 >nul

echo %GREEN%[%time%]%NC% 🌐 Aplikacja powinna być dostępna pod adresami:
echo    📊 Hawtio Dashboard: http://localhost:!HAWTIO_PORT!/hawtio
echo    🔧 Health Check: http://localhost:!HEALTH_PORT!/health
echo    📈 Metrics: http://localhost:!HEALTH_PORT!/metrics

echo %GREEN%[%time%]%NC% 🎉 Uruchomienie zakończone!
echo %BLUE%[INFO]%NC% 🛑 Aby zatrzymać: scripts\stop.bat

:end
pause
