#!/bin/bash
# ==========================================
# macOS Package Build Script
# ==========================================
# Generated code - Model: Auto (Cursor AI)
# Date: 2025-01-27
#
# This script builds the macOS distribution package
# ==========================================

set -e  # Exit on error

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# Change to project root (two levels up from dist/macos/)
PROJECT_ROOT="$( cd "$SCRIPT_DIR/../.." && pwd )"
cd "$PROJECT_ROOT"

echo "=========================================="
echo "Building macOS Package for Web2Book"
echo "=========================================="
echo ""

# Step 1: Build the fat JAR with Maven
echo "Step 1: Building JAR with Maven..."
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed or not in PATH"
    exit 1
fi

mvn clean package
if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Maven build failed"
    exit 1
fi

# Verify the JAR exists
JAR_FILE="target/web2book.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "ERROR: JAR file not found at $JAR_FILE"
    exit 1
fi

echo "✓ JAR built successfully: $JAR_FILE"
echo ""

# Step 2: Clean and create distribution directory
echo "Step 2: Preparing distribution directory..."
DIST_DIR="dist/macos/web2book"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"
mkdir -p "$DIST_DIR/config"
mkdir -p "$DIST_DIR/logs"
mkdir -p "$DIST_DIR/tmp"
mkdir -p "$DIST_DIR/output"

echo "✓ Directory structure created"
echo ""

# Step 3: Copy JAR file
echo "Step 3: Copying JAR file..."
cp "$JAR_FILE" "$DIST_DIR/web2book.jar"
echo "✓ JAR copied"
echo ""

# Step 4: Copy shell launcher
echo "Step 4: Copying shell launcher..."
# Use .command file for macOS (executable from Finder)
COMMAND_FILE="$SCRIPT_DIR/run-web2book.command"
if [ ! -f "$COMMAND_FILE" ]; then
    echo "ERROR: run-web2book.command not found in $SCRIPT_DIR"
    exit 1
fi
cp "$COMMAND_FILE" "$DIST_DIR/run-web2book.command"
chmod +x "$DIST_DIR/run-web2book.command"
echo "✓ Shell launcher copied and made executable"
echo ""

# Step 5: Copy and update web2book.properties
echo "Step 5: Copying and updating configuration..."
if [ ! -f "web2book.properties" ]; then
    echo "ERROR: web2book.properties not found"
    exit 1
fi

# Copy properties file
cp "web2book.properties" "$DIST_DIR/"

# Update paths for macOS distribution structure:
# - configs/ or book_configs/ -> config/
# - output/logs -> logs (to match root-level logs/ folder)
# - output/tmp -> tmp (to match root-level tmp/ folder)
sed -i '' 's|configs/|config/|g' "$DIST_DIR/web2book.properties"
sed -i '' 's|book_configs/|config/|g' "$DIST_DIR/web2book.properties"
sed -i '' 's|^default\.log\.dir=output/logs|default.log.dir=logs|g' "$DIST_DIR/web2book.properties"
sed -i '' 's|^default\.temp\.dir=output/tmp|default.temp.dir=tmp|g' "$DIST_DIR/web2book.properties"

echo "✓ Configuration file copied and updated"
echo ""

# Step 6: Copy and sanitize book config files
echo "Step 6: Copying and sanitizing book configuration files..."

# Find config files from either config/ or book_configs/ directory
CONFIG_SOURCE=""
if [ -d "config" ] && ls config/*.properties 1> /dev/null 2>&1; then
    CONFIG_SOURCE="config"
elif [ -d "book_configs" ] && ls book_configs/*.properties 1> /dev/null 2>&1; then
    CONFIG_SOURCE="book_configs"
fi

if [ -z "$CONFIG_SOURCE" ]; then
    echo "WARNING: No config directory found (checked config/ and book_configs/)"
else
    # Process each config file
    for config_file in "$CONFIG_SOURCE"/*.properties; do
        if [ -f "$config_file" ]; then
            filename=$(basename "$config_file")
            dest_file="$DIST_DIR/config/$filename"
            
            # Copy the file
            cp "$config_file" "$dest_file"
            
            # Convert absolute paths to relative paths for macOS compatibility
            # Only convert paths that start with / (absolute paths)
            # Convert absolute output.dir paths (starting with /) to relative
            sed -i '' -E 's|^output\.dir=/[^=]*/([^/]+)$|output.dir=output/\1|g' "$dest_file"
            sed -i '' -E 's|^output\.dir=/[^=]*$|output.dir=output|g' "$dest_file"
            # Convert absolute temp.dir paths to relative (preserve subdirectory name if present)
            sed -i '' -E 's|^temp\.dir=/[^=]*/([^/]+)$|temp.dir=tmp/\1|g' "$dest_file"
            sed -i '' -E 's|^temp\.dir=/[^=]*$|temp.dir=tmp|g' "$dest_file"
            # Convert absolute log.dir paths to relative
            sed -i '' -E 's|^log\.dir=/[^=]*/([^/]+)/logs$|log.dir=logs/\1|g' "$dest_file"
            sed -i '' -E 's|^log\.dir=/[^=]*/logs$|log.dir=logs|g' "$dest_file"
            sed -i '' -E 's|^log\.dir=/[^=]*/([^/]+)$|log.dir=logs/\1|g' "$dest_file"
            sed -i '' -E 's|^log\.dir=/[^=]*$|log.dir=logs|g' "$dest_file"
            # Update comments to reflect correct defaults
            sed -i '' 's|default: output/logs|default: logs|g' "$dest_file"
            
            echo "  ✓ Processed: $filename"
        fi
    done
    echo "✓ Book configuration files copied and sanitized"
fi
echo ""

# Step 7: Copy README files
echo "Step 7: Copying README files..."
if [ ! -f "dist/macos/README.txt" ]; then
    echo "WARNING: README.txt not found, creating default..."
    # Create a basic README if it doesn't exist
    cat > "$DIST_DIR/README.txt" << 'EOF'
Web2Book - macOS Distribution
================================

This package contains everything needed to run Web2Book on macOS.

QUICK START:
------------
1. Double-click run-web2book.command to start the application
   OR
   Open Terminal, navigate to this folder, and run:
   ./run-web2book.command

REQUIREMENTS:
-------------
- Java 17 or higher must be installed
- Java must be accessible from the command line

CONFIGURATION:
--------------
- Edit web2book.properties to configure global settings
- Edit files in the config/ folder to configure individual books

OUTPUT:
-------
- Generated books will be saved in the output/ folder
- Log files will be saved in the logs/ folder
- Temporary files are stored in tmp/ folder

For more information, see the main README.md file.
EOF
    echo "✓ Default README created"
else
    cp "dist/macos/README.txt" "$DIST_DIR/"
    echo "✓ README.txt copied"
fi

# Copy README_CONFIG.txt (common configuration documentation)
if [ -f "dist/README_CONFIG.txt" ]; then
    cp "dist/README_CONFIG.txt" "$DIST_DIR/"
    echo "✓ README_CONFIG.txt copied"
else
    echo "WARNING: README_CONFIG.txt not found in dist/"
fi
echo ""

# Step 8: Create ZIP archive
echo "Step 8: Creating ZIP archive..."
ZIP_FILE="dist/macos/web2book.zip"
cd dist/macos
rm -f web2book.zip
zip -r web2book.zip web2book/ > /dev/null
cd "$SCRIPT_DIR"

if [ -f "$ZIP_FILE" ]; then
    ZIP_SIZE=$(du -h "$ZIP_FILE" | cut -f1)
    echo "✓ ZIP archive created: $ZIP_FILE ($ZIP_SIZE)"
else
    echo "ERROR: Failed to create ZIP archive"
    exit 1
fi
echo ""

# Step 9: Verification
echo "Step 9: Verifying package contents..."
echo ""
echo "Package structure:"
echo "  $DIST_DIR/"
echo "    ├── web2book.jar"
echo "    ├── run-web2book.command"
echo "    ├── web2book.properties"
echo "    ├── config/"
if [ -d "$DIST_DIR/config" ] && [ "$(ls -A $DIST_DIR/config/*.properties 2>/dev/null)" ]; then
    echo "    │   └── *.properties"
else
    echo "    │   └── (no config files)"
fi
echo "    ├── logs/ (empty)"
echo "    ├── tmp/ (empty)"
echo "    ├── output/ (empty)"
echo "    ├── README.txt"
echo "    └── README_CONFIG.txt"
echo ""

# Final summary
echo "=========================================="
echo "macOS Package Build Complete!"
echo "=========================================="
echo ""
echo "Package location: $DIST_DIR"
echo "ZIP archive: $ZIP_FILE"
echo ""
echo "The package is ready for distribution."
echo ""

