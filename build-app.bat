@echo off
setlocal enabledelayedexpansion

REM === Config ===
set "PROJECT_ROOT=%cd%\android-app"
set "APP_DIR=%PROJECT_ROOT%\app"
set "SDK_DIR=%cd%\android-sdk-temp"
set "GRADLE_DIR=%cd%\gradle-temp"
set "OUTPUT_DIR=%cd%\apk-output"
set "JAVA_HOME=C:\Program Files\Java\jdk-17"

REM === Add JDK bin to PATH ===
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM === Create dirs if missing ===
if not exist "!SDK_DIR!" mkdir "!SDK_DIR!"
if not exist "!GRADLE_DIR!" mkdir "!GRADLE_DIR!"
if not exist "!OUTPUT_DIR!" mkdir "!OUTPUT_DIR!"

echo.
echo [*] Checking Android command-line tools...
if not exist "!SDK_DIR!\cmdline-tools" (
    echo [*] Downloading Android command-line tools...
    powershell -Command "Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-9477386_latest.zip' -OutFile cmdline-tools.zip"
    echo [*] Extracting SDK tools...
    powershell -Command "Expand-Archive cmdline-tools.zip -DestinationPath '!SDK_DIR!' -Force"
    del cmdline-tools.zip
)

REM === Add SDK tools to PATH ===
set "PATH=!SDK_DIR!\cmdline-tools\bin;!PATH!"

echo.
echo [*] Installing minimal SDK packages...
call sdkmanager.bat --sdk_root="!SDK_DIR!" "platform-tools" "platforms;android-33" "build-tools;33.0.2"

echo.
echo [*] Creating local.properties...
REM Replace backslashes with double-backslashes for Gradle
set "LOCAL_SDK_DIR=!SDK_DIR:\=\\!"
echo sdk.dir=!LOCAL_SDK_DIR!>"!PROJECT_ROOT!\local.properties"

echo.
echo [*] Downloading portable Gradle if missing...
if not exist "!GRADLE_DIR!\gradle-8.6\bin" (
    powershell -Command "Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.6-bin.zip' -OutFile gradle.zip"
    powershell -Command "Expand-Archive gradle.zip -DestinationPath '!GRADLE_DIR!' -Force"
    del gradle.zip
)

for /d %%i in ("!GRADLE_DIR!\gradle-*") do set "GRADLE_HOME=%%i"
set "PATH=!GRADLE_HOME!\bin;!PATH!"
set "GRADLE_USER_HOME=!GRADLE_DIR!\cache"

cd /d "!PROJECT_ROOT!"

REM === Generate gradlew wrapper if missing ===
if not exist "gradlew.bat" (
    echo [*] gradlew.bat not found, generating wrapper using portable Gradle...
    call gradle.bat wrapper
    if errorlevel 1 (
        echo [!] Failed to generate gradlew.bat. Make sure the project has build.gradle/settings.gradle.
        pause
        exit /b 1
    )
)

echo.
echo [*] Building APK using Gradle wrapper...
call "!PROJECT_ROOT!\gradlew.bat" ":app:assembleDebug"

echo.
echo [*] Copying APK to output folder...
copy "!APP_DIR!\build\outputs\apk\debug\app-debug.apk" "!OUTPUT_DIR!\" >nul 2>&1

if exist "!OUTPUT_DIR!\app-debug.apk" (
    echo.
    echo [✓] Build complete! APK is here:
    echo     !OUTPUT_DIR!\app-debug.apk
) else (
    echo.
    echo [!] Build failed; APK not found. Check logs above.
)

endlocal
pause
