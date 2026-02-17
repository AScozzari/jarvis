@echo off
REM Script to extract JDK using tar command (Windows 10+) and setup build environment

setlocal enabledelayedexpansion

cd /d "C:\Users\a.scozzari\Desktop\android-app\jdk-download"

echo Extracting OpenJDK using tar...
tar -xf openjdk17.zip

if errorlevel 1 (
    echo Extraction failed with tar, trying PowerShell...
    powershell -NoProfile -Command "Expand-Archive -Path 'openjdk17.zip' -DestinationPath '.' -Force"
    if errorlevel 1 (
        echo PowerShell extraction also failed
        pause
        exit /b 1
    )
)

echo Extraction completed!
timeout /t 10 /nobreak

REM Find the JDK directory
echo Looking for JDK directory...
for /d %%i in (jdk-*) do (
    set "JAVA_HOME=%%~fi"
    goto found_jdk
)

echo JDK directory not found!
echo Contents of jdk-download:
dir /b
pause
exit /b 1

:found_jdk
echo Found JDK at: !JAVA_HOME!

REM Test Java
echo Testing Java installation...
"!JAVA_HOME!\bin\java.exe" -version

if errorlevel 1 (
    echo Java test failed!
    pause
    exit /b 1
)

REM Set PATH and JAVA_HOME environment variables permanently
echo Setting environment variables...
setx JAVA_HOME "!JAVA_HOME!" /M
setx PATH "!JAVA_HOME!\bin;!PATH!" /M

echo Environment variables set successfully!

REM Now run the Gradle build
cd /d "C:\Users\a.scozzari\Desktop\android-app"
echo.
echo ====================================
echo Starting Gradle build...
echo ====================================
echo.
call gradlew.bat build

pause

