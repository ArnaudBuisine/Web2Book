// Generated code - Model: Auto (Cursor AI)
// Date: 2025-01-27
package org.web2book.core;

import org.web2book.epub.EpubBuilderService;
import org.web2book.pdf.PdfBuilderService;
import org.web2book.html.HtmlExtractor;
import org.web2book.log.LoggerFactory;
import org.web2book.model.ChapterContent;
import org.web2book.model.ChapterInfo;
import org.web2book.net.HttpClientService;
import org.web2book.util.TemplateEngine;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Handles the processing of a single book configuration.
 */
public class BookJob {
    private final Path bookConfigPath;
    private final Properties globalProps;
    private final HttpClientService httpClientService;
    
    private Properties bookProps;
    private Path outputDir;
    private Path logDir;
    private Path tempDir;
    private long thinkingTimeMs;
    private String outputFormat;
    private Logger logger;
    private String bookTitle;
    private int maxConcurrentImageDownloads;
    private boolean regenerateExistingBooks;
    
    // Track incomplete books with their failed image URLs
    private static class IncompleteBook {
        final String bookFilename;
        final int bookIndex;
        final List<String> failedUrls;
        
        IncompleteBook(String bookFilename, int bookIndex, List<String> failedUrls) {
            this.bookFilename = bookFilename;
            this.bookIndex = bookIndex;
            this.failedUrls = new ArrayList<>(failedUrls);
        }
    }
    
    private final List<IncompleteBook> incompleteBooks = new ArrayList<>();

    public BookJob(Path bookConfigPath, Properties globalProps, HttpClientService httpClientService) {
        this.bookConfigPath = bookConfigPath;
        this.globalProps = globalProps;
        this.httpClientService = httpClientService;
    }

    /**
     * Runs the book processing job.
     */
    public void run() {
        try {
            // Load book properties
            loadBookProperties();
            
            // Validate properties
            if (!validateProperties()) {
                return;
            }
            
            // Resolve output directory and thinking time
            resolveConfiguration();
            
            // Build book title
            bookTitle = TemplateEngine.applyBookTitleTemplate(
                bookProps.getProperty("book.title.template"), bookProps);
            
            // Ensure output directory exists
            Files.createDirectories(outputDir);
            
            // Ensure log directory exists
            Files.createDirectories(logDir);
            
            // Ensure temp directory exists
            Files.createDirectories(tempDir);
            
            // Initialize logger
            String loggerName = "web2book." + LoggerFactory.sanitizeFilename(bookTitle);
            logger = LoggerFactory.createFileLogger(loggerName, logDir, bookTitle, globalProps);
            
            logger.info("Starting book processing: " + bookTitle);
            logger.info("Config file: " + bookConfigPath.toAbsolutePath());
            
            // Fetch starting page
            String startingUrl = bookProps.getProperty("starting.url");
            logger.info("Fetching starting page: " + startingUrl);
            
            // Set referer URL (use starting URL or site root)
            String baseUrl = bookProps.getProperty("chapter.base.url");
            if (baseUrl != null) {
                httpClientService.setRefererUrl(baseUrl);
            } else {
                // Extract root from starting URL
                try {
                    URL url = new URL(startingUrl);
                    String rootUrl = url.getProtocol() + "://" + url.getHost();
                    httpClientService.setRefererUrl(rootUrl);
                } catch (Exception e) {
                    httpClientService.setRefererUrl(startingUrl);
                }
            }
            
            String startingPageHtml = httpClientService.getHtml(startingUrl);
            
            if (startingPageHtml == null) {
                String errorMsg = "Failed to fetch starting page. Aborting book processing.";
                logger.severe(errorMsg);
                System.err.println("ERROR: " + errorMsg);
                return;
            }
            
            // Check for Cloudflare challenge on starting page
            if (httpClientService.isCloudflareChallenge(startingPageHtml)) {
                String errorMsg = "Cloudflare challenge detected on starting URL, aborting this book.";
                logger.severe(errorMsg);
                System.err.println("ERROR: " + errorMsg);
                saveHtmlForDebug(startingPageHtml, "debug-cloudflare-starting-page.html", "Cloudflare challenge on starting page");
                return;
            }
            
            // Extract chapter links
            List<ChapterInfo> allChapters = HtmlExtractor.extractChapterLinks(startingPageHtml, bookProps);
            
            if (allChapters.isEmpty()) {
                String errorMsg = "No chapters found. Check chapter.pattern. Aborting book processing.";
                logger.severe(errorMsg);
                System.err.println("ERROR: " + errorMsg);
                saveHtmlForDebug(startingPageHtml, "debug-starting-page.html", "starting page");
                return;
            }
            
            logger.info("Found " + allChapters.size() + " total chapters");
            
            // Filter chapters by range
            int chapterStart = Integer.parseInt(bookProps.getProperty("chapter.start"));
            int chapterEnd = Integer.parseInt(bookProps.getProperty("chapter.end"));
            
            // Filter, deduplicate (keep first occurrence of each chapter number), and sort
            Map<Integer, ChapterInfo> chapterMap = new LinkedHashMap<>();
            for (ChapterInfo ch : allChapters) {
                if (ch.getChapterNumber() >= chapterStart && ch.getChapterNumber() <= chapterEnd) {
                    // Only add if we haven't seen this chapter number before
                    chapterMap.putIfAbsent(ch.getChapterNumber(), ch);
                }
            }
            
            List<ChapterInfo> filteredChapters = chapterMap.values().stream()
                .sorted(Comparator.comparingInt(ChapterInfo::getChapterNumber))
                .collect(Collectors.toList());
            
            logger.info("Filtered to " + filteredChapters.size() + " chapters (range: " + 
                chapterStart + " to " + chapterEnd + ")");
            
            if (filteredChapters.isEmpty()) {
                String errorMsg = "No chapters in specified range. Aborting book processing.";
                logger.warning(errorMsg);
                System.err.println("ERROR: " + errorMsg);
                return;
            }
            
            // Check for max chapters per book
            String maxChaptersStr = bookProps.getProperty("max.chapters.per.book");
            int maxChaptersPerBook = 0;
            if (maxChaptersStr != null && !maxChaptersStr.trim().isEmpty()) {
                try {
                    maxChaptersPerBook = Integer.parseInt(maxChaptersStr.trim());
                    if (maxChaptersPerBook <= 0) {
                        String errorMsg = "max.chapters.per.book must be > 0. Aborting book processing.";
                        logger.severe(errorMsg);
                        System.err.println("ERROR: " + errorMsg);
                        return;
                    }
                } catch (NumberFormatException e) {
                    String errorMsg = "Invalid max.chapters.per.book value: " + maxChaptersStr + ". Aborting book processing.";
                    logger.severe(errorMsg);
                    System.err.println("ERROR: " + errorMsg);
                    return;
                }
            }
            
            // Process volumes
            if (maxChaptersPerBook > 0) {
                processVolumes(filteredChapters, maxChaptersPerBook);
            } else {
                processSingleVolume(filteredChapters, bookTitle);
            }
            
            logger.info("Book processing completed: " + bookTitle);
            
        } catch (Exception e) {
            String errorMsg = "Error processing book: " + e.getMessage();
            if (logger != null) {
                logger.log(Level.SEVERE, errorMsg, e);
            }
            System.err.println("ERROR: " + errorMsg);
            e.printStackTrace();
        }
    }

    private void loadBookProperties() throws IOException {
        bookProps = new Properties();
        try (FileInputStream fis = new FileInputStream(bookConfigPath.toFile())) {
            bookProps.load(fis);
        }
    }

    private boolean validateProperties() {
        String[] mandatory = {
            "starting.url",
            "chapter.pattern",
            "chapter.base.url",
            "image.pattern",
            "chapter.start",
            "chapter.end",
            "book.title.template",
            "chapter.title.template"
        };
        
        for (String key : mandatory) {
            if (bookProps.getProperty(key) == null || bookProps.getProperty(key).trim().isEmpty()) {
                System.err.println("ERROR: Missing mandatory property '" + key + "' in " + bookConfigPath);
                return false;
            }
        }
        
        // Validate optional but impactful properties
        String deleteImages = bookProps.getProperty("delete.images.after.generation");
        if (deleteImages == null || deleteImages.trim().isEmpty()) {
            if (logger != null) {
                logger.warning("delete.images.after.generation not set, defaulting to false");
            }
        }
        
        String maxChapters = bookProps.getProperty("max.chapters.per.book");
        if (maxChapters != null && !maxChapters.trim().isEmpty()) {
            try {
                int value = Integer.parseInt(maxChapters.trim());
                if (value <= 0) {
                    System.err.println("ERROR: max.chapters.per.book must be > 0 in " + bookConfigPath);
                    return false;
                }
            } catch (NumberFormatException e) {
                System.err.println("ERROR: Invalid max.chapters.per.book value in " + bookConfigPath);
                return false;
            }
        }
        
        return true;
    }

    private void resolveConfiguration() {
        // Resolve output directory
        String bookOutputDir = bookProps.getProperty("output.dir");
        if (bookOutputDir != null && !bookOutputDir.trim().isEmpty()) {
            outputDir = Paths.get(bookOutputDir);
        } else {
            String defaultOutputDir = globalProps.getProperty("default.output.dir", "output");
            outputDir = Paths.get(defaultOutputDir);
        }
        
        // Resolve thinking time
        String bookThinkingTime = bookProps.getProperty("thinking.time.ms");
        if (bookThinkingTime != null && !bookThinkingTime.trim().isEmpty()) {
            try {
                thinkingTimeMs = Long.parseLong(bookThinkingTime.trim());
            } catch (NumberFormatException e) {
                thinkingTimeMs = Long.parseLong(globalProps.getProperty("default.thinking.time.ms", "1500"));
            }
        } else {
            thinkingTimeMs = Long.parseLong(globalProps.getProperty("default.thinking.time.ms", "1500"));
        }
        
        // Resolve output format
        String bookOutputFormat = bookProps.getProperty("output.format");
        if (bookOutputFormat != null && !bookOutputFormat.trim().isEmpty()) {
            outputFormat = bookOutputFormat.trim().toLowerCase();
            if (!outputFormat.equals("epub") && !outputFormat.equals("pdf")) {
                outputFormat = globalProps.getProperty("default.output.format", "pdf").toLowerCase();
            }
        } else {
            outputFormat = globalProps.getProperty("default.output.format", "pdf").toLowerCase();
        }
        
        // Resolve log directory
        String bookLogDir = bookProps.getProperty("log.dir");
        if (bookLogDir != null && !bookLogDir.trim().isEmpty()) {
            logDir = Paths.get(bookLogDir);
        } else {
            String defaultLogDir = globalProps.getProperty("default.log.dir");
            if (defaultLogDir != null && !defaultLogDir.trim().isEmpty()) {
                logDir = Paths.get(defaultLogDir);
            } else {
                // Fallback to output directory if not specified (backward compatibility)
                logDir = outputDir;
            }
        }
        
        // Resolve temp directory
        String bookTempDir = bookProps.getProperty("temp.dir");
        if (bookTempDir != null && !bookTempDir.trim().isEmpty()) {
            tempDir = Paths.get(bookTempDir);
        } else {
            String defaultTempDir = globalProps.getProperty("default.temp.dir", "tmp");
            tempDir = Paths.get(defaultTempDir);
        }
        
        // Resolve max concurrent image downloads
        String bookMaxConcurrent = bookProps.getProperty("max.concurrent.image.downloads");
        if (bookMaxConcurrent != null && !bookMaxConcurrent.trim().isEmpty()) {
            try {
                int value = Integer.parseInt(bookMaxConcurrent.trim());
                if (value > 0) {
                    maxConcurrentImageDownloads = value;
                } else {
                    // Invalid value, use global or default
                    String globalMaxConcurrent = globalProps.getProperty("max.concurrent.image.downloads");
                    if (globalMaxConcurrent != null && !globalMaxConcurrent.trim().isEmpty()) {
                        try {
                            int globalValue = Integer.parseInt(globalMaxConcurrent.trim());
                            maxConcurrentImageDownloads = (globalValue > 0) ? globalValue : 4;
                        } catch (NumberFormatException e) {
                            maxConcurrentImageDownloads = 4;
                        }
                    } else {
                        maxConcurrentImageDownloads = 4;
                    }
                }
            } catch (NumberFormatException e) {
                // Invalid value, use global or default
                String globalMaxConcurrent = globalProps.getProperty("max.concurrent.image.downloads");
                if (globalMaxConcurrent != null && !globalMaxConcurrent.trim().isEmpty()) {
                    try {
                        int globalValue = Integer.parseInt(globalMaxConcurrent.trim());
                        maxConcurrentImageDownloads = (globalValue > 0) ? globalValue : 4;
                    } catch (NumberFormatException ex) {
                        maxConcurrentImageDownloads = 4;
                    }
                } else {
                    maxConcurrentImageDownloads = 4;
                }
            }
        } else {
            // Book-specific not set, use global or default
            String globalMaxConcurrent = globalProps.getProperty("max.concurrent.image.downloads");
            if (globalMaxConcurrent != null && !globalMaxConcurrent.trim().isEmpty()) {
                try {
                    int globalValue = Integer.parseInt(globalMaxConcurrent.trim());
                    maxConcurrentImageDownloads = (globalValue > 0) ? globalValue : 4;
                } catch (NumberFormatException e) {
                    maxConcurrentImageDownloads = 4;
                }
            } else {
                maxConcurrentImageDownloads = 4;
            }
        }
        
        // Resolve regenerate existing books setting
        String bookRegenerate = bookProps.getProperty("regenerate.existing.books");
        if (bookRegenerate != null && !bookRegenerate.trim().isEmpty()) {
            regenerateExistingBooks = "true".equalsIgnoreCase(bookRegenerate.trim());
        } else {
            String defaultRegenerate = globalProps.getProperty("default.regenerate.existing.books", "false");
            regenerateExistingBooks = "true".equalsIgnoreCase(defaultRegenerate.trim());
        }
    }

    /**
     * Result of processing chapters, including processed chapters and failed image URLs/filenames.
     */
    private static class ProcessChaptersResult {
        final List<ChapterContent> processedChapters;
        final List<String> failedUrls;
        final java.util.Map<Integer, java.util.Set<String>> failedFilenamesByChapter; // Chapter number -> Set of failed filenames
        
        ProcessChaptersResult(List<ChapterContent> processedChapters, List<String> failedUrls, 
                java.util.Map<Integer, java.util.Set<String>> failedFilenamesByChapter) {
            this.processedChapters = processedChapters;
            this.failedUrls = failedUrls;
            this.failedFilenamesByChapter = failedFilenamesByChapter;
        }
    }
    
    /**
     * Processes a list of chapters and returns the successfully processed ChapterContent list and failed URLs.
     * 
     * @param chapters The chapters to process
     * @param tmpImagesDir The temporary images directory
     * @param tmpHtmlDir The temporary HTML directory
     * @param totalChaptersOverall Total number of chapters across all books
     * @param chaptersProcessedBefore Number of chapters already processed in previous books
     * @return ProcessChaptersResult containing processed chapters and failed URLs
     */
    private ProcessChaptersResult processChapters(List<ChapterInfo> chapters, Path tmpImagesDir, Path tmpHtmlDir,
            int totalChaptersOverall, int chaptersProcessedBefore) {
        List<ChapterContent> processedChapters = new ArrayList<>();
        List<String> allFailedUrls = new ArrayList<>();
        java.util.Map<Integer, java.util.Set<String>> failedFilenamesByChapter = new java.util.HashMap<>();
        Random random = new Random();
        
        int processedCount = 0;
        
        for (ChapterInfo chapterInfo : chapters) {
            // Track chapter processing time
            long chapterStartTime = System.currentTimeMillis();
            
            try {
                processedCount++;
                // Calculate overall progress: (chapters processed before + current chapter) / total chapters overall
                int overallProcessed = chaptersProcessedBefore + processedCount;
                int percentage = (int) Math.round((overallProcessed * 100.0) / totalChaptersOverall);
                String msg = "Processing chapter " + chapterInfo.getChapterNumber() + " (" + percentage + "% overall)";
                logger.info("Processing chapter " + chapterInfo.getChapterNumber() + " (" + percentage + "% overall)");
                System.out.println(msg);
                
                // Check if chapter already exists
                ChapterContent existingChapter = loadExistingChapter(chapterInfo, tmpHtmlDir, tmpImagesDir);
                if (existingChapter != null) {
                    processedChapters.add(existingChapter);
                    logger.info("Skipped processing chapter " + chapterInfo.getChapterNumber() + " (already exists)");
                    continue; // Skip thinking time and move to next chapter
                }
                
                // Fetch chapter HTML
                String chapterHtml = httpClientService.getHtml(chapterInfo.getFullUrl());
                if (chapterHtml == null) {
                    logger.warning("Failed to fetch chapter " + chapterInfo.getChapterNumber() + " from URL: " + chapterInfo.getFullUrl() + ". Skipping.");
                    continue;
                }
                
                // Check for Cloudflare challenge
                if (httpClientService.isCloudflareChallenge(chapterHtml)) {
                    String errorMsg = "Cloudflare challenge detected while processing chapter " + 
                        chapterInfo.getChapterNumber() + ", stopping further downloads.";
                    logger.severe(errorMsg);
                    System.err.println("ERROR: " + errorMsg);
                    logger.info("Will generate book for " + processedChapters.size() + 
                        " successfully processed chapters only.");
                    break; // Stop processing further chapters
                }
                
                // Extract image URLs
                List<String> imageUrls = HtmlExtractor.extractImageUrls(chapterHtml, bookProps);
                
                if (imageUrls.isEmpty()) {
                    logger.warning("Chapter " + chapterInfo.getChapterNumber() + " contains no images. Skipping.");
                    saveHtmlForDebug(chapterHtml, "debug-chapter-" + chapterInfo.getChapterNumber() + ".html", 
                        "chapter " + chapterInfo.getChapterNumber());
                    continue;
                }
                
                logger.info("Found " + imageUrls.size() + " images in chapter " + chapterInfo.getChapterNumber());
                
                // Download images
                Path chapterImagesDir = tmpImagesDir.resolve(String.valueOf(chapterInfo.getChapterNumber()));
                Files.createDirectories(chapterImagesDir);
                
                // Calculate overall chapter index for progress tracking
                int overallChapterIndex = chaptersProcessedBefore + processedCount;
                DownloadResult downloadResult = downloadImages(imageUrls, chapterImagesDir, chapterInfo.getChapterNumber(), 
                    overallChapterIndex, totalChaptersOverall);
                
                List<Path> downloadedImages = downloadResult.downloadedImages;
                allFailedUrls.addAll(downloadResult.failedUrls);
                
                // Track failed filenames for this chapter
                if (!downloadResult.failedUrlToFilename.isEmpty()) {
                    java.util.Set<String> failedFilenames = new java.util.HashSet<>(downloadResult.failedUrlToFilename.values());
                    failedFilenamesByChapter.put(chapterInfo.getChapterNumber(), failedFilenames);
                }
                
                if (downloadedImages.isEmpty()) {
                    logger.warning("Chapter " + chapterInfo.getChapterNumber() + 
                        " has no successfully downloaded images, skipping this chapter.");
                    // Still apply thinking time before next chapter
                    if (chapters.indexOf(chapterInfo) < chapters.size() - 1) {
                        long jitter = 500 + random.nextInt(1001); // 500-1500 ms
                        long totalWait = thinkingTimeMs + jitter;
                        logger.info("Waiting " + totalWait + " ms before next chapter");
                        Thread.sleep(totalWait);
                    }
                    continue;
                }
                
                // Create chapter HTML
                String chapterTitle = TemplateEngine.applyChapterTitleTemplate(
                    bookProps.getProperty("chapter.title.template"), 
                    chapterInfo.getChapterNumber(), 
                    bookProps);
                
                // Get failed filenames for this chapter
                java.util.Set<String> chapterFailedFilenames = failedFilenamesByChapter.getOrDefault(
                    chapterInfo.getChapterNumber(), new java.util.HashSet<>());
                
                Path chapterHtmlFile = createChapterHtml(chapterTitle, downloadedImages, 
                    chapterInfo.getChapterNumber(), tmpHtmlDir, chapterFailedFilenames);
                
                ChapterContent chapterContent = new ChapterContent(chapterInfo, downloadedImages, chapterHtmlFile);
                processedChapters.add(chapterContent);
                
                // Calculate and log chapter duration
                long chapterDuration = System.currentTimeMillis() - chapterStartTime;
                String durationStr = formatDuration(chapterDuration);
                logger.info("Completed chapter " + chapterInfo.getChapterNumber() + " (duration: " + durationStr + ")");
                
                // Thinking time with random jitter (AFTER all images are downloaded, BEFORE next chapter)
                if (chapters.indexOf(chapterInfo) < chapters.size() - 1) {
                    long jitter = 500 + random.nextInt(1001); // 500-1500 ms
                    long totalWait = thinkingTimeMs + jitter;
                    logger.info("Waiting " + totalWait + " ms before next chapter");
                    Thread.sleep(totalWait);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                String errorMsg = "Interrupted during chapter processing";
                logger.severe(errorMsg);
                System.err.println("ERROR: " + errorMsg);
                break;
            } catch (Exception e) {
                String errorMsg = "Error processing chapter " + chapterInfo.getChapterNumber() + ": " + e.getMessage();
                logger.log(Level.SEVERE, errorMsg, e);
                System.err.println("ERROR: " + errorMsg);
            }
        }
        
        return new ProcessChaptersResult(processedChapters, allFailedUrls, failedFilenamesByChapter);
    }
    
    /**
     * Generates a book from a list of processed chapters.
     * 
     * @param bookChapters The processed chapters for this book
     * @param bookIndex The index of this book (1-based)
     * @param totalBooks The total number of books
     * @param failedUrls List of failed image URLs for this book (null or empty if none)
     * @param failedFilenamesByChapter Map of chapter number to set of failed image filenames
     */
    private void generateBook(List<ChapterContent> bookChapters, int bookIndex, int totalBooks, List<String> failedUrls,
            java.util.Map<Integer, java.util.Set<String>> failedFilenamesByChapter) {
        if (bookChapters.isEmpty()) {
            logger.warning("No chapters to generate book " + bookIndex + ", skipping.");
            return;
        }
        
        // Calculate effective chapter range for this book
        int effectiveStart = bookChapters.stream()
                .mapToInt(c -> c.getInfo().getChapterNumber())
                .min()
                .orElse(0);
        int effectiveEnd = bookChapters.stream()
                .mapToInt(c -> c.getInfo().getChapterNumber())
                .max()
                .orElse(0);
            
            // Adjust book title template with effective range
            Properties adjustedProps = new Properties(bookProps);
            adjustedProps.setProperty("chapter.start", String.valueOf(effectiveStart));
            adjustedProps.setProperty("chapter.end", String.valueOf(effectiveEnd));
            
            // Generate title for PDF display (using book.title.template)
        String bookTitle = TemplateEngine.applyBookTitleTemplate(
                bookProps.getProperty("book.title.template"), adjustedProps);
            
            // Generate filename for saving (using book.filename.template, fallback to book.title.template)
            String filenameTemplate = bookProps.getProperty("book.filename.template");
            if (filenameTemplate == null || filenameTemplate.isEmpty()) {
                filenameTemplate = bookProps.getProperty("book.title.template", "");
            }
        String bookFilename = TemplateEngine.applyBookTitleTemplate(filenameTemplate, adjustedProps);
        
        // Add -INCOMPLETE suffix if there are failed image downloads
        if (failedUrls != null && !failedUrls.isEmpty()) {
            bookFilename = bookFilename + " - INCOMPLETE";
            logger.warning("Book " + bookIndex + " has " + failedUrls.size() + " failed image downloads, adding -INCOMPLETE suffix");
            incompleteBooks.add(new IncompleteBook(bookFilename, bookIndex, failedUrls));
        }
        
        logger.info("Generating book " + bookIndex + "/" + totalBooks + 
                ": Chapters " + effectiveStart + " to " + effectiveEnd);
            
        // Generate the book
            if (outputFormat.equals("pdf")) {
            PdfBuilderService pdfBuilder = new PdfBuilderService(bookTitle, logger, adjustedProps);
                
                // Collect all chapter titles first for TOC
                List<String> chapterTitles = new ArrayList<>();
            for (ChapterContent chapterContent : bookChapters) {
                    String chapterTitle = TemplateEngine.applyChapterTitleTemplate(
                        bookProps.getProperty("chapter.title.template"), 
                        chapterContent.getInfo().getChapterNumber(), 
                        bookProps);
                    chapterTitles.add(chapterTitle);
                }
                
                // Add title page with TOC first
                pdfBuilder.addTitlePage(chapterTitles);
                
                // Then add all chapters
            for (int i = 0; i < bookChapters.size(); i++) {
                ChapterContent chapterContent = bookChapters.get(i);
                int chapterNumber = chapterContent.getInfo().getChapterNumber();
                java.util.Set<String> failedFilenames = failedFilenamesByChapter.getOrDefault(chapterNumber, new java.util.HashSet<>());
                pdfBuilder.addChapter(chapterContent, chapterTitles.get(i), failedFilenames);
                }
                
                // Save PDF (use filename template for the file, title template is already used in PDF)
                try {
                Path pdfFile = pdfBuilder.saveTo(outputDir, bookFilename);
                    logger.info("PDF created: " + pdfFile.toAbsolutePath());
                logger.info("Book " + bookIndex + " contains " + bookChapters.size() + 
                        " chapters (range: " + effectiveStart + " to " + effectiveEnd + ")");
                } catch (IOException e) {
                logger.severe("Failed to save PDF book " + bookIndex + ": " + e.getMessage());
                }
            } else {
                // EPUB format
            EpubBuilderService epubBuilder = new EpubBuilderService(bookTitle, logger, adjustedProps);
            for (ChapterContent chapterContent : bookChapters) {
                    String chapterTitle = TemplateEngine.applyChapterTitleTemplate(
                        bookProps.getProperty("chapter.title.template"), 
                        chapterContent.getInfo().getChapterNumber(), 
                        bookProps);
                int chapterNumber = chapterContent.getInfo().getChapterNumber();
                java.util.Set<String> failedFilenames = failedFilenamesByChapter.getOrDefault(chapterNumber, new java.util.HashSet<>());
                epubBuilder.addChapter(chapterContent, chapterTitle, failedFilenames);
                }
                
                // Save EPUB (use filename template for the file, title template is already used in EPUB)
                try {
                Path epubFile = epubBuilder.saveTo(outputDir, bookFilename);
                    logger.info("EPUB created: " + epubFile.toAbsolutePath());
                logger.info("Book " + bookIndex + " contains " + bookChapters.size() + 
                        " chapters (range: " + effectiveStart + " to " + effectiveEnd + ")");
                } catch (IOException e) {
                logger.severe("Failed to save EPUB book " + bookIndex + ": " + e.getMessage());
            }
        }
    }
    
    private void processVolumes(List<ChapterInfo> chapters, int maxChaptersPerBook) {
        // Calculate how many books we need
        int totalChapters = chapters.size();
        int totalBooks = (int) Math.ceil((double) totalChapters / maxChaptersPerBook);
        
        logger.info("Processing " + totalChapters + " chapters into " + totalBooks + 
            " books (max " + maxChaptersPerBook + " chapters per book)");
        System.out.println("Processing " + totalChapters + " chapters into " + totalBooks + 
            " books (max " + maxChaptersPerBook + " chapters per book)");
        
        Path tmpImagesDir = tempDir.resolve("images");
        Path tmpHtmlDir = tempDir.resolve("html");
        
        try {
            Files.createDirectories(tmpImagesDir);
            Files.createDirectories(tmpHtmlDir);
        } catch (IOException e) {
            String errorMsg = "Failed to create temporary directories: " + e.getMessage();
            logger.severe(errorMsg);
            System.err.println("ERROR: " + errorMsg);
            return;
        }
        
        // Track overall progress across all books
        int chaptersProcessedSoFar = 0;
        
        // Process each book sequentially
        for (int bookIndex = 0; bookIndex < totalBooks; bookIndex++) {
            int startIdx = bookIndex * maxChaptersPerBook;
            int endIdx = Math.min(startIdx + maxChaptersPerBook, totalChapters);
            List<ChapterInfo> bookChapters = chapters.subList(startIdx, endIdx);
            
            if (bookChapters.isEmpty()) {
                continue; // Skip empty books
            }
            
            // Calculate chapter range for this book
            int bookStart = bookChapters.get(0).getChapterNumber();
            int bookEnd = bookChapters.get(bookChapters.size() - 1).getChapterNumber();
            
            logger.info("=== Processing Book " + (bookIndex + 1) + "/" + totalBooks + 
                ": Chapters " + bookStart + " to " + bookEnd + " ===");
            System.out.println("\n=== Processing Book " + (bookIndex + 1) + "/" + totalBooks + 
                ": Chapters " + bookStart + " to " + bookEnd + " ===");
            
            // Check if book already exists
            if (!regenerateExistingBooks && bookFileExists(bookStart, bookEnd)) {
                logger.info("Book " + (bookIndex + 1) + " already exists (Chapters " + bookStart + 
                    " to " + bookEnd + "). Skipping (regenerate.existing.books=false).");
                System.out.println("Skipping book " + (bookIndex + 1) + "/" + totalBooks + 
                    " - already exists (Chapters " + bookStart + " to " + bookEnd + ")");
                // Update progress counter for skipped books
                chaptersProcessedSoFar += bookChapters.size();
                continue;
            }
            
            // Process chapters for this book
            long bookStartTime = System.currentTimeMillis();
            ProcessChaptersResult processResult = processChapters(bookChapters, tmpImagesDir, tmpHtmlDir,
                    totalChapters, chaptersProcessedSoFar);
            List<ChapterContent> processedChapters = processResult.processedChapters;
            List<String> failedUrls = processResult.failedUrls;
            java.util.Map<Integer, java.util.Set<String>> failedFilenamesByChapter = processResult.failedFilenamesByChapter;
            
            // Update progress counter after processing this book
            chaptersProcessedSoFar += processedChapters.size();
            
            if (processedChapters.isEmpty()) {
                logger.warning("No chapters were successfully processed for book " + (bookIndex + 1) + ", skipping book generation.");
                continue;
            }
            
            // Generate the book immediately
            generateBook(processedChapters, bookIndex + 1, totalBooks, failedUrls, failedFilenamesByChapter);
            
            // Log book processing duration
            long bookDuration = System.currentTimeMillis() - bookStartTime;
            String bookDurationStr = formatDuration(bookDuration);
            logger.info("Completed book " + (bookIndex + 1) + "/" + totalBooks + 
                " (duration: " + bookDurationStr + ")");
        }
        
        logger.info("Completed processing all " + totalBooks + " books");
        
        // Cleanup at the end if configured
        cleanup(tmpImagesDir, tmpHtmlDir);
        
        // Report incomplete books
        if (!incompleteBooks.isEmpty()) {
            logger.warning("=== INCOMPLETE BOOKS REPORT ===");
            logger.warning("Total incomplete books: " + incompleteBooks.size());
            System.out.println("\n=== INCOMPLETE BOOKS REPORT ===");
            System.out.println("Total incomplete books: " + incompleteBooks.size());
            
            for (IncompleteBook incomplete : incompleteBooks) {
                logger.warning("Book " + incomplete.bookIndex + ": " + incomplete.bookFilename);
                logger.warning("  Failed image URLs (" + incomplete.failedUrls.size() + "):");
                System.out.println("\nBook " + incomplete.bookIndex + ": " + incomplete.bookFilename);
                System.out.println("  Failed image URLs (" + incomplete.failedUrls.size() + "):");
                for (String failedUrl : incomplete.failedUrls) {
                    logger.warning("    - " + failedUrl);
                    System.out.println("    - " + failedUrl);
                }
            }
            
            logger.warning("=== END INCOMPLETE BOOKS REPORT ===");
            System.out.println("\n=== END INCOMPLETE BOOKS REPORT ===");
        } else {
            logger.info("All books were generated successfully with all images downloaded.");
            System.out.println("All books were generated successfully with all images downloaded.");
        }
    }

    private void processSingleVolume(List<ChapterInfo> chapters, String volumeTitle) {
        Path tmpImagesDir = tempDir.resolve("images");
        Path tmpHtmlDir = tempDir.resolve("html");
        
        try {
            Files.createDirectories(tmpImagesDir);
            Files.createDirectories(tmpHtmlDir);
        } catch (IOException e) {
            String errorMsg = "Failed to create temporary directories: " + e.getMessage();
            logger.severe(errorMsg);
            System.err.println("ERROR: " + errorMsg);
            return;
        }
        
        // Calculate chapter range for visibility
        int bookStart = chapters.get(0).getChapterNumber();
        int bookEnd = chapters.get(chapters.size() - 1).getChapterNumber();
        
        logger.info("=== Processing Single Volume Book: Chapters " + bookStart + " to " + bookEnd + " ===");
        System.out.println("\n=== Processing Single Volume Book: Chapters " + bookStart + " to " + bookEnd + " ===");
        
                // Track successfully processed chapters and failed URLs
        List<ChapterContent> successfullyProcessedChapters = new ArrayList<>();
        List<String> allFailedUrls = new ArrayList<>();
        java.util.Map<Integer, java.util.Set<String>> failedFilenamesByChapter = new java.util.HashMap<>();
        Random random = new Random();
        
        // Track total processing time
        long totalStartTime = System.currentTimeMillis();
        
        // Process each chapter sequentially
        int totalChapters = chapters.size();
        int processedCount = 0;
        for (ChapterInfo chapterInfo : chapters) {
            // Track chapter processing time
            long chapterStartTime = System.currentTimeMillis();
            
            try {
                processedCount++;
                // For single volume, calculate percentage based on total chapters
                int percentage = (int) Math.round((processedCount * 100.0) / totalChapters);
                String msg = "Processing chapter " + chapterInfo.getChapterNumber() + " (" + percentage + "%)";
                logger.info("Processing chapter " + chapterInfo.getChapterNumber() + " (" + percentage + "%)");
                System.out.println(msg);
                
                // Check if chapter already exists
                ChapterContent existingChapter = loadExistingChapter(chapterInfo, tmpHtmlDir, tmpImagesDir);
                if (existingChapter != null) {
                    successfullyProcessedChapters.add(existingChapter);
                    logger.info("Skipped processing chapter " + chapterInfo.getChapterNumber() + " (already exists)");
                    continue; // Skip thinking time and move to next chapter
                }
                
                // Fetch chapter HTML
                String chapterHtml = httpClientService.getHtml(chapterInfo.getFullUrl());
                if (chapterHtml == null) {
                    logger.warning("Failed to fetch chapter " + chapterInfo.getChapterNumber() + " from URL: " + chapterInfo.getFullUrl() + ". Skipping.");
                    continue;
                }
                
                // Check for Cloudflare challenge
                if (httpClientService.isCloudflareChallenge(chapterHtml)) {
                    String errorMsg = "Cloudflare challenge detected while processing chapter " + 
                        chapterInfo.getChapterNumber() + ", stopping further downloads for this book.";
                    logger.severe(errorMsg);
                    System.err.println("ERROR: " + errorMsg);
                    logger.info("Will generate EPUB for " + successfullyProcessedChapters.size() + 
                        " successfully processed chapters only.");
                    break; // Stop processing further chapters
                }
                
                // Extract image URLs
                List<String> imageUrls = HtmlExtractor.extractImageUrls(chapterHtml, bookProps);
                
                if (imageUrls.isEmpty()) {
                    logger.warning("Chapter " + chapterInfo.getChapterNumber() + " contains no images. Skipping.");
                    saveHtmlForDebug(chapterHtml, "debug-chapter-" + chapterInfo.getChapterNumber() + ".html", 
                        "chapter " + chapterInfo.getChapterNumber());
                    continue;
                }
                
                logger.info("Found " + imageUrls.size() + " images in chapter " + chapterInfo.getChapterNumber());
                
                // Download images
                Path chapterImagesDir = tmpImagesDir.resolve(String.valueOf(chapterInfo.getChapterNumber()));
                Files.createDirectories(chapterImagesDir);
                
                // For single volume, use processedCount and totalChapters (local to this method)
                DownloadResult downloadResult = downloadImages(imageUrls, chapterImagesDir, chapterInfo.getChapterNumber(), 
                    processedCount, totalChapters);
                
                List<Path> downloadedImages = downloadResult.downloadedImages;
                allFailedUrls.addAll(downloadResult.failedUrls);
                
                // Track failed filenames for this chapter
                java.util.Set<String> failedFilenames = new java.util.HashSet<>();
                if (!downloadResult.failedUrlToFilename.isEmpty()) {
                    failedFilenames = new java.util.HashSet<>(downloadResult.failedUrlToFilename.values());
                    failedFilenamesByChapter.put(chapterInfo.getChapterNumber(), failedFilenames);
                }
                
                if (downloadedImages.isEmpty()) {
                    logger.warning("Chapter " + chapterInfo.getChapterNumber() + 
                        " has no successfully downloaded images, skipping this chapter.");
                    // Still apply thinking time before next chapter
                    if (chapters.indexOf(chapterInfo) < chapters.size() - 1) {
                        long jitter = 500 + random.nextInt(1001); // 500-1500 ms
                        long totalWait = thinkingTimeMs + jitter;
                        logger.info("Waiting " + totalWait + " ms before next chapter");
                        Thread.sleep(totalWait);
                    }
                    continue;
                }
                
                // Create chapter HTML
                String chapterTitle = TemplateEngine.applyChapterTitleTemplate(
                    bookProps.getProperty("chapter.title.template"), 
                    chapterInfo.getChapterNumber(), 
                    bookProps);
                
                Path chapterHtmlFile = createChapterHtml(chapterTitle, downloadedImages, 
                    chapterInfo.getChapterNumber(), tmpHtmlDir, failedFilenames);
                
                ChapterContent chapterContent = new ChapterContent(chapterInfo, downloadedImages, chapterHtmlFile);
                successfullyProcessedChapters.add(chapterContent);
                
                // Calculate and log chapter duration
                long chapterDuration = System.currentTimeMillis() - chapterStartTime;
                String durationStr = formatDuration(chapterDuration);
                logger.info("Completed chapter " + chapterInfo.getChapterNumber() + " (duration: " + durationStr + ")");
                
                // Thinking time with random jitter (AFTER all images are downloaded, BEFORE next chapter)
                if (chapters.indexOf(chapterInfo) < chapters.size() - 1) {
                    long jitter = 500 + random.nextInt(1001); // 500-1500 ms
                    long totalWait = thinkingTimeMs + jitter;
                    logger.info("Waiting " + totalWait + " ms before next chapter");
                    Thread.sleep(totalWait);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                String errorMsg = "Interrupted during chapter processing";
                logger.severe(errorMsg);
                System.err.println("ERROR: " + errorMsg);
                break;
            } catch (Exception e) {
                String errorMsg = "Error processing chapter " + chapterInfo.getChapterNumber() + ": " + e.getMessage();
                logger.log(Level.SEVERE, errorMsg, e);
                System.err.println("ERROR: " + errorMsg);
            }
        }
        
        // Log total processing duration
        long totalDuration = System.currentTimeMillis() - totalStartTime;
        String totalDurationStr = formatDuration(totalDuration);
        logger.info("Total processing duration: " + totalDurationStr);
        
        // Check if we have any successfully processed chapters
        if (successfullyProcessedChapters.isEmpty()) {
            logger.warning("No chapters were successfully processed, skipping EPUB generation for this book.");
            cleanup(tmpImagesDir, tmpHtmlDir);
            return;
        }
        
        // Calculate effective chapter range
        int effectiveStart = successfullyProcessedChapters.stream()
            .mapToInt(c -> c.getInfo().getChapterNumber())
            .min()
            .orElse(0);
        int effectiveEnd = successfullyProcessedChapters.stream()
            .mapToInt(c -> c.getInfo().getChapterNumber())
            .max()
            .orElse(0);
        
        // Adjust book title template with effective range
        Properties adjustedProps = new Properties(bookProps);
        adjustedProps.setProperty("chapter.start", String.valueOf(effectiveStart));
        adjustedProps.setProperty("chapter.end", String.valueOf(effectiveEnd));
        
        // Generate title for PDF display (using book.title.template)
        String adjustedTitle = TemplateEngine.applyBookTitleTemplate(
            bookProps.getProperty("book.title.template"), adjustedProps);
        
        // Generate filename for saving (using book.filename.template, fallback to book.title.template)
        String filenameTemplate = bookProps.getProperty("book.filename.template");
        if (filenameTemplate == null || filenameTemplate.isEmpty()) {
            filenameTemplate = bookProps.getProperty("book.title.template", "");
        }
        String adjustedFilename = TemplateEngine.applyBookTitleTemplate(filenameTemplate, adjustedProps);
        
        // Add -INCOMPLETE suffix if there are failed image downloads
        if (!allFailedUrls.isEmpty()) {
            adjustedFilename = adjustedFilename + " - INCOMPLETE";
            logger.warning("Single volume book has " + allFailedUrls.size() + " failed image downloads, adding -INCOMPLETE suffix");
            incompleteBooks.add(new IncompleteBook(adjustedFilename, 1, allFailedUrls));
        }
        
        // Add all successfully processed chapters
        if (outputFormat.equals("pdf")) {
            PdfBuilderService pdfBuilder = new PdfBuilderService(adjustedTitle, logger, adjustedProps);
            
            // Collect all chapter titles first for TOC
            List<String> chapterTitles = new ArrayList<>();
            for (ChapterContent chapterContent : successfullyProcessedChapters) {
                String chapterTitle = TemplateEngine.applyChapterTitleTemplate(
                    bookProps.getProperty("chapter.title.template"), 
                    chapterContent.getInfo().getChapterNumber(), 
                    bookProps);
                chapterTitles.add(chapterTitle);
            }
            
            // Add title page with TOC first
            pdfBuilder.addTitlePage(chapterTitles);
            
            // Then add all chapters
            for (int i = 0; i < successfullyProcessedChapters.size(); i++) {
                ChapterContent chapterContent = successfullyProcessedChapters.get(i);
                int chapterNumber = chapterContent.getInfo().getChapterNumber();
                java.util.Set<String> failedFilenames = failedFilenamesByChapter.getOrDefault(chapterNumber, new java.util.HashSet<>());
                pdfBuilder.addChapter(chapterContent, chapterTitles.get(i), failedFilenames);
            }
            
            // Save PDF (use filename template for the file, title template is already used in PDF)
            try {
                Path pdfFile = pdfBuilder.saveTo(outputDir, adjustedFilename);
                logger.info("PDF created: " + pdfFile.toAbsolutePath());
                logger.info("Processed " + successfullyProcessedChapters.size() + " chapters (range: " + 
                    effectiveStart + " to " + effectiveEnd + ")");
            } catch (IOException e) {
                logger.severe("Failed to save PDF: " + e.getMessage());
            }
        } else {
            // EPUB format
            EpubBuilderService epubBuilder = new EpubBuilderService(adjustedTitle, logger, adjustedProps);
            for (ChapterContent chapterContent : successfullyProcessedChapters) {
                String chapterTitle = TemplateEngine.applyChapterTitleTemplate(
                    bookProps.getProperty("chapter.title.template"), 
                    chapterContent.getInfo().getChapterNumber(), 
                    bookProps);
                int chapterNumber = chapterContent.getInfo().getChapterNumber();
                java.util.Set<String> failedFilenames = failedFilenamesByChapter.getOrDefault(chapterNumber, new java.util.HashSet<>());
                epubBuilder.addChapter(chapterContent, chapterTitle, failedFilenames);
            }
            
            // Save EPUB (use filename template for the file, title template is already used in EPUB)
            try {
                Path epubFile = epubBuilder.saveTo(outputDir, adjustedFilename);
                logger.info("EPUB created: " + epubFile.toAbsolutePath());
                logger.info("Processed " + successfullyProcessedChapters.size() + " chapters (range: " + 
                    effectiveStart + " to " + effectiveEnd + ")");
            } catch (IOException e) {
                logger.severe("Failed to save EPUB: " + e.getMessage());
            }
        }
        
        // Cleanup
        cleanup(tmpImagesDir, tmpHtmlDir);
        
        // Report incomplete books if any
        if (!incompleteBooks.isEmpty()) {
            logger.warning("=== INCOMPLETE BOOKS REPORT ===");
            logger.warning("Total incomplete books: " + incompleteBooks.size());
            System.out.println("\n=== INCOMPLETE BOOKS REPORT ===");
            System.out.println("Total incomplete books: " + incompleteBooks.size());
            
            for (IncompleteBook incomplete : incompleteBooks) {
                logger.warning("Book " + incomplete.bookIndex + ": " + incomplete.bookFilename);
                logger.warning("  Failed image URLs (" + incomplete.failedUrls.size() + "):");
                System.out.println("\nBook " + incomplete.bookIndex + ": " + incomplete.bookFilename);
                System.out.println("  Failed image URLs (" + incomplete.failedUrls.size() + "):");
                for (String failedUrl : incomplete.failedUrls) {
                    logger.warning("    - " + failedUrl);
                    System.out.println("    - " + failedUrl);
                }
            }
            
            logger.warning("=== END INCOMPLETE BOOKS REPORT ===");
            System.out.println("\n=== END INCOMPLETE BOOKS REPORT ===");
        } else {
            logger.info("Book was generated successfully with all images downloaded.");
            System.out.println("Book was generated successfully with all images downloaded.");
        }
    }

    /**
     * Result of an image download task, preserving the original index for ordering.
     */
    private static class ImageDownloadResult {
        final int originalIndex;
        final Path imageFile;
        final boolean success;
        final String imageUrl; // Store URL for failed downloads
        
        ImageDownloadResult(int originalIndex, Path imageFile, boolean success, String imageUrl) {
            this.originalIndex = originalIndex;
            this.imageFile = imageFile;
            this.success = success;
            this.imageUrl = imageUrl;
        }
    }
    
    /**
     * Task for downloading a single image with caching and error handling.
     */
    private class ImageDownloadTask implements Callable<ImageDownloadResult> {
        private final int index;
        private final String imageUrl;
        private final Path chapterImagesDir;
        private final int chapterNumber;
        private final int totalImages;
        private final int currentChapterIndex;
        private final int totalChapters;
        private final Map<String, Integer> filenameCounters;
        
        ImageDownloadTask(int index, String imageUrl, Path chapterImagesDir, int chapterNumber,
                         int totalImages, int currentChapterIndex, int totalChapters,
                         Map<String, Integer> filenameCounters) {
            this.index = index;
            this.imageUrl = imageUrl;
            this.chapterImagesDir = chapterImagesDir;
            this.chapterNumber = chapterNumber;
            this.totalImages = totalImages;
            this.currentChapterIndex = currentChapterIndex;
            this.totalChapters = totalChapters;
            this.filenameCounters = filenameCounters;
        }
        
        @Override
        public ImageDownloadResult call() {
            long taskStartTime = System.currentTimeMillis();
            try {
                logger.finest("ImageDownloadTask[" + index + "] starting: " + imageUrl);
                
                // Generate filename (synchronized to avoid conflicts)
                String filename;
                synchronized (filenameCounters) {
                    filename = generateImageFilename(imageUrl, index, filenameCounters);
                }
                Path imageFile = chapterImagesDir.resolve(filename);
                
                logger.finest("ImageDownloadTask[" + index + "] generated filename: " + filename);
                
                // Print progress
                System.out.println("  Downloading image " + (index + 1) + "/" + totalImages + 
                    " of chapter " + chapterNumber);
                
                // Check if file already exists (caching)
                if (Files.exists(imageFile)) {
                    logger.finest("ImageDownloadTask[" + index + "] file already exists, skipping download");
                    logger.info("Skipped existing image: " + filename + " from " + imageUrl);
                    return new ImageDownloadResult(index, imageFile, true, imageUrl);
                }
                
                // Encode URL to handle spaces and special characters
                String encodedImageUrl = encodeUrl(imageUrl);
                logger.finest("ImageDownloadTask[" + index + "] encoded URL: " + encodedImageUrl);
                
                // Download image
                logger.finest("ImageDownloadTask[" + index + "] calling downloadBinary()");
                long downloadStartTime = System.currentTimeMillis();
                byte[] imageData = httpClientService.downloadBinary(encodedImageUrl);
                long downloadDuration = System.currentTimeMillis() - downloadStartTime;
                
                if (imageData == null) {
                    logger.warning("Failed to download image " + (index + 1) + "/" + totalImages + 
                        " from " + imageUrl + " (encoded: " + encodedImageUrl + ") after " + downloadDuration + "ms");
                    return new ImageDownloadResult(index, null, false, imageUrl);
                }
                
                logger.finest("ImageDownloadTask[" + index + "] downloaded " + imageData.length + 
                    " bytes in " + downloadDuration + "ms");
                
                // Convert WebP to JPEG if necessary
                byte[] processedImageData = convertWebPToJpeg(imageData);
                String finalFilename = updateFilenameForJpeg(filename);
                Path finalImageFile = chapterImagesDir.resolve(finalFilename);
                
                // Write file (synchronized on filenameCounters to avoid conflicts)
                // Note: Since filename generation is synchronized and we check file existence,
                // this is mainly a safety measure for concurrent writes
                long writeStartTime = System.currentTimeMillis();
                synchronized (filenameCounters) {
                    // Double-check file doesn't exist (another thread might have created it)
                    if (!Files.exists(finalImageFile)) {
                        Files.write(finalImageFile, processedImageData);
                        logger.finest("ImageDownloadTask[" + index + "] wrote file in " + 
                            (System.currentTimeMillis() - writeStartTime) + "ms");
                    } else {
                        logger.finest("ImageDownloadTask[" + index + "] file was created by another thread, skipping write");
                        logger.info("Skipped writing image (already exists): " + finalFilename + " from " + imageUrl);
                    }
                }
                
                long totalDuration = System.currentTimeMillis() - taskStartTime;
                logger.finest("ImageDownloadTask[" + index + "] completed successfully in " + totalDuration + "ms");
                logger.info("Downloaded image " + (index + 1) + "/" + totalImages + ": " + finalFilename + 
                    " from " + imageUrl + " (duration: " + totalDuration + "ms)");
                return new ImageDownloadResult(index, finalImageFile, true, imageUrl);
                
            } catch (Exception e) {
                long totalDuration = System.currentTimeMillis() - taskStartTime;
                logger.log(Level.WARNING, "Error downloading image " + imageUrl + " after " + totalDuration + "ms: " + e.getMessage(), e);
                return new ImageDownloadResult(index, null, false, imageUrl);
            }
        }
    }
    
    /**
     * Downloads images and returns both successful downloads and failed URLs/filenames.
     * 
     * @param imageUrls List of image URLs to download
     * @param chapterImagesDir Directory to save images
     * @param chapterNumber Chapter number
     * @param currentChapterIndex Current chapter index
     * @param totalChapters Total number of chapters
     * @return A DownloadResult containing successful downloads and failed URLs/filenames
     */
    private static class DownloadResult {
        final List<Path> downloadedImages;
        final List<String> failedUrls;
        final java.util.Map<String, String> failedUrlToFilename; // Map failed URL to expected filename
        
        DownloadResult(List<Path> downloadedImages, List<String> failedUrls, java.util.Map<String, String> failedUrlToFilename) {
            this.downloadedImages = downloadedImages;
            this.failedUrls = failedUrls;
            this.failedUrlToFilename = failedUrlToFilename;
        }
    }
    
    private DownloadResult downloadImages(List<String> imageUrls, Path chapterImagesDir, int chapterNumber, 
            int currentChapterIndex, int totalChapters) {
        int totalImages = imageUrls.size();
        if (totalImages == 0) {
            logger.finest("downloadImages: No images to download for chapter " + chapterNumber);
            return new DownloadResult(new ArrayList<>(), new ArrayList<>(), new java.util.HashMap<>());
        }
        
        logger.finest("downloadImages: Starting download of " + totalImages + " images for chapter " + 
            chapterNumber + " using " + maxConcurrentImageDownloads + " concurrent downloads");
        long downloadStartTime = System.currentTimeMillis();
        
        // Create thread pool for this chapter
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrentImageDownloads);
        List<Future<ImageDownloadResult>> futures = new ArrayList<>();
        Map<String, Integer> filenameCounters = new ConcurrentHashMap<>();
        
        try {
            // Submit all download tasks
            logger.finest("downloadImages: Submitting " + totalImages + " download tasks to thread pool");
            for (int i = 0; i < imageUrls.size(); i++) {
                ImageDownloadTask task = new ImageDownloadTask(
                    i, imageUrls.get(i), chapterImagesDir, chapterNumber,
                    totalImages, currentChapterIndex, totalChapters, filenameCounters);
                futures.add(executor.submit(task));
                logger.finest("downloadImages: Submitted task " + (i + 1) + "/" + totalImages + " for URL: " + imageUrls.get(i));
            }
            
            // Wait for all tasks to complete and collect results
            // Use timeout to prevent infinite blocking (60s per image)
            long timeoutPerImage = 60; // seconds
            List<ImageDownloadResult> results = new ArrayList<>();
            List<String> failedUrls = new ArrayList<>();
            java.util.Map<String, String> failedUrlToFilename = new java.util.HashMap<>();
            
            for (int i = 0; i < futures.size(); i++) {
                Future<ImageDownloadResult> future = futures.get(i);
                String imageUrl = imageUrls.get(i);
                boolean success = false;
                
                try {
                    logger.finest("Waiting for image download " + (i + 1) + "/" + totalImages + 
                        " with timeout of " + timeoutPerImage + "s: " + imageUrl);
                    long waitStartTime = System.currentTimeMillis();
                    ImageDownloadResult result = future.get(timeoutPerImage, TimeUnit.SECONDS);
                    long waitDuration = System.currentTimeMillis() - waitStartTime;
                    logger.finest("Image download " + (i + 1) + "/" + totalImages + " completed after waiting " + 
                        waitDuration + "ms");
                    results.add(result);
                    if (result.success) {
                        success = true;
                    }
                } catch (java.util.concurrent.TimeoutException e) {
                    logger.severe("Image download " + (i + 1) + "/" + totalImages + 
                        " timed out after " + timeoutPerImage + "s: " + imageUrl);
                    logger.severe("This may indicate a network issue or the server is not responding");
                    // Cancel the future to free resources
                    future.cancel(true);
                    // Generate expected filename for failed download
                    String expectedFilename = generateImageFilename(imageUrl, i, filenameCounters);
                    failedUrlToFilename.put(imageUrl, expectedFilename);
                    results.add(new ImageDownloadResult(i, null, false, imageUrl));
                } catch (ExecutionException e) {
                    logger.severe("Image download task failed: " + e.getCause().getMessage() + " for URL: " + imageUrl);
                    if (e.getCause() != null) {
                        logger.log(Level.SEVERE, "Exception cause:", e.getCause());
                    }
                    // Generate expected filename for failed download
                    String expectedFilename = generateImageFilename(imageUrl, i, filenameCounters);
                    failedUrlToFilename.put(imageUrl, expectedFilename);
                    results.add(new ImageDownloadResult(i, null, false, imageUrl));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.severe("Interrupted while waiting for image downloads");
                    // Cancel remaining futures
                    for (Future<ImageDownloadResult> f : futures.subList(i, futures.size())) {
                        f.cancel(true);
                    }
                    break;
                }
                
                // If download failed, wait 5 minutes and retry once
                if (!success) {
                    failedUrls.add(imageUrl);
                    logger.warning("Image download failed, will retry after 5 minutes: " + imageUrl);
                    System.err.println("WARNING: Image download failed for chapter " + chapterNumber + 
                        ", waiting 5 minutes before retry: " + imageUrl);
                    
                    try {
                        logger.info("Waiting 5 minutes before retrying failed image download...");
                        System.err.println("Waiting 5 minutes before retrying failed image download...");
                        Thread.sleep(5 * 60 * 1000); // 5 minutes
                        
                        // Retry the download
                        logger.info("Retrying image download after 5 minute wait: " + imageUrl);
                        System.err.println("Retrying image download: " + imageUrl);
                        byte[] imageData = httpClientService.downloadBinary(encodeUrl(imageUrl));
                        
                        if (imageData != null) {
                            // Convert WebP to JPEG if necessary
                            byte[] processedImageData = convertWebPToJpeg(imageData);
                            
                            // Generate filename
                            String filename;
                            synchronized (filenameCounters) {
                                filename = generateImageFilename(imageUrl, i, filenameCounters);
                            }
                            String finalFilename = updateFilenameForJpeg(filename);
                            Path imageFile = chapterImagesDir.resolve(finalFilename);
                            
                            // Write file
                            synchronized (filenameCounters) {
                                if (!Files.exists(imageFile)) {
                                    Files.write(imageFile, processedImageData);
                                    logger.info("Successfully downloaded image on retry: " + finalFilename + " from " + imageUrl);
                                    System.err.println("SUCCESS: Image downloaded on retry: " + imageUrl);
                                    // Update result
                                    results.set(i, new ImageDownloadResult(i, imageFile, true, imageUrl));
                                    failedUrls.remove(imageUrl);
                                } else {
                                    logger.info("Image file already exists on retry: " + finalFilename);
                                    results.set(i, new ImageDownloadResult(i, imageFile, true, imageUrl));
                                    failedUrls.remove(imageUrl);
                                }
                            }
                        } else {
                            logger.severe("Retry also failed for image: " + imageUrl);
                            System.err.println("ERROR: Retry also failed for image: " + imageUrl);
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.severe("Interrupted during 5-minute wait for retry");
                        System.err.println("ERROR: Interrupted during 5-minute wait for retry");
                    } catch (Exception retryException) {
                        logger.severe("Exception during retry: " + retryException.getMessage());
                        System.err.println("ERROR: Exception during retry: " + retryException.getMessage());
                    }
                }
            }
            
            // Sort results by original index to preserve order
            results.sort(Comparator.comparingInt(r -> r.originalIndex));
            
            // Extract successfully downloaded images in order and collect final failed URLs
            List<Path> downloadedImages = new ArrayList<>();
            List<String> finalFailedUrls = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;
            for (ImageDownloadResult result : results) {
                if (result.success && result.imageFile != null) {
                    downloadedImages.add(result.imageFile);
                    successCount++;
                } else {
                    failureCount++;
                    if (result.imageUrl != null) {
                        finalFailedUrls.add(result.imageUrl);
                    }
                }
            }
            
            long totalDuration = System.currentTimeMillis() - downloadStartTime;
            logger.finest("downloadImages: Completed chapter " + chapterNumber + 
                " - Success: " + successCount + ", Failed: " + failureCount + 
                ", Total time: " + totalDuration + "ms");
            logger.info("Downloaded " + successCount + "/" + totalImages + " images for chapter " + 
                chapterNumber + " in " + totalDuration + "ms");
            
            if (!finalFailedUrls.isEmpty()) {
                logger.warning("Failed to download " + finalFailedUrls.size() + " images for chapter " + chapterNumber);
                for (String failedUrl : finalFailedUrls) {
                    logger.warning("  Failed URL: " + failedUrl);
                }
            }
            
            return new DownloadResult(downloadedImages, finalFailedUrls, failedUrlToFilename);
            
        } finally {
            // Shutdown executor
            logger.finest("downloadImages: Shutting down executor for chapter " + chapterNumber);
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.warning("downloadImages: Executor did not terminate within 60s, forcing shutdown");
                    executor.shutdownNow();
                } else {
                    logger.finest("downloadImages: Executor terminated successfully");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                logger.warning("downloadImages: Interrupted while waiting for executor shutdown");
            }
        }
    }

    private String generateImageFilename(String imageUrl, int index, Map<String, Integer> filenameCounters) {
        try {
            URL url = new URL(imageUrl);
            String path = url.getPath();
            String filename = Paths.get(path).getFileName().toString();
            
            // If filename is empty or invalid, use index
            if (filename == null || filename.isEmpty() || !filename.contains(".")) {
                filename = "image_" + index + ".jpg";
            }
            
            // Check for duplicate filenames
            String baseFilename = filename;
            int counter = filenameCounters.getOrDefault(baseFilename, 0);
            if (counter > 0 || Files.exists(Paths.get("").resolve(filename))) {
                // Add counter suffix
                int dotIndex = filename.lastIndexOf('.');
                if (dotIndex > 0) {
                    String name = filename.substring(0, dotIndex);
                    String ext = filename.substring(dotIndex);
                    filename = name + "_" + counter + ext;
                } else {
                    filename = filename + "_" + counter;
                }
            }
            filenameCounters.put(baseFilename, counter + 1);
            
            return filename;
        } catch (Exception e) {
            return "image_" + index + ".jpg";
        }
    }

    private Path createChapterHtml(String chapterTitle, List<Path> imageFiles, int chapterNumber, Path tmpHtmlDir,
            java.util.Set<String> failedFilenames) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        html.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n");
        html.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        html.append("<head><title>").append(chapterTitle).append("</title></head>\n");
        html.append("<body>\n");
        html.append("<h1>").append(chapterTitle).append("</h1>\n");
        
        // Collect all image filenames (downloaded + failed) to maintain order
        java.util.Map<String, Path> imageFileMap = new java.util.HashMap<>();
        for (Path imageFile : imageFiles) {
            imageFileMap.put(imageFile.getFileName().toString(), imageFile);
        }
        
        // Combine downloaded and failed filenames, sort by numeric value
        java.util.Set<String> allFilenames = new java.util.HashSet<>();
        allFilenames.addAll(imageFileMap.keySet());
        if (failedFilenames != null) {
            allFilenames.addAll(failedFilenames);
        }
        
        java.util.List<String> sortedFilenames = new java.util.ArrayList<>(allFilenames);
        sortedFilenames.sort((a, b) -> {
            try {
                int dotIndexA = a.lastIndexOf('.');
                int dotIndexB = b.lastIndexOf('.');
                String numPartA = dotIndexA > 0 ? a.substring(0, dotIndexA) : a;
                String numPartB = dotIndexB > 0 ? b.substring(0, dotIndexB) : b;
                return Integer.compare(Integer.parseInt(numPartA), Integer.parseInt(numPartB));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });
        
        // Add images and placeholders in order
        for (String filename : sortedFilenames) {
            if (imageFileMap.containsKey(filename)) {
                // Successfully downloaded image
                html.append("<img src=\"images/").append(chapterNumber).append("/").append(filename)
                    .append("\" style=\"width:100%;max-width:100%;\" />\n");
            } else if (failedFilenames != null && failedFilenames.contains(filename)) {
                // Failed to download - add placeholder
                html.append("<p style=\"text-align:center;color:#666;padding:20px;\">[Image could not be downloaded: ")
                    .append(filename).append("]</p>\n");
            }
        }
        
        html.append("</body>\n");
        html.append("</html>\n");
        
        Path chapterHtmlFile = tmpHtmlDir.resolve("chapter-" + chapterNumber + ".xhtml");
        Files.write(chapterHtmlFile, html.toString().getBytes("UTF-8"));
        
        return chapterHtmlFile;
    }

    /**
     * Saves HTML content to a debug file in the output directory for problem analysis.
     * 
     * @param html The HTML content to save
     * @param filename The filename to use (e.g., "debug-starting-page.html")
     * @param description Description of what the HTML represents (for logging)
     */
    private void saveHtmlForDebug(String html, String filename, String description) {
        if (html == null || html.isEmpty()) {
            logger.warning("Cannot save debug HTML for " + description + ": HTML content is null or empty");
            return;
        }
        
        try {
            // Ensure output directory exists
            Files.createDirectories(outputDir);
            
            // Create debug file path
            Path debugFile = outputDir.resolve(filename);
            
            // Write HTML content
            Files.write(debugFile, html.getBytes("UTF-8"));
            
            logger.info("Saved HTML debug file for " + description + ": " + debugFile.toAbsolutePath());
        } catch (IOException e) {
            logger.warning("Failed to save HTML debug file for " + description + ": " + e.getMessage());
        }
    }

    /**
     * Formats a duration in milliseconds to a human-readable string.
     * Only displays non-zero units (hours, minutes, seconds, milliseconds).
     * 
     * @param durationMs Duration in milliseconds
     * @return Formatted string (e.g., "1h 23m 45s 123ms" or "45s 123ms" or "123ms")
     */
    private String formatDuration(long durationMs) {
        if (durationMs < 0) {
            return "0ms";
        }
        
        long totalMs = durationMs;
        long hours = totalMs / 3_600_000;
        long minutes = (totalMs % 3_600_000) / 60_000;
        long seconds = (totalMs % 60_000) / 1_000;
        long milliseconds = totalMs % 1_000;
        
        StringBuilder sb = new StringBuilder();
        
        if (hours > 0) {
            sb.append(hours).append("h");
            if (minutes > 0 || seconds > 0 || milliseconds > 0) {
                sb.append(" ");
            }
        }
        
        if (hours > 0 || minutes > 0) {
            if (minutes > 0) {
                sb.append(minutes).append("m");
                if (seconds > 0 || milliseconds > 0) {
                    sb.append(" ");
                }
            }
        }
        
        if (hours > 0 || minutes > 0 || seconds > 0) {
            if (seconds > 0) {
                sb.append(seconds).append("s");
                if (milliseconds > 0) {
                    sb.append(" ");
                }
            }
        }
        
        if (milliseconds > 0 || sb.length() == 0) {
            sb.append(milliseconds).append("ms");
        }
        
        return sb.toString();
    }
    
    /**
     * Checks if a book file already exists for the given chapter range.
     * 
     * @param chapterStart The starting chapter number
     * @param chapterEnd The ending chapter number
     * @return true if the book file exists, false otherwise
     */
    private boolean bookFileExists(int chapterStart, int chapterEnd) {
        // Create adjusted properties for this book's chapter range
        Properties adjustedProps = new Properties(bookProps);
        adjustedProps.setProperty("chapter.start", String.valueOf(chapterStart));
        adjustedProps.setProperty("chapter.end", String.valueOf(chapterEnd));
        
        // Generate filename for saving (using book.filename.template, fallback to book.title.template)
        String filenameTemplate = bookProps.getProperty("book.filename.template");
        if (filenameTemplate == null || filenameTemplate.isEmpty()) {
            filenameTemplate = bookProps.getProperty("book.title.template", "");
        }
        String bookFilename = TemplateEngine.applyBookTitleTemplate(filenameTemplate, adjustedProps);
        
        // Sanitize filename
        String sanitizedFilename = LoggerFactory.sanitizeFilename(bookFilename);
        
        // Add extension based on output format
        String extension = outputFormat.equals("pdf") ? ".pdf" : ".epub";
        String fullFilename = sanitizedFilename + extension;
        
        // Check if file exists
        Path bookFile = outputDir.resolve(fullFilename);
        return Files.exists(bookFile);
    }
    
    private void cleanup(Path tmpImagesDir, Path tmpHtmlDir) {
        // Delete HTML folder if configured
        String deleteXhtml = bookProps.getProperty("delete.xhtml.after.generation", "false");
        if ("true".equalsIgnoreCase(deleteXhtml.trim())) {
            try {
                if (Files.exists(tmpHtmlDir)) {
                    deleteDirectory(tmpHtmlDir);
                    logger.info("Deleted temporary HTML directory");
                }
            } catch (IOException e) {
                logger.warning("Failed to delete temporary HTML directory: " + e.getMessage());
            }
        }
        
        // Delete images folder if configured
        String deleteImages = bookProps.getProperty("delete.images.after.generation", "false");
        if ("true".equalsIgnoreCase(deleteImages.trim())) {
            try {
                if (Files.exists(tmpImagesDir)) {
                    deleteDirectory(tmpImagesDir);
                    logger.info("Deleted temporary images directory");
                }
            } catch (IOException e) {
                logger.warning("Failed to delete temporary images directory: " + e.getMessage());
            }
        }
    }
    
    /**
     * Checks if a chapter's xhtml file already exists and loads it if found.
     * Returns null if the chapter needs to be processed from scratch.
     * 
     * @param chapterInfo The chapter information
     * @param tmpHtmlDir The temporary HTML directory
     * @param tmpImagesDir The temporary images directory
     * @return ChapterContent if xhtml exists, null otherwise
     */
    private ChapterContent loadExistingChapter(ChapterInfo chapterInfo, Path tmpHtmlDir, Path tmpImagesDir) {
        int chapterNumber = chapterInfo.getChapterNumber();
        Path chapterHtmlFile = tmpHtmlDir.resolve("chapter-" + chapterNumber + ".xhtml");
        
        if (!Files.exists(chapterHtmlFile)) {
            return null; // Chapter doesn't exist, needs processing
        }
        
        // Check if images directory exists and has images
        Path chapterImagesDir = tmpImagesDir.resolve(String.valueOf(chapterNumber));
        if (!Files.exists(chapterImagesDir)) {
            logger.warning("Chapter " + chapterNumber + " xhtml exists but images directory is missing. Re-processing chapter.");
            return null;
        }
        
        try {
            // List all image files in the chapter directory
            List<Path> imageFiles = Files.list(chapterImagesDir)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                           name.endsWith(".png") || name.endsWith(".gif") || 
                           name.endsWith(".webp");
                })
                .sorted(Comparator.comparing(p -> {
                    // Sort by numeric value in filename (e.g., "1.jpg", "2.jpg", "10.jpg")
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
                }))
                .collect(Collectors.toList());
            
            if (imageFiles.isEmpty()) {
                logger.warning("Chapter " + chapterNumber + " xhtml exists but no images found. Re-processing chapter.");
                return null;
            }
            
            // Chapter already exists, use it
            logger.info("Chapter " + chapterNumber + " already processed, using existing files");
            return new ChapterContent(chapterInfo, imageFiles, chapterHtmlFile);
            
        } catch (IOException e) {
            logger.warning("Failed to check existing chapter " + chapterNumber + ": " + e.getMessage() + ". Re-processing chapter.");
            return null;
        }
    }

    /**
     * Encodes a URL to handle spaces and special characters.
     * Spaces will be encoded as %20, and other special characters will be properly encoded.
     * 
     * @param url The URL to encode
     * @return The encoded URL
     */
    private String encodeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        try {
            // Check if it's already a full URL
            if (url.startsWith("http://") || url.startsWith("https://")) {
                // Parse URL manually to extract components
                int schemeEnd = url.indexOf("://");
                if (schemeEnd == -1) {
                    return url.replace(" ", "%20"); // Fallback
                }
                
                String scheme = url.substring(0, schemeEnd);
                String rest = url.substring(schemeEnd + 3);
                
                int pathStart = rest.indexOf("/");
                int queryStart = rest.indexOf("?");
                int fragmentStart = rest.indexOf("#");
                
                String authority;
                String path;
                String query = null;
                String fragment = null;
                
                if (pathStart == -1) {
                    // No path
                    authority = rest;
                    path = "";
                } else {
                    authority = rest.substring(0, pathStart);
                    int pathEnd = rest.length();
                    if (queryStart != -1 && queryStart < pathEnd) {
                        pathEnd = queryStart;
                    }
                    if (fragmentStart != -1 && fragmentStart < pathEnd) {
                        pathEnd = fragmentStart;
                    }
                    path = rest.substring(pathStart, pathEnd);
                    
                    if (queryStart != -1) {
                        int queryEnd = fragmentStart != -1 ? fragmentStart : rest.length();
                        query = rest.substring(queryStart + 1, queryEnd);
                    }
                    if (fragmentStart != -1) {
                        fragment = rest.substring(fragmentStart + 1);
                    }
                }
                
                // Encode the path segments if it contains spaces
                if (path != null && path.contains(" ")) {
                    String[] pathParts = path.split("/", -1);
                    StringBuilder encodedPath = new StringBuilder();
                    boolean pathStartsWithSlash = path.startsWith("/");
                    for (int i = 0; i < pathParts.length; i++) {
                        if (i > 0 || (i == 0 && pathStartsWithSlash)) {
                            encodedPath.append("/");
                        }
                        if (!pathParts[i].isEmpty()) {
                            // Encode each path segment, replacing + with %20 for spaces
                            encodedPath.append(URLEncoder.encode(pathParts[i], StandardCharsets.UTF_8)
                                .replace("+", "%20"));
                        }
                    }
                    path = encodedPath.toString();
                }
                
                // Reconstruct the URL
                StringBuilder encodedUrl = new StringBuilder();
                encodedUrl.append(scheme).append("://").append(authority).append(path);
                if (query != null) {
                    encodedUrl.append("?").append(query);
                }
                if (fragment != null) {
                    encodedUrl.append("#").append(fragment);
                }
                return encodedUrl.toString();
            } else {
                // Relative URL - encode the path segments
                String[] parts = url.split("/", -1);
                StringBuilder encoded = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) {
                        encoded.append("/");
                    }
                    if (!parts[i].isEmpty()) {
                        // Encode each path segment, replacing + with %20 for spaces
                        encoded.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8)
                            .replace("+", "%20"));
                    }
                }
                return encoded.toString();
            }
        } catch (Exception e) {
            // If encoding fails, try simple space replacement as fallback
            logger.warning("Failed to encode URL " + url + ": " + e.getMessage() + ". Using simple encoding.");
            return url.replace(" ", "%20");
        }
    }

    /**
     * Converts WebP image data to JPEG format.
     * Uses TwelveMonkeys ImageIO library for WebP support.
     * 
     * @param imageData The original image data (may be WebP or other format)
     * @return Converted JPEG image data, or original data if not WebP or conversion fails
     */
    private byte[] convertWebPToJpeg(byte[] imageData) {
        if (imageData == null || imageData.length < 12) {
            return imageData;
        }
        
        // Check if it's a WebP image (RIFF header: "RIFF" at offset 0, "WEBP" at offset 8)
        boolean isWebP = imageData.length >= 12 &&
            imageData[0] == 'R' && imageData[1] == 'I' && imageData[2] == 'F' && imageData[3] == 'F' &&
            imageData[8] == 'W' && imageData[9] == 'E' && imageData[10] == 'B' && imageData[11] == 'P';
        
        if (!isWebP) {
            return imageData; // Not WebP, return original
        }
        
        try {
            logger.finest("Converting WebP image to JPEG");
            
            // Read WebP image using ImageIO (TwelveMonkeys library provides WebP support)
            ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
            BufferedImage image = ImageIO.read(bais);
            
            if (image == null) {
                logger.warning("Failed to read WebP image, returning original data");
                return imageData;
            }
            
            // Convert to JPEG with quality settings
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // Get JPEG writer
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            if (writer != null) {
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.9f); // High quality JPEG
                }
                
                ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
                writer.dispose();
                ios.close();
            } else {
                // Fallback to simple ImageIO.write
                ImageIO.write(image, "jpg", baos);
            }
            
            byte[] jpegData = baos.toByteArray();
            
            logger.info("Successfully converted WebP image to JPEG (" + imageData.length + 
                " bytes -> " + jpegData.length + " bytes)");
            
            return jpegData;
        } catch (Exception e) {
            logger.warning("Failed to convert WebP to JPEG: " + e.getMessage() + ", using original format");
            logger.log(Level.FINEST, "WebP conversion error details", e);
            return imageData; // Return original on failure
        }
    }

    /**
     * Updates filename extension to .jpg if it was .webp
     * 
     * @param filename Original filename
     * @return Filename with .jpg extension if original was .webp, otherwise unchanged
     */
    private String updateFilenameForJpeg(String filename) {
        if (filename == null) {
            return filename;
        }
        
        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".webp")) {
            return filename.substring(0, filename.length() - 5) + ".jpg";
        }
        
        return filename;
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore individual file deletion errors
                    }
                });
        }
    }
}

