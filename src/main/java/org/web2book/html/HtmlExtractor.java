// Generated code - Model: Auto (Cursor AI)
// Date: 2025-01-27
package org.web2book.html;

import org.web2book.model.ChapterInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts chapter links and image URLs from HTML content using regex patterns.
 */
public class HtmlExtractor {

    /**
     * Extracts chapter links from the starting page HTML.
     * 
     * @param startingPageHtml The HTML content of the starting page
     * @param bookProps Properties containing chapter.pattern and chapter.base.url
     * @return List of ChapterInfo objects
     */
    public static List<ChapterInfo> extractChapterLinks(String startingPageHtml, Properties bookProps) {
        List<ChapterInfo> chapters = new ArrayList<>();
        
        String chapterPattern = bookProps.getProperty("chapter.pattern");
        String baseUrl = bookProps.getProperty("chapter.base.url");
        
        if (chapterPattern == null || baseUrl == null) {
            return chapters;
        }
        
        // Ensure base URL doesn't end with /
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        Pattern pattern = Pattern.compile(chapterPattern);
        Matcher matcher = pattern.matcher(startingPageHtml);
        
        while (matcher.find()) {
            try {
                String relativeUrl = matcher.group(1);
                int chapterNumber = Integer.parseInt(matcher.group(2));
                
                // Build full URL
                String fullUrl;
                if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                    fullUrl = relativeUrl;
                } else {
                    // Ensure relative URL starts with /
                    if (!relativeUrl.startsWith("/")) {
                        relativeUrl = "/" + relativeUrl;
                    }
                    fullUrl = baseUrl + relativeUrl;
                }
                
                chapters.add(new ChapterInfo(chapterNumber, relativeUrl, fullUrl));
            } catch (Exception e) {
                // Skip invalid matches
                continue;
            }
        }
        
        return chapters;
    }

    /**
     * Extracts image URLs from a chapter HTML page.
     * 
     * @param chapterHtml The HTML content of the chapter page
     * @param bookProps Properties containing image.pattern
     * @return List of image URLs in the order they appear in the HTML
     */
    public static List<String> extractImageUrls(String chapterHtml, Properties bookProps) {
        List<String> imageUrls = new ArrayList<>();
        
        String imagePattern = bookProps.getProperty("image.pattern");
        if (imagePattern == null) {
            return imageUrls;
        }
        
        try {
            Pattern pattern = Pattern.compile(imagePattern);
            Matcher matcher = pattern.matcher(chapterHtml);
        
            while (matcher.find()) {
                try {
                    String imageUrl = matcher.group(1);
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        imageUrls.add(imageUrl);
                    }
                } catch (Exception e) {
                    // Skip invalid matches
                    continue;
                }
            }
        } catch (Exception e) {
            // Pattern compilation failed - log and return empty list
            System.err.println("ERROR: Failed to compile image pattern: " + imagePattern);
            System.err.println("Error: " + e.getMessage());
            return imageUrls;
        }
        
        return imageUrls;
    }
}

