// Generated code - Model: Auto (Cursor AI)
// Date: 2025-01-27
package org.web2book;

import org.web2book.core.BookJob;
import org.web2book.log.LoggerFactory;
import org.web2book.net.HttpClientService;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Main application entry point.
 */
public class Web2BookApp {
    private static final String GLOBAL_CONFIG_FILE = "web2book.properties";

    public static void main(String[] args) {
        // Set PDFBox system properties BEFORE any PDFBox classes are loaded
        // This prevents font scanning issues on macOS
        System.setProperty("org.apache.pdfbox.forceSystemFontScan", "false");
        System.setProperty("pdfbox.fontcache", "false");
        // Additional properties to prevent font provider initialization
        System.setProperty("org.apache.pdfbox.fontProvider", "org.apache.pdfbox.pdmodel.font.FontProvider");
        System.setProperty("java.awt.headless", "true");
        // Try to disable font scanning completely
        System.setProperty("org.apache.pdfbox.disableFontCache", "true");
        
        // Suppress PDFBox and FontBox font scanning warnings by setting log level
        // This reduces noise in the output when font scanning encounters issues
        // Set to OFF to completely suppress all messages, including SEVERE
        
        // Suppress all PDFBox logging
        java.util.logging.Logger pdfboxLogger = java.util.logging.Logger.getLogger("org.apache.pdfbox");
        pdfboxLogger.setLevel(java.util.logging.Level.OFF);
        pdfboxLogger.setUseParentHandlers(false);
        
        // Suppress all FontBox logging
        java.util.logging.Logger fontboxLogger = java.util.logging.Logger.getLogger("org.apache.fontbox");
        fontboxLogger.setLevel(java.util.logging.Level.OFF);
        fontboxLogger.setUseParentHandlers(false);
        
        // Specifically suppress GlyphSubstitutionTable warnings
        java.util.logging.Logger glyphLogger = java.util.logging.Logger.getLogger("org.apache.fontbox.ttf.GlyphSubstitutionTable");
        glyphLogger.setLevel(java.util.logging.Level.OFF);
        glyphLogger.setUseParentHandlers(false);
        
        // Suppress all TTF-related warnings
        java.util.logging.Logger ttfLogger = java.util.logging.Logger.getLogger("org.apache.fontbox.ttf");
        ttfLogger.setLevel(java.util.logging.Level.OFF);
        ttfLogger.setUseParentHandlers(false);
        
        // Suppress font provider warnings
        java.util.logging.Logger fontProviderLogger = java.util.logging.Logger.getLogger("org.apache.pdfbox.pdmodel.font");
        fontProviderLogger.setLevel(java.util.logging.Level.OFF);
        fontProviderLogger.setUseParentHandlers(false);
        
        System.out.println("Web2Book - Manga to Book Converter");
        System.out.println("===================================");
        
        // Load global configuration
        Properties globalProps = loadGlobalConfig();
        if (globalProps == null) {
            System.err.println("ERROR: Failed to load " + GLOBAL_CONFIG_FILE);
            System.err.println("Please ensure " + GLOBAL_CONFIG_FILE + " exists in the current working directory.");
            System.exit(1);
        }
        
        // Collect book configuration files
        List<Path> bookConfigPaths = collectBookConfigPaths(globalProps);
        
        if (bookConfigPaths.isEmpty()) {
            System.err.println("ERROR: No book configuration files found in " + GLOBAL_CONFIG_FILE);
            System.err.println("Please add at least one book.config.N property.");
            System.exit(1);
        }
        
        System.out.println("Found " + bookConfigPaths.size() + " book configuration(s)");
        
        // Create shared HTTP client service
        Logger consoleLogger = Logger.getLogger("web2book.console");
        HttpClientService httpClientService = new HttpClientService(consoleLogger);
        
        // Process each book sequentially
        int successCount = 0;
        int failureCount = 0;
        
        for (Path bookConfigPath : bookConfigPaths) {
            System.out.println("\nProcessing book: " + bookConfigPath);
            
            if (!bookConfigPath.toFile().exists()) {
                System.err.println("ERROR: Book config file not found: " + bookConfigPath.toAbsolutePath());
                failureCount++;
                continue;
            }
            
            try {
                BookJob bookJob = new BookJob(bookConfigPath, globalProps, httpClientService);
                bookJob.run();
                successCount++;
                System.out.println("Completed: " + bookConfigPath);
            } catch (Exception e) {
                System.err.println("ERROR: Failed to process " + bookConfigPath + ": " + e.getMessage());
                e.printStackTrace();
                failureCount++;
            }
        }
        
        // Print summary
        System.out.println("\n===================================");
        System.out.println("Processing Summary:");
        System.out.println("  Success: " + successCount);
        System.out.println("  Failed:  " + failureCount);
        System.out.println("===================================");
    }

    private static Properties loadGlobalConfig() {
        Path configFile = Paths.get(GLOBAL_CONFIG_FILE);
        
        if (!configFile.toFile().exists()) {
            return null;
        }
        
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile.toFile())) {
            props.load(fis);
            return props;
        } catch (IOException e) {
            System.err.println("Error reading " + GLOBAL_CONFIG_FILE + ": " + e.getMessage());
            return null;
        }
    }

    private static List<Path> collectBookConfigPaths(Properties globalProps) {
        List<Path> paths = new ArrayList<>();
        
        // Look for book.config.1, book.config.2, etc.
        int index = 1;
        while (true) {
            String key = "book.config." + index;
            String value = globalProps.getProperty(key);
            
            if (value == null || value.trim().isEmpty()) {
                break;
            }
            
            Path configPath = Paths.get(value.trim());
            paths.add(configPath);
            index++;
        }
        
        return paths;
    }
}

