@echo off
rem Compila y ejecuta las pruebas. Requiere JDK 17 o superior.
cd /d "%~dp0"
if exist out-test rmdir /s /q out-test
mkdir out-test
dir /s /b src\main\java\*.java src\test\java\*.java > sources.txt
javac --release 17 -d out-test @sources.txt
if errorlevel 1 (del sources.txt & exit /b 1)
del sources.txt
xcopy /e /i /y src\main\resources out-test >nul
java -cp out-test com.hfw.builds.SelfTest
set RESULT=%errorlevel%
rmdir /s /q out-test
exit /b %RESULT%
