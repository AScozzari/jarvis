@echo off
REM Build senza daemon per evitare "Unable to delete directory" su Windows.
REM Imposta JAVA_HOME se non definito (percorso tipico Android Studio).
if "%JAVA_HOME%"=="" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

echo Stopping Gradle daemons...
call gradlew.bat --stop
timeout /t 2 /nobreak >nul

echo Building debug APK (no daemon)...
call gradlew.bat assembleDebug --no-daemon

pause
