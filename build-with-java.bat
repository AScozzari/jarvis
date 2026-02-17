@echo off
setlocal enabledelayedexpansion

REM Check if JDK exists in various locations
set "JAVA_HOME="

REM Check Android SDK
if exist "C:\Users\a.scozzari\AppData\Local\Android\Sdk\jdk\jdk17\bin\java.exe" (
    set "JAVA_HOME=C:\Users\a.scozzari\AppData\Local\Android\Sdk\jdk\jdk17"
    goto found
)

if exist "C:\Users\a.scozzari\AppData\Local\Android\Sdk\jdk\bin\java.exe" (
    set "JAVA_HOME=C:\Users\a.scozzari\AppData\Local\Android\Sdk\jdk"
    goto found
)

REM Check our downloaded JDK
if exist "C:\Users\a.scozzari\Desktop\android-app\jdk-final\bin\java.exe" (
    set "JAVA_HOME=C:\Users\a.scozzari\Desktop\android-app\jdk-final"
    goto found
)

REM Check standard locations
if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-17"
    goto found
)

echo ERROR: Java not found!
echo Please ensure JDK 17 is installed.
pause
exit /b 1

:found
echo Found Java at: !JAVA_HOME!
"!JAVA_HOME!\bin\java.exe" -version

REM Build with Gradle
cd /d "C:\Users\a.scozzari\Desktop\android-app"
set "PATH=!JAVA_HOME!\bin;!PATH!"
gradlew.bat build

pause

