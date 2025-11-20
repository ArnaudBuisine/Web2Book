// Generated code - Model: Auto (Cursor AI)
// Date: 2025-01-27
package org.web2book.log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Factory for creating file-based loggers.
 */
public class LoggerFactory {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Creates a logger that writes to a file in the output directory.
     * 
     * @param loggerName The name for the logger
     * @param outputDir The output directory where the log file should be created
     * @param bookTitle The book title (will be sanitized for filename)
     * @return A Logger instance configured to write to a file
     */
    public static Logger createFileLogger(String loggerName, Path outputDir, String bookTitle) {
        try {
            // Ensure output directory exists
            Files.createDirectories(outputDir);
            
            // Sanitize book title for filename
            String sanitizedTitle = sanitizeFilename(bookTitle);
            
            // Use sanitized title as log filename (will overwrite previous logs)
            String logFileName = sanitizedTitle + ".log";
            Path logFile = outputDir.resolve(logFileName);
            
            // Create logger
            Logger logger = Logger.getLogger(loggerName);
            logger.setLevel(Level.FINEST); // Set to FINEST (equivalent to TRACE) for maximum detail
            logger.setUseParentHandlers(false); // Disable console output
            
            // Create file handler
            FileHandler fileHandler = new FileHandler(logFile.toString(), true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.FINEST); // Set to FINEST for maximum detail
            
            logger.addHandler(fileHandler);
            
            return logger;
        } catch (IOException e) {
            // Fallback to console logger if file creation fails
            Logger logger = Logger.getLogger(loggerName);
            logger.log(Level.SEVERE, "Failed to create file logger: " + e.getMessage(), e);
            return logger;
        }
    }

    /**
     * Sanitizes a string to be safe for use as a filename.
     * Replaces invalid characters with underscores and trims whitespace.
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unknown";
        }
        
        // Replace invalid filename characters with underscores
        String sanitized = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        // Preserve dashes and their surrounding spaces (e.g., " - " should remain " - ")
        // First, protect dashes with surrounding spaces by temporarily replacing them
        sanitized = sanitized.replaceAll("\\s+-\\s+", "___DASH___");
        
        // Trim and collapse other whitespace sequences
        sanitized = sanitized.trim().replaceAll("\\s+", " ");
        
        // Restore dashes with their surrounding spaces
        sanitized = sanitized.replace("___DASH___", " - ");
        
        // Remove leading/trailing dots and spaces (Windows issue)
        sanitized = sanitized.replaceAll("^[\\.\\s]+|[\\.\\s]+$", "");
        
        if (sanitized.isEmpty()) {
            return "unknown";
        }
        
        return sanitized;
    }

    /**
     * Simple formatter that outputs: [yyyy-MM-dd HH:mm:ss] LEVEL message
     */
    private static class SimpleFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            String timestamp = LocalDateTime.now().format(DATE_FORMAT);
            String level = record.getLevel().getName();
            String message = formatMessage(record);
            
            if (record.getThrown() != null) {
                return String.format("[%s] %s %s%n%s%n", 
                    timestamp, level, message, 
                    getStackTrace(record.getThrown()));
            }
            
            return String.format("[%s] %s %s%n", timestamp, level, message);
        }
        
        private String getStackTrace(Throwable throwable) {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            throwable.printStackTrace(pw);
            return sw.toString();
        }
    }
}

