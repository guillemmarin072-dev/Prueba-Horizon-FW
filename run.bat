@echo off
rem Compila y arranca la web. Requiere JDK 17 o superior.   Uso: run.bat   (puerto: set PORT=8080)
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
dir /s /b src\main\java\*.java > sources.txt
javac --release 17 -d out @sources.txt
if errorlevel 1 (del sources.txt & exit /b 1)
del sources.txt
xcopy /e /i /y src\main\resources out >nul
java -cp out com.hfw.builds.Main
