@echo off
REM ==========================================
REM Web2Book Runner Script for Windows
REM This script can be double-clicked to run the application
REM ==========================================

REM Get the directory where this script is located
set SCRIPT_DIR=%~dp0

REM Change to the script directory
cd /d "%SCRIPT_DIR%"

REM Print header
echo ==========================================
echo Web2Book - Manga to EPUB/PDF Converter
echo ==========================================
echo.
echo Project directory: %SCRIPT_DIR%
echo.

REM Check if Java is installed
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 17 or higher
    echo.
    pause
    exit /b 1
)

REM Check Java version (requires Java 17+)
for /f "tokens=2" %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION_STRING=%%i
    goto :check_version
)
:check_version
REM Extract major version number
for /f "tokens=1 delims=." %%a in ("%JAVA_VERSION_STRING%") do set MAJOR_VERSION=%%a
REM Remove quotes and "1." prefix if present
set MAJOR_VERSION=%MAJOR_VERSION:"=%
set MAJOR_VERSION=%MAJOR_VERSION:1.=%

REM Check if version is less than 17
if %MAJOR_VERSION% LSS 17 (
    echo ERROR: Java 17 or higher is required. Found Java %MAJOR_VERSION%
    echo.
    pause
    exit /b 1
)

echo Java version:
java -version 2>&1 | findstr /i "version"
echo.

REM Check if JAR exists
if not exist "web2book.jar" (
    echo ERROR: web2book.jar not found in the current directory
    echo Please ensure web2book.jar exists in: %SCRIPT_DIR%
    echo.
    pause
    exit /b 1
)

REM Check if web2book.properties exists
if not exist "web2book.properties" (
    echo ERROR: web2book.properties not found in the current directory
    echo Please ensure web2book.properties exists in: %SCRIPT_DIR%
    echo.
    pause
    exit /b 1
)

REM Check if config directory exists
if not exist "config" (
    echo WARNING: config directory not found
    echo The application may not work correctly without configuration files
    echo.
)

echo Starting Web2Book application...
echo.

REM Run the application with system property to disable font scanning (fixes PDFBox font issues)
java -Dorg.apache.pdfbox.forceSystemFontScan=false -jar web2book.jar

REM Capture exit code
set EXIT_CODE=%ERRORLEVEL%

echo.
echo ==========================================
if %EXIT_CODE% EQU 0 (
    echo Application completed successfully!
) else (
    echo Application exited with error code: %EXIT_CODE%
)
echo ==========================================
echo.

REM Keep terminal open so user can see the output
pause

