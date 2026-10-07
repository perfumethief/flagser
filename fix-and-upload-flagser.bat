@echo off
setlocal EnableExtensions
title Fix + Upload Flagser to GitHub

cd /d "%~dp0"

echo.
echo ==========================================
echo     FLAGSER - FIX + UPLOAD TO GITHUB
echo ==========================================
echo.

where git >nul 2>&1
if errorlevel 1 (
    echo Git is not available.
    pause
    exit /b 1
)

REM Set identity locally for this repository only
git config user.name "oxypetals"
git config user.email "oxypetals@users.noreply.github.com"

REM Initialize if needed
if not exist ".git" git init

git branch -M main

REM Ensure correct remote
git remote get-url origin >nul 2>&1
if errorlevel 1 (
    git remote add origin https://github.com/oxypetals/flagser.git
) else (
    git remote set-url origin https://github.com/oxypetals/flagser.git
)

echo Adding files...
git add -A
if errorlevel 1 goto :error

echo Creating commit...
git diff --cached --quiet
if errorlevel 1 (
    git commit -m "Upload Flagser Android app"
    if errorlevel 1 goto :error
) else (
    echo Nothing new to commit.
)

echo.
echo Checking GitHub...
git ls-remote --exit-code --heads origin main >nul 2>&1
if not errorlevel 1 (
    echo Existing main branch found. Syncing first...
    git pull origin main --rebase --autostash
    if errorlevel 1 goto :syncerror
)

echo.
echo Uploading Flagser...
git push -u origin main
if errorlevel 1 goto :error

echo.
echo ==========================================
echo SUCCESS!
echo Open:
echo https://github.com/oxypetals/flagser/actions
echo ==========================================
echo.
pause
exit /b 0

:syncerror
echo.
echo GitHub already contains files that could not be merged automatically.
echo Send me a screenshot of the message above and I will fix it.
echo.
pause
exit /b 1

:error
echo.
echo Something went wrong.
echo Send me a screenshot of the error above.
echo.
pause
exit /b 1
