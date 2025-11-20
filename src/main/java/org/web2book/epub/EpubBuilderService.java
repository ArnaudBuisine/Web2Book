// Generated code - Model: Auto (Cursor AI)
// Date: 2025-01-27
package org.web2book.epub;

import nl.siegmann.epublib.domain.Author;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Metadata;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.epub.EpubWriter;
import org.web2book.model.ChapterContent;
import org.web2book.log.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Service for building EPUB files from chapter content.
 */
public class EpubBuilderService {
    private final Book book;
    private final String bookTitle;
    private final Logger logger;
    private final java.util.Properties bookProps;

    public EpubBuilderService(String bookTitle, Logger logger, java.util.Properties bookProps) {
        this.bookTitle = bookTitle;
        this.logger = logger;
        this.bookProps = bookProps;
        this.book = new Book();
        
        // Set metadata
        Metadata metadata = book.getMetadata();
        metadata.addTitle(bookTitle);
        
        // Add author if available
        if (bookProps != null) {
            String authorName = bookProps.getProperty("book.author", "");
            if (!authorName.isEmpty()) {
                Author author = new Author(authorName);
                metadata.addAuthor(author);
                logger.info("Added author to EPUB metadata: " + authorName);
            } else {
                logger.info("No author specified in book.author property");
            }
        }
    }

    /**
     * Adds a chapter to the EPUB.
     * 
     * @param chapterContent The chapter content including images and HTML
     * @param chapterTitle The title for this chapter
     * @param failedFilenames Set of image filenames that failed to download (for placeholders)
     */
    public void addChapter(ChapterContent chapterContent, String chapterTitle, java.util.Set<String> failedFilenames) {
        try {
            // Read chapter HTML file
            String chapterHtml = new String(Files.readAllBytes(chapterContent.getChapterHtmlFile()), java.nio.charset.StandardCharsets.UTF_8);
            
            // Note: Failed downloads already have placeholders in HTML from createChapterHtml
            // We only need to process successfully downloaded image files here
            
            int chapterNumber = chapterContent.getInfo().getChapterNumber();
            
            // Process existing image files and add placeholders for errors (read errors, unsupported types)
            for (Path imageFile : chapterContent.getImageFiles()) {
                String imageName = imageFile.getFileName().toString();
                try {
                    if (!Files.exists(imageFile)) {
                        // File doesn't exist - replace img tag with placeholder
                        String imgTag = "<img src=\"images/" + chapterNumber + "/" + imageName + "\"";
                        String placeholder = "<p style=\"text-align:center;color:#666;padding:20px;\">[Image can not be read: " + imageName + "]</p>";
                        chapterHtml = chapterHtml.replaceAll(
                            java.util.regex.Pattern.quote(imgTag) + "[^>]*>",
                            placeholder
                        );
                        continue;
                    }
                    
                    byte[] imageBytes = Files.readAllBytes(imageFile);
                    
                    // Use subdirectory structure: images/10/1.jpg to match XHTML references
                    // This ensures unique paths per chapter and matches createChapterHtml
                    String resourceHref = "images/" + chapterNumber + "/" + imageName;
                    Resource imageResource = new Resource(imageBytes, resourceHref);
                    
                    book.getResources().add(imageResource);
                } catch (IOException e) {
                    // Image can't be read - replace img tag with placeholder
                    logger.warning("Failed to add image " + imageFile + ": " + e.getMessage());
                    String imgTag = "<img src=\"images/" + chapterNumber + "/" + imageName + "\"";
                    String placeholder = "<p style=\"text-align:center;color:#666;padding:20px;\">[Image can not be read: " + imageName + "]</p>";
                    chapterHtml = chapterHtml.replaceAll(
                        java.util.regex.Pattern.quote(imgTag) + "[^>]*>",
                        placeholder
                    );
                } catch (Exception e) {
                    // Unsupported image type or other error
                    String errorMsg = e.getMessage();
                    String placeholderText;
                    if (errorMsg != null && errorMsg.contains("Image type") && errorMsg.contains("not supported")) {
                        logger.severe("Image type not supported: " + imageName + " - " + errorMsg);
                        placeholderText = "[Image type UNKNOWN not supported: " + imageName + "]";
                    } else {
                        logger.severe("Failed to process image " + imageName + ": " + errorMsg);
                        placeholderText = "[Image can not be read: " + imageName + "]";
                    }
                    String imgTag = "<img src=\"images/" + chapterNumber + "/" + imageName + "\"";
                    String placeholder = "<p style=\"text-align:center;color:#666;padding:20px;\">" + placeholderText + "</p>";
                    chapterHtml = chapterHtml.replaceAll(
                        java.util.regex.Pattern.quote(imgTag) + "[^>]*>",
                        placeholder
                    );
                }
            }
            
            // Create resource for chapter HTML with updated content
            byte[] chapterHtmlBytes = chapterHtml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String chapterHref = "chapter-" + chapterContent.getInfo().getChapterNumber() + ".xhtml";
            Resource chapterResource = new Resource(chapterHtmlBytes, chapterHref);
            // MediaType will be inferred from .xhtml extension
            
            // Add chapter resource to book resources first (required before adding to spine)
            book.getResources().add(chapterResource);
            
            // Add chapter to spine and TOC (resource must be in book.getResources() first)
            book.getSpine().addResource(chapterResource);
            book.getTableOfContents().addSection(chapterResource, chapterTitle);
            
        } catch (IOException e) {
            logger.severe("Failed to add chapter " + chapterTitle + ": " + e.getMessage());
        }
    }

    /**
     * Saves the EPUB to the output directory.
     * 
     * @param outputDir The directory where the EPUB should be saved
     * @param bookTitle The title to use for the filename (will be sanitized)
     * @return The path to the created EPUB file
     * @throws IOException If the EPUB cannot be written
     */
    public Path saveTo(Path outputDir, String bookTitle) throws IOException {
        // Ensure output directory exists
        Files.createDirectories(outputDir);
        
        // Sanitize book title for filename
        String sanitizedTitle = LoggerFactory.sanitizeFilename(bookTitle);
        String epubFileName = sanitizedTitle + ".epub";
        Path epubFile = outputDir.resolve(epubFileName);
        
        // Write EPUB
        EpubWriter epubWriter = new EpubWriter();
        try (FileOutputStream out = new FileOutputStream(epubFile.toFile())) {
            epubWriter.write(book, out);
        }
        
        logger.info("EPUB saved to: " + epubFile.toAbsolutePath());
        return epubFile;
    }

}

