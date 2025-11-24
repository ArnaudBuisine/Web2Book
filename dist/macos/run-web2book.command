#!/bin/bash
# ==========================================
# Web2Book Runner Script for macOS
# This script can be double-clicked from Finder to run the application
# ==========================================

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Change to the script directory
cd "$SCRIPT_DIR"

# Print header
echo "=========================================="
echo "Web2Book - Manga to EPUB/PDF Converter"
echo "=========================================="
echo ""
echo "Working directory: $SCRIPT_DIR"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    echo "Please install Java 17 or higher"
    echo ""
    echo "You can install Java from:"
    echo "  - Homebrew: brew install openjdk@17"
    echo "  - Oracle: https://www.oracle.com/java/technologies/downloads/"
    echo "  - Eclipse Adoptium: https://adoptium.net/"
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

# Check Java version (requires Java 17+)
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
if [ -z "$JAVA_VERSION" ] || [ "$JAVA_VERSION" -lt 17 ]; then
    echo "ERROR: Java 17 or higher is required. Found Java $JAVA_VERSION"
    echo ""
    echo "Please update your Java installation to version 17 or higher"
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

echo "Java version:"
java -version 2>&1 | head -n 1
echo ""

# Check if JAR exists
if [ ! -f "web2book.jar" ]; then
    echo "ERROR: web2book.jar not found in the current directory"
    echo "Please ensure web2book.jar exists in: $SCRIPT_DIR"
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

# Check if web2book.properties exists
if [ ! -f "web2book.properties" ]; then
    echo "ERROR: web2book.properties not found in the current directory"
    echo "Please ensure web2book.properties exists in: $SCRIPT_DIR"
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

# Check if config directory exists
if [ ! -d "config" ]; then
    echo "WARNING: config directory not found"
    echo "The application may not work correctly without configuration files"
    echo ""
fi

echo "Starting Web2Book application..."
echo ""

# Run the application with system properties to disable font scanning (fixes PDFBox font issues)
java -Dorg.apache.pdfbox.forceSystemFontScan=false \
     -Dpdfbox.fontcache=false \
     -Dorg.apache.pdfbox.disableFontCache=true \
     -Djava.awt.headless=true \
     -jar web2book.jar

# Capture exit code
EXIT_CODE=$?

echo ""
echo "=========================================="
if [ $EXIT_CODE -eq 0 ]; then
    echo "Application completed successfully!"
else
    echo "Application exited with error code: $EXIT_CODE"
fi
echo "=========================================="
echo ""

# Keep terminal open so user can see the output (only if run from Terminal)
if [ -t 0 ]; then
    read -p "Press Enter to exit..."
fi

