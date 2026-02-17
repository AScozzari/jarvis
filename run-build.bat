@echo off
cd /d "C:\Users\a.scozzari\Desktop\android-app"
set JAVA_HOME=C:\Users\a.scozzari\Desktop\android-app\java-setup\jdk-17.0.10+7
set PATH=%JAVA_HOME%\bin;%PATH%
echo JAVA_HOME=%JAVA_HOME%
gradlew.bat build

