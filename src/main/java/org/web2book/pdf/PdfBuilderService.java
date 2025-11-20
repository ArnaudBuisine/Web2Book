// Generated code - Model: Auto (Cursor AI)
// Date: 2025-11-19
package org.web2book.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.web2book.model.ChapterContent;
import org.web2book.log.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Service for building PDF files from chapter content.
 */
public class PdfBuilderService {
    private final PDDocument document;
    private final String bookTitle;
    private final Logger logger;
    private final java.util.Properties bookProps;
    private static final float MARGIN = 0f; // No margins - images use full width
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth(); // A4 width: 595.2756 points
    private static final float CONTENT_WIDTH = PAGE_WIDTH; // Full width, no margins
    
    // Cache the font to avoid repeated initialization and font scanning issues
    private static PDType1Font titleFont;
    private static PDType1Font regularFont;
    private static boolean fontInitializationFailed = false;
    private static final Object FONT_LOCK = new Object();
    
    // Static initializer to set system properties before any font operations
    static {
        try {
            // Ensure system properties are set before any font operations
            if (System.getProperty("org.apache.pdfbox.forceSystemFontScan") == null) {
                System.setProperty("org.apache.pdfbox.forceSystemFontScan", "false");
            }
            if (System.getProperty("pdfbox.fontcache") == null) {
                System.setProperty("pdfbox.fontcache", "false");
            }
            if (System.getProperty("org.apache.pdfbox.disableFontCache") == null) {
                System.setProperty("org.apache.pdfbox.disableFontCache", "true");
            }
        } catch (Exception e) {
            // Ignore - properties may already be set
        }
    }
    
    // Track chapters for TOC
    private final java.util.List<ChapterInfo> chapters = new java.util.ArrayList<>();
    private static class ChapterInfo {
        final String title;
        final int pageNumber;
        ChapterInfo(String title, int pageNumber) {
            this.title = title;
            this.pageNumber = pageNumber;
        }
    }
    
    // Title page flag
    private boolean titlePageAdded = false;
    
    // TOC content alignment - stored when title page is created
    private float tocContentStartX = 50f; // Default to margin if not set

    public PdfBuilderService(String bookTitle, Logger logger, java.util.Properties bookProps) {
        this.bookTitle = bookTitle;
        this.logger = logger;
        this.bookProps = bookProps;
        this.document = new PDDocument();
        
        // Try to initialize fonts early to catch any issues
        // This helps identify font problems before we start adding content
        try {
            PDType1Font testFont = getTitleFont();
            if (testFont == null) {
                logger.warning("Title font initialization failed - text may not be visible in PDF");
            }
            PDType1Font testRegular = getRegularFont();
            if (testRegular == null) {
                logger.warning("Regular font initialization failed - text may not be visible in PDF");
            }
        } catch (Exception e) {
            logger.severe("Error during font initialization check: " + e.getMessage());
        }
    }
    
    /**
     * Gets or creates the title font, handling initialization errors gracefully.
     * Returns null if font cannot be initialized (e.g., due to font provider issues).
     * Uses lazy initialization to avoid font scanning issues during static initialization.
     * PDFBox 2.0.x uses static fields for Standard14 fonts.
     */
    private PDType1Font getTitleFont() {
        // If we've already failed to initialize fonts, don't try again
        if (fontInitializationFailed) {
            return null;
        }
        
        if (titleFont == null) {
            synchronized (FONT_LOCK) {
                if (titleFont == null && !fontInitializationFailed) {
                    try {
                        // PDFBox 2.0.x uses static fields: PDType1Font.HELVETICA_BOLD
                        titleFont = PDType1Font.HELVETICA_BOLD;
                        logger.info("Successfully initialized title font");
                    } catch (NoClassDefFoundError e) {
                        // Font provider class failed to initialize
                        fontInitializationFailed = true;
                        String errorMsg = "Font provider unavailable (NoClassDefFoundError): " + e.getMessage();
                        logger.severe(errorMsg);
                        System.err.println("ERROR: " + errorMsg);
                        System.err.println("PDF text will not be visible. This is a known issue with PDFBox font initialization on some systems.");
                        return null;
                    } catch (ExceptionInInitializerError e) {
                        // Font provider initialization failed
                        fontInitializationFailed = true;
                        String errorMsg = "Font provider initialization failed (ExceptionInInitializerError): " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                        logger.severe(errorMsg);
                        System.err.println("ERROR: " + errorMsg);
                        System.err.println("PDF text will not be visible. This is a known issue with PDFBox font initialization on some systems.");
                        return null;
                    } catch (LinkageError e) {
                        // Other linkage errors (includes NoClassDefFoundError and ExceptionInInitializerError, but catch explicitly above)
                        fontInitializationFailed = true;
                        String errorMsg = "Font provider unavailable (LinkageError): " + e.getMessage();
                        logger.severe(errorMsg);
                        System.err.println("ERROR: " + errorMsg);
                        System.err.println("PDF text will not be visible. This is a known issue with PDFBox font initialization on some systems.");
                        return null;
                    } catch (Exception e) {
                        // Any other exception - mark as failed and return null
                        fontInitializationFailed = true;
                        String errorMsg = "Font initialization failed: " + e.getMessage();
                        logger.severe(errorMsg);
                        System.err.println("ERROR: " + errorMsg);
                        return null;
                    }
                }
            }
        }
        return titleFont;
    }
    
    /**
     * Gets or creates the regular font, handling initialization errors gracefully.
     * Returns null if font cannot be initialized.
     * PDFBox 2.0.x uses static fields for Standard14 fonts.
     */
    private PDType1Font getRegularFont() {
        if (fontInitializationFailed) {
            return null;
        }
        
        if (regularFont == null) {
            synchronized (FONT_LOCK) {
                if (regularFont == null && !fontInitializationFailed) {
                    try {
                        // PDFBox 2.0.x uses static fields: PDType1Font.HELVETICA
                        regularFont = PDType1Font.HELVETICA;
                        logger.info("Successfully initialized regular font");
                    } catch (NoClassDefFoundError e) {
                        fontInitializationFailed = true;
                        String errorMsg = "Font provider unavailable (NoClassDefFoundError): " + e.getMessage();
                        logger.severe(errorMsg);
                        System.err.println("ERROR: " + errorMsg);
                        return null;
                    } catch (ExceptionInInitializerError e) {
                        fontInitializationFailed = true;
                        String errorMsg = "Font provider initialization failed (ExceptionInInitializerError): " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                        logger.severe(errorMsg);
                        System.err.println("ERROR: " + errorMsg);
                        return null;
                    } catch (LinkageError e) {
                        fontInitializationFailed = true;
                        String errorMsg = "Font provider unavailable (LinkageError): " + e.getMessage();
                        logger.severe(errorMsg);
                        System.err.println("ERROR: " + errorMsg);
                        return null;
                    } catch (Exception e) {
                        fontInitializationFailed = true;
                        String errorMsg = "Font initialization failed: " + e.getMessage();
                        logger.severe(errorMsg);
                        System.err.println("ERROR: " + errorMsg);
                        return null;
                    }
                }
            }
        }
        return regularFont;
    }

    /**
     * Adds a chapter to the PDF.
     * All images from the chapter are placed on a single long page (A4 width, variable height).
     * Images are stacked vertically, each using full width with no margins.
     * Chapter title is added at the top of the page.
     * 
     * @param chapterContent The chapter content including images and HTML
     * @param chapterTitle The title for this chapter
     */
    public void addChapter(ChapterContent chapterContent, String chapterTitle) {
        try {
            List<Path> imageFiles = chapterContent.getImageFiles();
            if (imageFiles.isEmpty()) {
                logger.warning("No images found for chapter " + chapterTitle);
                return;
            }
            
            // Get current page number (before adding new page)
            // This will be the page number after title page (if added)
            int pageNumber = document.getNumberOfPages() + 1;
            
            // Add all images to a single long page with chapter title
            addChapterImagesPage(imageFiles, chapterTitle);
            
            // Track chapter for TOC (page number is now correct after page was added)
            chapters.add(new ChapterInfo(chapterTitle, pageNumber));
            
        } catch (Exception e) {
            logger.severe("Failed to add chapter " + chapterTitle + ": " + e.getMessage());
        }
    }
    
    /**
     * Adds the title page with book title and table of contents.
     * This should be called before adding chapters.
     * TOC entries will have clickable links once chapters are added.
     * 
     * @param chapterTitles List of all chapter titles in order
     */
    public void addTitlePage(java.util.List<String> chapterTitles) {
        if (titlePageAdded) {
            return; // Already added
        }
        
        try {
            PDPage titlePage = new PDPage(PDRectangle.A4);
            document.addPage(titlePage);
            titlePageAdded = true;
            
            float pageHeight = PDRectangle.A4.getHeight();
            float pageWidth = PDRectangle.A4.getWidth();
            float margin = 50f;
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, titlePage)) {
                PDType1Font titleFont = getTitleFont();
                PDType1Font regularFont = getRegularFont();
                
                // If fonts are not available, log error but continue (don't try to reinitialize as it will fail)
                if (titleFont == null) {
                    String errorMsg = "Title font not available - title page text may not be visible. Check log for font initialization errors.";
                    logger.severe(errorMsg);
                    System.err.println("WARNING: " + errorMsg);
                }
                if (regularFont == null) {
                    String errorMsg = "Regular font not available - TOC text may not be visible. Check log for font initialization errors.";
                    logger.severe(errorMsg);
                    System.err.println("WARNING: " + errorMsg);
                }
                
                float y = pageHeight - margin;
                
                // Book title - use the processed title (from book.title.template) and split into multiple lines
                if (titleFont != null) {
                    try {
                        // bookTitle is already processed from book.title.template with placeholders replaced
                        // Split title by newlines to get multiple lines
                        String[] titleLines = bookTitle.split("\\r?\\n");
                        
                        // Set text color to black
                        contentStream.setNonStrokingColor(0, 0, 0);
                        
                        // Draw each line of the title
                        // First line uses larger font (36pt), subsequent lines use smaller font (28pt)
                        for (int i = 0; i < titleLines.length; i++) {
                            String line = titleLines[i].trim();
                            if (line.isEmpty()) {
                                continue; // Skip empty lines
                            }
                            
                            float fontSize = (i == 0) ? 36f : 28f; // First line larger, rest smaller
                            contentStream.beginText();
                            contentStream.setFont(titleFont, fontSize);
                            float lineWidth = titleFont.getStringWidth(line) / 1000f * fontSize;
                            float lineX = (pageWidth - lineWidth) / 2f;
                            contentStream.newLineAtOffset(lineX, y);
                            contentStream.showText(line);
                            contentStream.endText();
                            
                            // Adjust Y position for next line
                            if (i < titleLines.length - 1) {
                                y -= (i == 0) ? 50f : 40f; // More space after first line
                            }
                        }
                        
                        y -= 60f; // Space after title
                    } catch (Exception e) {
                        logger.severe("Failed to draw book title: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    logger.severe("Cannot draw book title - font unavailable");
                }
                
                // Table of Contents heading
                if (titleFont != null && !chapterTitles.isEmpty()) {
                    try {
                        y -= 40f;
                        // Set text color to black
                        contentStream.setNonStrokingColor(0, 0, 0);
                        // Reduced font size for TOC heading
                        float tocHeadingFontSize = 14f; // Reduced from 18f
                        contentStream.beginText();
                        contentStream.setFont(titleFont, tocHeadingFontSize);
                        float tocWidth = titleFont.getStringWidth("Table of Contents") / 1000f * tocHeadingFontSize;
                        float tocX = (pageWidth - tocWidth) / 2f;
                        contentStream.newLineAtOffset(tocX, y);
                        contentStream.showText("Table of Contents");
                        contentStream.endText();
                        y -= 50f;
                        
                        // Store the left edge of the centered TOC title for aligning content
                        // This will be used to align TOC entries to the TOC title
                        tocContentStartX = tocX;
                        
                        // Chapter list (links will be added after chapters are created)
                        if (regularFont != null) {
                            float lineHeight = 16f; // Reduced line height for smaller font
                            float textStartX = tocContentStartX; // Align to TOC title's left edge
                            float tocFontSize = 9f; // Reduced font size for TOC
                            
                            for (int i = 0; i < chapterTitles.size(); i++) {
                                String chapterTitle = chapterTitles.get(i);
                                String tocEntry = (i + 1) + ". " + chapterTitle;
                                
                                try {
                                    // Set text color to black
                                    contentStream.setNonStrokingColor(0, 0, 0);
                                    // Draw text
                                    contentStream.beginText();
                                    contentStream.setFont(regularFont, tocFontSize);
                                    contentStream.newLineAtOffset(textStartX, y);
                                    contentStream.showText(tocEntry);
                                    contentStream.endText();
                                } catch (Exception e) {
                                    logger.warning("Failed to draw TOC entry " + (i + 1) + ": " + e.getMessage());
                                    e.printStackTrace();
                                }
                                
                                // Store position for later link creation (after chapters are added)
                                // Links will be added in addTocLinks() method
                                
                                y -= lineHeight;
                                
                                if (y < margin) {
                                    // Would go off page - this shouldn't happen with reasonable chapter counts
                                    break;
                                }
                            }
                        } else {
                            logger.severe("Cannot draw TOC entries - regular font unavailable");
                        }
                    } catch (Exception e) {
                        logger.severe("Failed to add TOC heading or entries: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("Failed to add title page: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Adds clickable links to TOC entries on the title page.
     * This should be called after all chapters are added.
     */
    private void addTocLinks() {
        if (!titlePageAdded || chapters.isEmpty()) {
            return;
        }
        
        try {
            // Title page is page 0 (first page)
            PDPage titlePage = document.getPage(0);
            float pageHeight = PDRectangle.A4.getHeight();
            float margin = 50f;
            float lineHeight = 16f; // Match the reduced line height used in addTitlePage
            // Use the same X position as TOC content (aligned to TOC title)
            float textStartX = tocContentStartX;
            float pageWidth = PDRectangle.A4.getWidth();
            float textWidth = pageWidth - tocContentStartX - margin; // Width from TOC start to right margin
            
            // Calculate starting Y position for TOC entries
            // Account for two-line title (manga name + chapter range) and TOC heading
            float yStart = pageHeight - margin - 110f - 40f - 50f; // After title (two lines) and TOC heading
            
            // Add links for each chapter
            for (int i = 0; i < chapters.size() && i < document.getNumberOfPages() - 1; i++) {
                ChapterInfo chapter = chapters.get(i);
                float linkY = yStart - (i * lineHeight);
                
                // Create link annotation
                try {
                    // Get the target page (chapter page, accounting for title page)
                    PDPage targetPage = document.getPage(chapter.pageNumber - 1);
                    
                    // Create destination
                    PDPageFitDestination destination = new PDPageFitDestination();
                    destination.setPage(targetPage);
                    
                    // Create action
                    PDActionGoTo action = new PDActionGoTo();
                    action.setDestination(destination);
                    
                    // Create link annotation
                    PDAnnotationLink link = new PDAnnotationLink();
                    // Set link area (full width of text line)
                    PDRectangle linkRect = new PDRectangle(textStartX, linkY - 12f, textWidth, lineHeight);
                    link.setRectangle(linkRect);
                    link.setAction(action);
                    
                    // Remove border/frame from link annotation
                    // In PDFBox 2.0, set border width to 0 using COSDictionary
                    try {
                        // Get the underlying COSDictionary and set border width to 0
                        org.apache.pdfbox.cos.COSDictionary linkDict = link.getCOSObject();
                        if (linkDict != null) {
                            // Set Border array: [horizontal corner radius, vertical corner radius, width]
                            // Setting width to 0 makes the border invisible
                            org.apache.pdfbox.cos.COSArray borderArray = new org.apache.pdfbox.cos.COSArray();
                            borderArray.add(new org.apache.pdfbox.cos.COSFloat(0f)); // horizontal corner
                            borderArray.add(new org.apache.pdfbox.cos.COSFloat(0f)); // vertical corner
                            borderArray.add(new org.apache.pdfbox.cos.COSFloat(0f)); // width (0 = invisible)
                            linkDict.setItem("Border", borderArray);
                        }
                    } catch (Exception e) {
                        // If border setting fails, continue without it
                        logger.warning("Could not set link border style: " + e.getMessage());
                    }
                    
                    // Add link to title page
                    titlePage.getAnnotations().add(link);
                } catch (Exception e) {
                    logger.warning("Failed to add TOC link for chapter " + chapter.title + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to add TOC links: " + e.getMessage());
        }
    }

    private void addChapterTitlePage(String chapterTitle) throws IOException {
        // Try to get font - if unavailable, skip title page
        PDType1Font font;
        try {
            font = getTitleFont();
        } catch (LinkageError e) {
            // Font classes cannot be loaded (NoClassDefFoundError, etc.) - skip title page
            throw new IOException("Font classes unavailable - skipping title page: " + e.getMessage());
        }
        
        if (font == null) {
            // Font is not available - skip title page
            throw new IOException("Font not available - skipping title page");
        }
        
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        
        PDPageContentStream contentStream = null;
        try {
            contentStream = new PDPageContentStream(document, page);
            contentStream.beginText();
            contentStream.setFont(font, 24);
            // Use A4 height directly for title page positioning
            float pageHeight = PDRectangle.A4.getHeight();
            contentStream.newLineAtOffset(MARGIN, pageHeight - MARGIN - 50);
            contentStream.showText(chapterTitle);
            contentStream.endText();
        } catch (LinkageError e) {
            // Font provider issues (NoClassDefFoundError, ExceptionInInitializerError, etc.) - remove page and rethrow
            document.removePage(document.getNumberOfPages() - 1);
            throw new IOException("Font provider unavailable: " + e.getMessage(), e);
        } catch (Exception e) {
            // Any other error - remove page and rethrow
            document.removePage(document.getNumberOfPages() - 1);
            throw new IOException("Failed to add chapter title page: " + e.getMessage(), e);
        } finally {
            // Ensure contentStream is always closed
            if (contentStream != null) {
                try {
                    contentStream.close();
                } catch (IOException e) {
                    // Ignore close errors
                }
            }
        }
    }

    /**
     * Adds all images from a chapter to a single long page.
     * Images are stacked vertically, each using full width (A4 width, no margins).
     * Chapter title is added at the top of the page.
     * The page height is calculated to fit all images plus title.
     * 
     * @param imageFiles List of image files to add
     * @param chapterTitle Title of the chapter to display at the top
     */
    private void addChapterImagesPage(List<Path> imageFiles, String chapterTitle) throws IOException {
        // Ensure images are sorted by numeric filename (e.g., "1.jpg", "2.jpg", "10.jpg")
        // This prevents ordering issues when images are loaded from disk
        List<Path> sortedImageFiles = new java.util.ArrayList<>(imageFiles);
        sortedImageFiles.sort(java.util.Comparator.comparing(p -> {
            String name = p.getFileName().toString();
            try {
                // Extract number from filename (e.g., "1.jpg" -> 1, "10.jpg" -> 10)
                int dotIndex = name.lastIndexOf('.');
                if (dotIndex > 0) {
                    String numberPart = name.substring(0, dotIndex);
                    return Integer.parseInt(numberPart);
                }
            } catch (NumberFormatException e) {
                // If not a number, use 0 (will be sorted first)
            }
            return 0;
        }));
        
        // First pass: load all images and calculate total height needed
        java.util.List<PDImageXObject> images = new java.util.ArrayList<>();
        float totalHeight = 0f;
        
        // Add space for chapter title at the top - reserve more space to ensure visibility
        // Title spacing: 50f top margin + 24f font size + 20f bottom margin = 94f total
        float titleTopMargin = 50f; // Space from top of page (increased for better spacing)
        float titleFontSize = 24f; // Font size for chapter title
        float titleBottomMargin = 20f; // Space after title before images
        float titleHeight = titleTopMargin + titleFontSize + titleBottomMargin; // Total: 94f
        totalHeight += titleHeight;
        
        for (Path imageFile : sortedImageFiles) {
            try {
                byte[] imageBytes = Files.readAllBytes(imageFile);
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, imageBytes, imageFile.getFileName().toString());
                images.add(pdImage);
                
                // Calculate scaled dimensions for full width
                float imageWidth = pdImage.getWidth();
                float imageHeight = pdImage.getHeight();
                
                // Scale to full page width while maintaining aspect ratio
                float scale = CONTENT_WIDTH / imageWidth;
                float scaledHeight = imageHeight * scale;
                
                totalHeight += scaledHeight;
            } catch (IOException e) {
                logger.warning("Failed to load image " + imageFile + ": " + e.getMessage());
            }
        }
        
        if (images.isEmpty()) {
            logger.warning("No valid images found for chapter " + chapterTitle);
            return;
        }
        
        // Create a custom page with A4 width and calculated height
        PDRectangle customPageSize = new PDRectangle(PAGE_WIDTH, totalHeight);
        PDPage page = new PDPage(customPageSize);
        document.addPage(page);
        
        // Second pass: draw chapter title and all images stacked vertically
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            float currentY = totalHeight; // Start from top of page
            
            // Draw chapter title at the top - reserve space and ensure it's visible
            PDType1Font titleFont = getTitleFont();
            
            // Use same spacing values as calculated in first pass (already defined above)
            
            if (titleFont != null) {
                try {
                    // Set text color to black
                    contentStream.setNonStrokingColor(0, 0, 0);
                    contentStream.beginText();
                    contentStream.setFont(titleFont, titleFontSize);
                    // Center the title
                    float titleWidth = titleFont.getStringWidth(chapterTitle) / 1000f * titleFontSize;
                    float titleX = (PAGE_WIDTH - titleWidth) / 2f;
                    // Position title with proper spacing from top
                    // Y coordinate: totalHeight - titleTopMargin (20 points from top)
                    currentY = totalHeight - titleTopMargin;
                    contentStream.newLineAtOffset(titleX, currentY);
                    contentStream.showText(chapterTitle);
                    contentStream.endText();
                    // Update currentY to leave space after title before first image
                    currentY = totalHeight - titleTopMargin - titleFontSize - titleBottomMargin;
                } catch (Exception e) {
                    logger.severe("Failed to draw chapter title: " + e.getMessage());
                    e.printStackTrace();
                    // Even if title fails, reserve the space so images don't overlap
                    currentY = totalHeight - titleTopMargin - titleFontSize - titleBottomMargin;
                }
            } else {
                String errorMsg = "Cannot draw chapter title - font unavailable for: " + chapterTitle;
                logger.severe(errorMsg);
                System.err.println("WARNING: " + errorMsg);
                // Reserve space even if font unavailable (prevents images from starting at top)
                currentY = totalHeight - titleTopMargin - titleFontSize - titleBottomMargin;
            }
            
            // Draw all images stacked vertically
            for (PDImageXObject pdImage : images) {
                float imageWidth = pdImage.getWidth();
                float imageHeight = pdImage.getHeight();
                
                // Scale to full page width while maintaining aspect ratio
                float scale = CONTENT_WIDTH / imageWidth;
                float scaledWidth = CONTENT_WIDTH;
                float scaledHeight = imageHeight * scale;
                
                // Position image at current Y
                currentY -= scaledHeight;
                float x = MARGIN; // 0, but keeping for clarity
                float y = currentY;
                
                // Draw image at full width
                contentStream.drawImage(pdImage, x, y, scaledWidth, scaledHeight);
            }
        }
        
        logger.info("Added " + images.size() + " images to chapter page with title (total height: " + totalHeight + " points)");
    }

    /**
     * Saves the PDF to the output directory.
     * Creates bookmarks/outline for navigation to chapters.
     * 
     * @param outputDir The directory where the PDF should be saved
     * @param bookTitle The title to use for the filename (will be sanitized)
     * @return The path to the created PDF file
     * @throws IOException If the PDF cannot be written
     */
    public Path saveTo(Path outputDir, String bookTitle) throws IOException {
        // Add clickable links to TOC entries
        addTocLinks();
        
        // Create bookmarks/outline for navigation
        if (!chapters.isEmpty()) {
            try {
                PDDocumentOutline outline = new PDDocumentOutline();
                document.getDocumentCatalog().setDocumentOutline(outline);
                
                for (ChapterInfo chapter : chapters) {
                    try {
                        // Get the page (chapters are 1-indexed, pages are 0-indexed)
                        PDPage page = document.getPage(chapter.pageNumber - 1);
                        
                        // Create destination for the page
                        PDPageFitDestination destination = new PDPageFitDestination();
                        destination.setPage(page);
                        
                        // Create bookmark
                        PDOutlineItem bookmark = new PDOutlineItem();
                        bookmark.setTitle(chapter.title);
                        bookmark.setDestination(destination);
                        outline.addLast(bookmark);
                    } catch (Exception e) {
                        logger.warning("Failed to create bookmark for chapter " + chapter.title + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to create PDF bookmarks: " + e.getMessage());
            }
        }
        
        // Ensure output directory exists
        Files.createDirectories(outputDir);
        
        // Sanitize book title for filename
        String sanitizedTitle = LoggerFactory.sanitizeFilename(bookTitle);
        String pdfFileName = sanitizedTitle + ".pdf";
        Path pdfFile = outputDir.resolve(pdfFileName);
        
        // Write PDF
        document.save(pdfFile.toFile());
        document.close();
        
        logger.info("PDF saved to: " + pdfFile.toAbsolutePath());
        return pdfFile;
    }
}

