@echo off
setlocal enabledelayedexpansion

set "JAVA_HOME=C:\Users\a.scozzari\Desktop\android-app\jdk-final"
set "PATH=!JAVA_HOME!\bin;!PATH!"

cd /d "C:\Users\a.scozzari\Desktop\android-app\gradle\wrapper"

echo Extracting Gradle wrapper zip using PowerShell...

powershell -NoProfile -Command ^
  "Add-Type -AssemblyName System.IO.Compression.FileSystem; ^
   [System.IO.Compression.ZipFile]::ExtractToDirectory('gradle-8.7-all.zip', '.', $true); ^
   Copy-Item 'gradle-8.7\gradle\wrapper\gradle-wrapper.jar' '.'; ^
   Remove-Item 'gradle-8.7' -Recurse -Force; ^
   Remove-Item 'gradle-8.7-all.zip' -Force"

if errorlevel 1 (
    echo Extraction failed, trying alternative method...

    REM Use jar command from Java
    "!JAVA_HOME!\bin\jar.exe" xf "gradle-8.7-all.zip"

    if exist "gradle-8.7\gradle\wrapper\gradle-wrapper.jar" (
        copy "gradle-8.7\gradle\wrapper\gradle-wrapper.jar" .
        rmdir /s /q "gradle-8.7"
        del "gradle-8.7-all.zip"
    ) else (
        echo ERROR: Could not extract gradle-wrapper.jar
        pause
        exit /b 1
    )
)

echo gradle-wrapper.jar successfully extracted!
pause

