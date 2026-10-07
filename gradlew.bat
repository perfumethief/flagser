@echo off
setlocal
set "GRADLE_VERSION=8.9"
set "WRAPPER_ROOT=%USERPROFILE%\.gradle\flagser-wrapper"
set "GRADLE_HOME=%WRAPPER_ROOT%\gradle-%GRADLE_VERSION%"
set "ZIP_FILE=%WRAPPER_ROOT%\gradle-%GRADLE_VERSION%-bin.zip"

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo Preparing Gradle %GRADLE_VERSION%...
  if not exist "%WRAPPER_ROOT%" mkdir "%WRAPPER_ROOT%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_FILE%'"
  if errorlevel 1 exit /b 1
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP_FILE%' '%WRAPPER_ROOT%'"
  if errorlevel 1 exit /b 1
)

call "%GRADLE_HOME%\bin\gradle.bat" %*
endlocal
