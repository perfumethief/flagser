@echo off
setlocal EnableExtensions
title Upload Flagser to GitHub

echo.
echo ==========================================
echo        FLAGSER - UPLOAD TO GITHUB
echo ==========================================
echo.

REM Make sure we run from the folder containing this BAT file
cd /d "%~dp0"

REM Check Git
where git >nul 2>&1
if errorlevel 1 (
    echo Git is not installed or not available in PATH.
    echo.
    echo Download Git for Windows from:
    echo https://git-scm.com/download/win
    echo.
    pause
    exit /b 1
)

echo Git found.
echo Project folder:
echo %CD%
echo.

REM Initialize repository if needed
if not exist ".git" (
    echo Initializing Git repository...
    git init
    if errorlevel 1 goto :error
)

REM Make sure branch is called main
git branch -M main
if errorlevel 1 goto :error

REM Configure identity only if missing
for /f "delims=" %%A in ('git config user.name 2^>nul') do set "GITNAME=%%A"
if not defined GITNAME (
    echo.
    set /p GITNAME=Enter your Git/GitHub name: 
    if not defined GITNAME set "GITNAME=oxypetals"
    git config user.name "%GITNAME%"
)

for /f "delims=" %%A in ('git config user.email 2^>nul') do set "GITEMAIL=%%A"
if not defined GITEMAIL (
    echo.
    set /p GITEMAIL=Enter the email you use for GitHub: 
    if not defined GITEMAIL (
        echo An email is required for the commit.
        pause
        exit /b 1
    )
    git config user.email "%GITEMAIL%"
)

REM Set the correct GitHub remote
git remote get-url origin >nul 2>&1
if errorlevel 1 (
    echo Connecting to GitHub repository...
    git remote add origin https://github.com/oxypetals/flagser.git
) else (
    git remote set-url origin https://github.com/oxypetals/flagser.git
)

REM Add all project files, including .github
echo.
echo Adding Flagser files...
git add -A
if errorlevel 1 goto :error

REM Commit only when there is something to commit
git diff --cached --quiet
if errorlevel 1 (
    echo Creating commit...
    git commit -m "Upload Flagser Android app"
    if errorlevel 1 goto :error
) else (
    echo No new local changes need committing.
)

REM If the remote already has a main branch, merge it safely first
echo.
echo Checking GitHub repository...
git ls-remote --exit-code --heads origin main >nul 2>&1
if not errorlevel 1 (
    echo Existing main branch found on GitHub.
    echo Syncing it before upload...
    git pull origin main --rebase --autostash
    if errorlevel 1 (
        echo.
        echo Automatic sync could not be completed.
        echo This usually means the GitHub repo already contains conflicting files.
        echo Nothing has been force-overwritten.
        pause
        exit /b 1
    )
)

echo.
echo Uploading to GitHub...
echo If GitHub asks you to sign in, complete the browser sign-in.
echo.
git push -u origin main
if errorlevel 1 goto :error

echo.
echo ==========================================
echo SUCCESS!
echo Flagser is now uploaded to:
echo https://github.com/oxypetals/flagser
echo.
echo Next: open GitHub ^> Actions
echo       ^> Build Flagser APK ^> Run workflow
echo ==========================================
echo.
pause
exit /b 0

:error
echo.
echo ==========================================
echo Something went wrong.
echo Copy the error shown above and send it to ChatGPT.
echo ==========================================
echo.
pause
exit /b 1
