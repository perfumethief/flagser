@echo off
setlocal
cd /d "%~dp0"

if not defined ANDROID_SDK_ROOT set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
if not exist "%ANDROID_SDK_ROOT%\platforms" (
  echo Android SDK was not found at:
  echo %ANDROID_SDK_ROOT%
  echo.
  echo Open Android Studio once and install the Android SDK, then run this again.
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "$p='%ANDROID_SDK_ROOT%'.Replace('\\','/'); Set-Content -Encoding ASCII -Path 'local.properties' -Value ('sdk.dir='+$p)"
call gradlew.bat assembleDebug
if errorlevel 1 (
  echo.
  echo Build failed. Open this folder in Android Studio to see the exact error.
  pause
  exit /b 1
)
copy /Y "app\build\outputs\apk\debug\app-debug.apk" "flagser-debug.apk" >nul

echo.
echo DONE - flagser-debug.apk is in this folder.
pause
endlocal
