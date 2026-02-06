@echo off
setlocal enabledelayedexpansion

:: ==============================================
:: CONFIGURATION
:: ==============================================
set "APP_NAME=SchoolScheduler"
set "VENDOR=HLoc"
set "MAIN_CLASS=application.AppLauncher"

:: The artifact ID matches your jar name (scheduler.application-X.X.X.jar)
set "ARTIFACT_ID=scheduler.application"

:: Output Paths
set "DIST_INPUT=target\dist-input"
set "OUT_PORTABLE=target\portable"
set "OUT_INSTALLER=target\installer"

echo ==============================================
echo [0/5] FORCE CLEANING (kill process and delete target)...
echo ==============================================

:: 1. Kill app process if running
taskkill /F /IM "%APP_NAME%.exe" /T >nul 2>&1

:: 2. Kill Java process (Optional/Safety)
:: taskkill /F /IM "java.exe" /T >nul 2>&1

:: Wait for release
timeout /t 2 /nobreak >nul

:: 3. Delete target
if exist "target" (
    echo Attempting to delete target directory...
    rmdir /s /q "target"
    if exist "target" (
        echo.
        echo [WARNING] Could not delete 'target' directory.
        echo Check if IntelliJ or a console is holding the files.
        pause
        rmdir /s /q "target"
    )
)

echo ==============================================
echo [1/5] DETECTING PROJECT VERSION (PowerShell)...
echo ==============================================

for /f "usebackq tokens=*" %%v in (`powershell -NoProfile -Command "$xml = [xml](Get-Content pom.xml); $xml.project.version"`) do (
    set "APP_VERSION=%%v"
)

if "%APP_VERSION%"=="" (
    echo [ERROR] Could not detect version from pom.xml.
    pause
    exit /b 1
)

echo Detected Version: [%APP_VERSION%]
set "MAIN_JAR=%ARTIFACT_ID%-%APP_VERSION%.jar"

echo ==============================================
echo [2/5] BUILDING JAR WITH MAVEN...
echo ==============================================
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo [ERROR] Maven build failed.
    pause
    exit /b %errorlevel%
)

echo.
echo ==============================================
echo [3/5] PREPARING DISTRIBUTION DIRECTORY...
echo ==============================================
:: JPackage needs a clean folder containing ONLY the files to be bundled.
:: We copy the JAR from target/ to target/dist-input/

if not exist "%DIST_INPUT%" mkdir "%DIST_INPUT%"

:: Copy Main Jar
copy "target\%MAIN_JAR%" "%DIST_INPUT%\" >nul

:: (Optional) If you have a 'lib' folder for dependencies, uncomment below:
:: if exist "target\lib" xcopy /s /i "target\lib" "%DIST_INPUT%\lib" >nul

if not exist "%DIST_INPUT%\%MAIN_JAR%" (
    echo [ERROR] Could not find %MAIN_JAR% in %DIST_INPUT%.
    echo Expected file name: %MAIN_JAR%
    echo Please check if your pom.xml artifactId matches the script configuration.
    pause
    exit /b 1
)

echo Files prepared in %DIST_INPUT%

echo.
echo ==============================================
echo [4/5] GENERATING PORTABLE VERSION (App Image)...
echo ==============================================

jpackage ^
  --type app-image ^
  --dest "%OUT_PORTABLE%" ^
  --input "%DIST_INPUT%" ^
  --name "%APP_NAME%Portable" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class "%MAIN_CLASS%" ^
  --app-version "%APP_VERSION%" ^
  --vendor "%VENDOR%"

if %errorlevel% neq 0 (
    echo [ERROR] JPackage Portable failed.
    pause
    exit /b %errorlevel%
)

jpackage ^
  --type app-image ^
  --dest "%OUT_PORTABLE%" ^
  --input "%DIST_INPUT%" ^
  --name "%APP_NAME%Portable-console" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class "%MAIN_CLASS%" ^
  --app-version "%APP_VERSION%" ^
  --vendor "%VENDOR%" ^
  --win-console

if %errorlevel% neq 0 (
    echo [ERROR] JPackage Portable console failed.
    pause
    exit /b %errorlevel%
)

echo.
echo ==============================================
echo [5/5] GENERATING INSTALLERS (EXE ^& MSI)...
echo ==============================================

:: Ensure installer directory exists
if not exist "%OUT_INSTALLER%" mkdir "%OUT_INSTALLER%"

echo Generating EXE Installer...
jpackage ^
  --type exe ^
  --dest "%OUT_INSTALLER%" ^
  --input "%DIST_INPUT%" ^
  --name "%APP_NAME%" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class "%MAIN_CLASS%" ^
  --app-version "%APP_VERSION%" ^
  --vendor "%VENDOR%" ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --win-per-user-install

if %errorlevel% neq 0 (
    echo [ERROR] JPackage EXE failed.
    pause
    exit /b %errorlevel%
)

echo Generating MSI Installer...
jpackage ^
  --type msi ^
  --dest "%OUT_INSTALLER%" ^
  --input "%DIST_INPUT%" ^
  --name "%APP_NAME%" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class "%MAIN_CLASS%" ^
  --app-version "%APP_VERSION%" ^
  --vendor "%VENDOR%" ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --win-per-user-install

if %errorlevel% neq 0 (
    echo [ERROR] JPackage MSI failed.
    pause
    exit /b %errorlevel%
)

echo.
echo ==============================================
echo BUILD SUCCESSFUL!
echo ==============================================
echo 1. Portable:  %CD%\%OUT_PORTABLE%\%APP_NAME%Portable
echo 2. Installer: %CD%\%OUT_INSTALLER%\%APP_NAME%-%APP_VERSION%.exe
echo 3. Installer: %CD%\%OUT_INSTALLER%\%APP_NAME%-%APP_VERSION%.msi
echo ==============================================
pause