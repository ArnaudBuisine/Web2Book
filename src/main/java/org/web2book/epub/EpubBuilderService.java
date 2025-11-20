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
     */
    public void addChapter(ChapterContent chapterContent, String chapterTitle) {
        try {
            // Read chapter HTML file
            byte[] chapterHtmlBytes = Files.readAllBytes(chapterContent.getChapterHtmlFile());
            
            // Create resource for chapter HTML
            String chapterHref = "chapter-" + chapterContent.getInfo().getChapterNumber() + ".xhtml";
            Resource chapterResource = new Resource(chapterHtmlBytes, chapterHref);
            // MediaType will be inferred from .xhtml extension
            
            // Add chapter resource to book resources first (required before adding to spine)
            book.getResources().add(chapterResource);
            
            // Add images as resources
            int chapterNumber = chapterContent.getInfo().getChapterNumber();
            for (Path imageFile : chapterContent.getImageFiles()) {
                try {
                    byte[] imageBytes = Files.readAllBytes(imageFile);
                    String imageName = imageFile.getFileName().toString();
                    
                    // Use subdirectory structure: images/10/1.jpg to match XHTML references
                    // This ensures unique paths per chapter and matches createChapterHtml
                    String resourceHref = "images/" + chapterNumber + "/" + imageName;
                    Resource imageResource = new Resource(imageBytes, resourceHref);
                    
                    book.getResources().add(imageResource);
                } catch (IOException e) {
                    logger.warning("Failed to add image " + imageFile + ": " + e.getMessage());
                }
            }
            
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

