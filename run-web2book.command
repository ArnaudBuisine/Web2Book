#!/bin/bash

# Web2Book Runner Script for macOS
# This script can be double-clicked from Finder to run the application

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Change to the project directory
cd "$SCRIPT_DIR" || exit 1

# Print header
echo "=========================================="
echo "Web2Book - Manga to EPUB/PDF Converter"
echo "=========================================="
echo ""
echo "Project directory: $SCRIPT_DIR"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    echo "Please install Java 17 or higher"
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

# Check Java version (requires Java 17+)
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "ERROR: Java 17 or higher is required. Found Java $JAVA_VERSION"
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

echo "Java version: $(java -version 2>&1 | head -n 1)"
echo ""

# Check if Maven is installed (needed for building)
if ! command -v mvn &> /dev/null; then
    echo "WARNING: Maven is not installed or not in PATH"
    echo "The script will try to use the existing JAR file if available"
    echo ""
fi

# Check if JAR exists, if not build it
JAR_FILE="target/web2book-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "JAR file not found. Building project..."
    echo ""
    
    if command -v mvn &> /dev/null; then
        mvn clean package
        if [ $? -ne 0 ]; then
            echo ""
            echo "ERROR: Build failed. Please check the errors above."
            echo ""
            read -p "Press Enter to exit..."
            exit 1
        fi
        echo ""
    else
        echo "ERROR: JAR file not found and Maven is not available to build it."
        echo "Please run 'mvn clean package' manually or install Maven."
        echo ""
        read -p "Press Enter to exit..."
        exit 1
    fi
fi

# Check if web2book.properties exists
if [ ! -f "web2book.properties" ]; then
    echo "ERROR: web2book.properties not found in project directory"
    echo "Please create web2book.properties before running the application."
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

echo "Starting Web2Book application..."
echo ""

# Run the application with system property to disable font scanning (fixes PDFBox font issues on macOS)
java -Dorg.apache.pdfbox.forceSystemFontScan=false -jar "$JAR_FILE"

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

# Keep terminal open so user can see the output
read -p "Press Enter to close this window..."

