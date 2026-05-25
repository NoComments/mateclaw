@echo off
chcp 65001 >nul 2>&1
echo Stopping SurveyMind Server...

for /f "tokens=5" %%a in ('netstat -ano ^| findstr :18088 ^| findstr LISTENING') do (
    echo Killing process %%a
    taskkill /F /PID %%a >nul 2>&1
)

echo SurveyMind Server stopped.
pause
