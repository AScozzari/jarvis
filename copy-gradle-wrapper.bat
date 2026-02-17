@echo off
setlocal enabledelayedexpansion

set "SRC=C:\Users\a.scozzari\Desktop\android-app\gradle\wrapper\gradle-8.7\lib\plugins\gradle-wrapper-8.7.jar"
set "DST=C:\Users\a.scozzari\Desktop\android-app\gradle\wrapper\gradle-wrapper.jar"

echo Checking if source file exists...
if not exist "!SRC!" (
    echo ERROR: Source file not found at !SRC!
    exit /b 1
)

echo Source file found!
echo Copying to !DST!...

REM Use PowerShell to copy
powershell -Command "Copy-Item -Path '!SRC!' -Destination '!DST!' -Force"

if exist "!DST!" (
    echo Success! File copied to !DST!
    for %%F in ("!DST!") do echo File size: %%~zF bytes
) else (
    echo ERROR: File was not copied!
    exit /b 1
)

pause

