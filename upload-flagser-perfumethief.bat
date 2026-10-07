@echo off
setlocal EnableExtensions
title Upload Flagser to GitHub

cd /d "%~dp0"

echo.
echo ==========================================
echo     FLAGSER - UPLOAD TO GITHUB
echo ==========================================
echo.

where git >nul 2>&1
if errorlevel 1 (
    echo Git is not available.
    echo Install Git first, then run this again.
    pause
    exit /b 1
)

REM Initialize repository if needed
if not exist ".git" (
    echo Initializing Git repository...
    git init
    if errorlevel 1 goto :error
)

REM Use local repo identity
git config user.name "perfumethief"
git config user.email "perfumethief@users.noreply.github.com"

git branch -M main
if errorlevel 1 goto :error

REM Set correct GitHub remote
git remote get-url origin >nul 2>&1
if errorlevel 1 (
    echo Connecting to GitHub...
    git remote add origin https://github.com/perfumethief/flagser.git
) else (
    git remote set-url origin https://github.com/perfumethief/flagser.git
)

echo.
echo Adding Flagser files...
git add -A
if errorlevel 1 goto :error

git diff --cached --quiet
if errorlevel 1 (
    echo Creating commit...
    git commit -m "Upload Flagser Android app"
    if errorlevel 1 goto :error
) else (
    echo Nothing new to commit.
)

echo.
echo Checking GitHub repository...
git ls-remote --exit-code --heads origin main >nul 2>&1
if not errorlevel 1 (
    echo Existing main branch found. Syncing first...
    git pull origin main --rebase --autostash
    if errorlevel 1 goto :syncerror
)

echo.
echo Uploading Flagser...
echo If GitHub asks you to sign in, complete the sign-in.
echo.
git push -u origin main
if errorlevel 1 goto :error

echo.
echo ==========================================
echo SUCCESS!
echo.
echo Open:
echo https://github.com/perfumethief/flagser/actions
echo.
echo Then:
echo Actions ^> Build Flagser APK ^> Run workflow
echo ==========================================
echo.
pause
exit /b 0

:syncerror
echo.
echo The repo already contains files that could not be merged automatically.
echo Send me a screenshot of the message above and I can fix it.
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
