==========================================
Web2Book - macOS Distribution
==========================================

This package contains everything needed to run Web2Book on macOS.

QUICK START:
------------
1. Extract this ZIP file to a folder of your choice
   (e.g., ~/Web2Book/ or your Desktop)

2. Double-click run_web2book.sh to start the application
   (You may need to right-click and select "Open" the first time)
   
   OR
   
   Open Terminal, navigate to the extracted folder, and run:
   ./run_web2book.sh

3. The application will process all books configured in web2book.properties
   Check the output/ folder for generated EPUB/PDF files when complete

REQUIREMENTS:
-------------
- Java 17 or higher must be installed on your system
  (Note: The application is compiled with Java 21, but Java 17+ should work)
- Java must be accessible from the command line (in your system PATH)
  
  To check if Java is installed:
  Open Terminal and type: java -version
  
  If Java is not installed, you can install it using:
  - Homebrew: brew install openjdk@17
  - Oracle: https://www.oracle.com/java/technologies/downloads/
  - Eclipse Adoptium (recommended): https://adoptium.net/
  
  After installing Java, you may need to restart Terminal for the PATH changes to take effect.

CONFIGURATION:
--------------
For detailed configuration documentation, see README_CONFIG.txt in this folder.
This file contains complete information about:
- Global configuration settings
- Book-specific configuration options
- Template configuration
- Folder structure and output locations
- Pattern generation examples (Appendix)

TROUBLESHOOTING:
----------------
If you encounter errors:

1. "Java is not installed or not in PATH"
   → Install Java 17 or higher using Homebrew: brew install openjdk@17
   → Or download from Oracle or Eclipse Adoptium
   → Ensure Java is added to your system PATH

2. "Java 17 or higher is required"
   → Update your Java installation to version 17 or higher
   → Check your Java version: java -version

3. "web2book.jar not found"
   → Ensure you haven't moved or deleted web2book.jar
   → Re-extract the package if needed

4. "web2book.properties not found"
   → Ensure web2book.properties is in the same folder as run_web2book.sh
   → Re-extract the package if needed

5. "Permission denied" when running run_web2book.sh:
   → Make the script executable: chmod +x run_web2book.sh
   → Or run it with: bash run_web2book.sh

6. Application errors:
   → Check the logs/ folder for detailed error messages
   → Verify your configuration files are correct
   → Ensure all paths in config files use forward slashes (/)
   → Check that URLs in config files are accessible

7. "config directory not found" warning:
   → This is normal if you haven't added any book configs yet
   → Create .properties files in the config/ folder and reference them
     in web2book.properties

8. macOS Security Warning when double-clicking:
   → Right-click the script and select "Open" (first time only)
   → Or run from Terminal: ./run_web2book.sh

SUPPORT:
--------
For more detailed information, troubleshooting, and examples,
please refer to the main README.md file in the source distribution.

==========================================
Enjoy converting your manga to books!
==========================================
