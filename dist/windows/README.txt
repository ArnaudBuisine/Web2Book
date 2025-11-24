==========================================
Web2Book - Windows Distribution
==========================================

This package contains everything needed to run Web2Book on Windows.

QUICK START:
------------
1. Extract this ZIP file to a folder of your choice
   (e.g., C:\Web2Book\ or your Desktop)

2. Double-click run_web2book.bat to start the application
   
   OR
   
   Open Command Prompt, navigate to the extracted folder, and run:
   run_web2book.bat

3. The application will process all books configured in web2book.properties
   Check the output/ folder for generated EPUB/PDF files when complete

REQUIREMENTS:
-------------
- Java 17 or higher must be installed on your system
  (Note: The application is compiled with Java 21, but Java 17+ should work)
- Java must be accessible from the command line (in your system PATH)
  
  To check if Java is installed:
  Open Command Prompt and type: java -version
  
  If Java is not installed, download it from:
  - Oracle: https://www.oracle.com/java/technologies/downloads/
  - Eclipse Adoptium (recommended): https://adoptium.net/
  
  After installing Java, you may need to restart your computer or
  Command Prompt for the PATH changes to take effect.

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
   → Install Java 17 or higher
   → Ensure Java is added to your system PATH

2. "Java 17 or higher is required"
   → Update your Java installation to version 17 or higher

3. "web2book.jar not found"
   → Ensure you haven't moved or deleted web2book.jar
   → Re-extract the package if needed

4. "web2book.properties not found"
   → Ensure web2book.properties is in the same folder as run_web2book.bat
   → Re-extract the package if needed

5. Application errors:
   → Check the logs/ folder for detailed error messages
   → Verify your configuration files are correct
   → Ensure all paths in config files use forward slashes (/)
   → Check that URLs in config files are accessible

6. "config directory not found" warning:
   → This is normal if you haven't added any book configs yet
   → Create .properties files in the config/ folder and reference them
     in web2book.properties

SUPPORT:
--------
For more detailed information, troubleshooting, and examples,
please refer to the main README.md file in the source distribution.

==========================================
Enjoy converting your manga to books!
==========================================
