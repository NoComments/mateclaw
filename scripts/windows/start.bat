@echo off
chcp 65001 >nul 2>&1
title SurveyMind Server

set "APP_HOME=%~dp0"
cd /d "%APP_HOME%"

set "JAVA_HOME=%APP_HOME%jre"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ============================================
echo   SurveyMind - AI Agent Platform
echo   Starting server...
echo ============================================
echo.

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JRE not found at %JAVA_HOME%
    echo Please ensure the jre/ folder is present.
    pause
    exit /b 1
)

if not exist "%APP_HOME%license.lic" (
    echo [WARNING] license.lic not found!
    echo The application will start but API access will be blocked.
    echo Please place license.lic in: %APP_HOME%
    echo.
)

echo Access URL: http://localhost:18088
echo Default login: admin / admin123
echo Press Ctrl+C to stop the server.
echo.

java -Xms512m -Xmx2g -jar "%APP_HOME%surveymind-server.jar"
pause
