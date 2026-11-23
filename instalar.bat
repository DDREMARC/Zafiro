@echo off
rem Compila, instala y abre Z4firo en el telefono conectado por USB.
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set ADB=%ANDROID_HOME%\platform-tools\adb.exe
call gradlew.bat installDebug || exit /b 1
"%ADB%" shell am start -n com.dremarc.grokbot/.MainActivity
