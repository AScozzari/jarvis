@echo off
setlocal enabledelayedexpansion

REM Extract JDK using PowerShell
echo Extracting JDK...
powershell -Command "Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('C:\Users\a.scozzari\Desktop\android-app\java-setup\jdk17.zip', 'C:\Users\a.scozzari\Desktop\android-app\java-setup', $true)"

if errorlevel 1 (
    echo Extraction failed!
    exit /b 1
)

echo Extraction complete!

REM Find the JDK directory
for /d %%i in ("C:\Users\a.scozzari\Desktop\android-app\java-setup\jdk-*") do (
    set "JAVA_HOME=%%i"
)

if "!JAVA_HOME!"=="" (
    echo JDK directory not found!
    echo Contents of java-setup:
    dir "C:\Users\a.scozzari\Desktop\android-app\java-setup"
    exit /b 1
)

echo Found JDK at: !JAVA_HOME!
echo Setting JAVA_HOME environment variable...

REM Set JAVA_HOME and run the build
setx JAVA_HOME "!JAVA_HOME!" /M

REM Update PATH
set "PATH=!JAVA_HOME!\bin;!PATH!"
setx PATH "!JAVA_HOME!\bin;!PATH!" /M

echo JAVA_HOME set successfully!
echo Running gradle build...

cd /d "C:\Users\a.scozzari\Desktop\android-app"
call gradlew.bat build

pause

