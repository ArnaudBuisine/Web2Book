// Generated code - Model: Auto (Cursor AI)
// Date: 2025-01-27
package org.web2book.util;

import java.util.Properties;

/**
 * Simple template engine for replacing placeholders in strings.
 */
public class TemplateEngine {

    /**
     * Applies the book title template by replacing placeholders.
     * 
     * @param template The template string with ${start}, ${end}, ${manga} placeholders
     * @param bookProps Properties containing chapter.start, chapter.end, manga.name
     * @return The template with placeholders replaced
     */
    public static String applyBookTitleTemplate(String template, Properties bookProps) {
        String result = template;
        
        // Replace escape sequences for newlines
        // Support both \n (literal) and actual newline characters
        result = result.replace("\\n", "\n");
        result = result.replace("\\r", "\r");
        result = result.replace("\\r\\n", "\r\n");
        
        String start = bookProps.getProperty("chapter.start", "");
        String end = bookProps.getProperty("chapter.end", "");
        String manga = bookProps.getProperty("manga.name", "");
        
        result = result.replace("${start}", start);
        result = result.replace("${end}", end);
        result = result.replace("${manga}", manga);
        
        return result;
    }

    /**
     * Applies the chapter title template by replacing placeholders.
     * 
     * @param template The template string with ${chapter} placeholder
     * @param chapterNumber The chapter number to insert
     * @param bookProps Properties (currently unused but kept for consistency)
     * @return The template with placeholders replaced
     */
    public static String applyChapterTitleTemplate(String template, int chapterNumber, Properties bookProps) {
        String result = template;
        result = result.replace("${chapter}", String.valueOf(chapterNumber));
        return result;
    }
}

