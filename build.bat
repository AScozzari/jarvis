@echo off
setlocal enabledelayedexpansion

echo Searching for Java...

REM Check Android SDK jdk
if exist "C:\Users\a.scozzari\AppData\Local\Android\Sdk\jdk\jdk17\bin\java.exe" (
    set "JAVA_HOME=C:\Users\a.scozzari\AppData\Local\Android\Sdk\jdk\jdk17"
    echo Found Java at !JAVA_HOME!
    goto found
)

if exist "C:\Users\a.scozzari\AppData\Local\Android\Sdk\jdk\bin\java.exe" (
    set "JAVA_HOME=C:\Users\a.scozzari\AppData\Local\Android\Sdk\jdk"
    echo Found Java at !JAVA_HOME!
    goto found
)

REM Check Program Files
if exist "C:\Program Files\Java" (
    for /d %%i in ("C:\Program Files\Java\jdk*") do (
        if exist "%%i\bin\java.exe" (
            set "JAVA_HOME=%%i"
            echo Found Java at !JAVA_HOME!
            goto found
        )
    )
)

REM Check Program Files (x86)
if exist "C:\Program Files (x86)\Java" (
    for /d %%i in ("C:\Program Files (x86)\Java\jdk*") do (
        if exist "%%i\bin\java.exe" (
            set "JAVA_HOME=%%i"
            echo Found Java at !JAVA_HOME!
            goto found
        )
    )
)

echo Java not found!
exit /b 1

:found
set "PATH=!JAVA_HOME!\bin;!PATH!"
cd /d "C:\Users\a.scozzari\Desktop\android-app"
echo Running gradle build...
call gradlew.bat build

