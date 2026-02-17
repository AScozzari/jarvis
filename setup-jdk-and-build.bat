@echo off
REM Script to extract JDK and setup build environment

setlocal enabledelayedexpansion

cd /d "C:\Users\a.scozzari\Desktop\android-app\jdk-download"

REM Extract using PowerShell
echo Extracting OpenJDK...
powershell -NoProfile -Command "Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory((Get-Item 'openjdk17.zip').FullName, (Get-Item '.').FullName, $true)" || goto error

REM Wait a bit for extraction to complete
timeout /t 30 /nobreak

REM Find the JDK directory
echo Looking for JDK...
for /d %%i in (jdk-*) do (
    set "JAVA_HOME=!CD!\%%i"
)

if "!JAVA_HOME!"=="" (
    echo JDK not found after extraction
    dir /b /s
    goto error
)

echo Found JDK: !JAVA_HOME!

REM Test Java
"!JAVA_HOME!\bin\java.exe" -version

REM Set PATH and JAVA_HOME environment variables permanently
setx JAVA_HOME "!JAVA_HOME!" /M
setx PATH "!JAVA_HOME!\bin;%%PATH%%" /M

echo Environment variables set!

REM Now run the Gradle build
cd /d "C:\Users\a.scozzari\Desktop\android-app"
echo Starting Gradle build...
call gradlew.bat build

goto done

:error
echo An error occurred!
pause
exit /b 1

:done
echo Build completed!
pause

