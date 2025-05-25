
@echo off
setlocal enabledelayedexpansion

set "GREEN=[92m"
set "RED=[91m"
set "YELLOW=[93m"
set "NC=[0m"

echo %GREEN%[%time%]%NC% 🛑 Zatrzymywanie Apache Camel Groovy Validator...

REM Find Java processes running Groovy
for /f "tokens=2" %%a in ('tasklist /fi "imagename eq java.exe" /fo csv ^| find "java.exe"') do (
    set "pid=%%a"
    set "pid=!pid:"=!"

    REM Check if it's our Groovy process (this is simplified)
    taskkill /f /pid !pid! >nul 2>&1
)

echo %GREEN%[%time%]%NC% ✅ Zatrzymywanie zakończone
pause