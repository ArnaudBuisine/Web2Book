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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
    private long thinkingTimeMs;
    private String outputFormat;
    private Logger logger;
    private String bookTitle;

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
            
            // Initialize logger
            String loggerName = "web2book." + LoggerFactory.sanitizeFilename(bookTitle);
            logger = LoggerFactory.createFileLogger(loggerName, logDir, bookTitle);
            
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
    }

    private void processVolumes(List<ChapterInfo> chapters, int maxChaptersPerBook) {
        // Process all chapters and track successfully processed ones
        List<ChapterContent> allSuccessfullyProcessed = new ArrayList<>();
        Random random = new Random();
        
        Path tmpImagesDir = outputDir.resolve("tmp").resolve("images");
        Path tmpHtmlDir = outputDir.resolve("tmp").resolve("html");
        
        try {
            Files.createDirectories(tmpImagesDir);
            Files.createDirectories(tmpHtmlDir);
        } catch (IOException e) {
            String errorMsg = "Failed to create temporary directories: " + e.getMessage();
            logger.severe(errorMsg);
            System.err.println("ERROR: " + errorMsg);
            return;
        }
        
        // Process each chapter sequentially
        int totalChapters = chapters.size();
        int processedCount = 0;
        for (ChapterInfo chapterInfo : chapters) {
            try {
                processedCount++;
                int percentage = (int) Math.round((processedCount * 100.0) / totalChapters);
                String msg = "Processing chapter " + chapterInfo.getChapterNumber() + " (" + percentage + "%)";
                logger.info("Processing chapter " + chapterInfo.getChapterNumber());
                System.out.println(msg);
                
                // Check if chapter already exists
                ChapterContent existingChapter = loadExistingChapter(chapterInfo, tmpHtmlDir, tmpImagesDir);
                if (existingChapter != null) {
                    allSuccessfullyProcessed.add(existingChapter);
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
                    logger.info("Will generate EPUB volumes for " + allSuccessfullyProcessed.size() + 
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
                
                List<Path> downloadedImages = downloadImages(imageUrls, chapterImagesDir, chapterInfo.getChapterNumber(), 
                    processedCount, totalChapters);
                
                if (downloadedImages.isEmpty()) {
                    logger.warning("No images downloaded for chapter " + chapterInfo.getChapterNumber() + ". Skipping.");
                    continue;
                }
                
                // Create chapter HTML
                String chapterTitle = TemplateEngine.applyChapterTitleTemplate(
                    bookProps.getProperty("chapter.title.template"), 
                    chapterInfo.getChapterNumber(), 
                    bookProps);
                
                Path chapterHtmlFile = createChapterHtml(chapterTitle, downloadedImages, 
                    chapterInfo.getChapterNumber(), tmpHtmlDir);
                
                ChapterContent chapterContent = new ChapterContent(chapterInfo, downloadedImages, chapterHtmlFile);
                allSuccessfullyProcessed.add(chapterContent);
                
                logger.info("Completed chapter " + chapterInfo.getChapterNumber());
                
                // Thinking time with random jitter
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
        
        // Check if we have any successfully processed chapters
        if (allSuccessfullyProcessed.isEmpty()) {
            logger.warning("No chapters were successfully processed, skipping EPUB generation for this book.");
            cleanup(tmpImagesDir, tmpHtmlDir);
            return;
        }
        
        // Split into volumes
        int totalProcessed = allSuccessfullyProcessed.size();
        int volumeCount = (int) Math.ceil((double) totalProcessed / maxChaptersPerBook);
        
        logger.info("Splitting " + totalProcessed + " successfully processed chapters into " + 
            volumeCount + " volumes (max " + maxChaptersPerBook + " chapters per volume)");
        
        for (int volumeIndex = 0; volumeIndex < volumeCount; volumeIndex++) {
            int startIdx = volumeIndex * maxChaptersPerBook;
            int endIdx = Math.min(startIdx + maxChaptersPerBook, allSuccessfullyProcessed.size());
            List<ChapterContent> volumeChapters = allSuccessfullyProcessed.subList(startIdx, endIdx);
            
            if (volumeChapters.isEmpty()) {
                continue; // Skip empty volumes
            }
            
            // Calculate effective chapter range for this volume
            int effectiveStart = volumeChapters.stream()
                .mapToInt(c -> c.getInfo().getChapterNumber())
                .min()
                .orElse(0);
            int effectiveEnd = volumeChapters.stream()
                .mapToInt(c -> c.getInfo().getChapterNumber())
                .max()
                .orElse(0);
            
            // Adjust book title template with effective range
            Properties adjustedProps = new Properties(bookProps);
            adjustedProps.setProperty("chapter.start", String.valueOf(effectiveStart));
            adjustedProps.setProperty("chapter.end", String.valueOf(effectiveEnd));
            
            // Generate title for PDF display (using book.title.template)
            String volumeTitle = TemplateEngine.applyBookTitleTemplate(
                bookProps.getProperty("book.title.template"), adjustedProps);
            
            // Generate filename for saving (using book.filename.template, fallback to book.title.template)
            String filenameTemplate = bookProps.getProperty("book.filename.template");
            if (filenameTemplate == null || filenameTemplate.isEmpty()) {
                filenameTemplate = bookProps.getProperty("book.title.template", "");
            }
            String volumeFilename = TemplateEngine.applyBookTitleTemplate(filenameTemplate, adjustedProps);
            logger.info("Creating volume " + (volumeIndex + 1) + "/" + volumeCount + 
                ": Chapters " + effectiveStart + " to " + effectiveEnd);
            
            // Add chapters to this volume
            if (outputFormat.equals("pdf")) {
                PdfBuilderService pdfBuilder = new PdfBuilderService(volumeTitle, logger, adjustedProps);
                
                // Collect all chapter titles first for TOC
                List<String> chapterTitles = new ArrayList<>();
                for (ChapterContent chapterContent : volumeChapters) {
                    String chapterTitle = TemplateEngine.applyChapterTitleTemplate(
                        bookProps.getProperty("chapter.title.template"), 
                        chapterContent.getInfo().getChapterNumber(), 
                        bookProps);
                    chapterTitles.add(chapterTitle);
                }
                
                // Add title page with TOC first
                pdfBuilder.addTitlePage(chapterTitles);
                
                // Then add all chapters
                for (int i = 0; i < volumeChapters.size(); i++) {
                    ChapterContent chapterContent = volumeChapters.get(i);
                    pdfBuilder.addChapter(chapterContent, chapterTitles.get(i));
                }
                
                // Save PDF (use filename template for the file, title template is already used in PDF)
                try {
                    Path pdfFile = pdfBuilder.saveTo(outputDir, volumeFilename);
                    logger.info("PDF created: " + pdfFile.toAbsolutePath());
                    logger.info("Volume " + (volumeIndex + 1) + " contains " + volumeChapters.size() + 
                        " chapters (range: " + effectiveStart + " to " + effectiveEnd + ")");
                } catch (IOException e) {
                    logger.severe("Failed to save PDF volume " + (volumeIndex + 1) + ": " + e.getMessage());
                }
            } else {
                // EPUB format
                EpubBuilderService epubBuilder = new EpubBuilderService(volumeTitle, logger);
                for (ChapterContent chapterContent : volumeChapters) {
                    String chapterTitle = TemplateEngine.applyChapterTitleTemplate(
                        bookProps.getProperty("chapter.title.template"), 
                        chapterContent.getInfo().getChapterNumber(), 
                        bookProps);
                    epubBuilder.addChapter(chapterContent, chapterTitle);
                }
                
                // Save EPUB (use filename template for the file, title template is already used in EPUB)
                try {
                    Path epubFile = epubBuilder.saveTo(outputDir, volumeFilename);
                    logger.info("EPUB created: " + epubFile.toAbsolutePath());
                    logger.info("Volume " + (volumeIndex + 1) + " contains " + volumeChapters.size() + 
                        " chapters (range: " + effectiveStart + " to " + effectiveEnd + ")");
                } catch (IOException e) {
                    logger.severe("Failed to save EPUB volume " + (volumeIndex + 1) + ": " + e.getMessage());
                }
            }
        }
        
        // Cleanup
        cleanup(tmpImagesDir, tmpHtmlDir);
    }

    private void processSingleVolume(List<ChapterInfo> chapters, String volumeTitle) {
        Path tmpImagesDir = outputDir.resolve("tmp").resolve("images");
        Path tmpHtmlDir = outputDir.resolve("tmp").resolve("html");
        
        try {
            Files.createDirectories(tmpImagesDir);
            Files.createDirectories(tmpHtmlDir);
        } catch (IOException e) {
            String errorMsg = "Failed to create temporary directories: " + e.getMessage();
            logger.severe(errorMsg);
            System.err.println("ERROR: " + errorMsg);
            return;
        }
        
        // Track successfully processed chapters
        List<ChapterContent> successfullyProcessedChapters = new ArrayList<>();
        Random random = new Random();
        
        // Process each chapter sequentially
        int totalChapters = chapters.size();
        int processedCount = 0;
        for (ChapterInfo chapterInfo : chapters) {
            try {
                processedCount++;
                int percentage = (int) Math.round((processedCount * 100.0) / totalChapters);
                String msg = "Processing chapter " + chapterInfo.getChapterNumber() + " (" + percentage + "%)";
                logger.info("Processing chapter " + chapterInfo.getChapterNumber());
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
                
                List<Path> downloadedImages = downloadImages(imageUrls, chapterImagesDir, chapterInfo.getChapterNumber(), 
                    processedCount, totalChapters);
                
                if (downloadedImages.isEmpty()) {
                    logger.warning("No images downloaded for chapter " + chapterInfo.getChapterNumber() + ". Skipping.");
                    continue;
                }
                
                // Create chapter HTML
                String chapterTitle = TemplateEngine.applyChapterTitleTemplate(
                    bookProps.getProperty("chapter.title.template"), 
                    chapterInfo.getChapterNumber(), 
                    bookProps);
                
                Path chapterHtmlFile = createChapterHtml(chapterTitle, downloadedImages, 
                    chapterInfo.getChapterNumber(), tmpHtmlDir);
                
                ChapterContent chapterContent = new ChapterContent(chapterInfo, downloadedImages, chapterHtmlFile);
                successfullyProcessedChapters.add(chapterContent);
                
                logger.info("Completed chapter " + chapterInfo.getChapterNumber());
                
                // Thinking time with random jitter
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
                pdfBuilder.addChapter(chapterContent, chapterTitles.get(i));
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
            EpubBuilderService epubBuilder = new EpubBuilderService(adjustedTitle, logger);
            for (ChapterContent chapterContent : successfullyProcessedChapters) {
                String chapterTitle = TemplateEngine.applyChapterTitleTemplate(
                    bookProps.getProperty("chapter.title.template"), 
                    chapterContent.getInfo().getChapterNumber(), 
                    bookProps);
                epubBuilder.addChapter(chapterContent, chapterTitle);
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
    }

    private List<Path> downloadImages(List<String> imageUrls, Path chapterImagesDir, int chapterNumber, 
            int currentChapterIndex, int totalChapters) {
        List<Path> downloadedImages = new ArrayList<>();
        Map<String, Integer> filenameCounters = new HashMap<>();
        int totalImages = imageUrls.size();
        
        for (int i = 0; i < imageUrls.size(); i++) {
            String imageUrl = imageUrls.get(i);
            try {
                // Generate filename
                String filename = generateImageFilename(imageUrl, i, filenameCounters);
                Path imageFile = chapterImagesDir.resolve(filename);
                System.out.println("Processing Image " + (i + 1) + "/" + totalImages + " of chapter " + chapterNumber);
                
                
                // Check if file already exists (caching)
                if (Files.exists(imageFile)) {
                    logger.info("Skipped existing image: " + filename + " from " + imageUrl);
                    downloadedImages.add(imageFile);
                    continue;
                }

                
                // Encode URL to handle spaces and special characters
                String encodedImageUrl = encodeUrl(imageUrl);
                
                // Download image
                byte[] imageData = httpClientService.downloadBinary(encodedImageUrl);
                if (imageData == null) {
                    logger.warning("Failed to download image " + (i + 1) + "/" + imageUrls.size() + 
                        " from " + imageUrl + " (encoded: " + encodedImageUrl + ")");
                    continue;
                }
                
                Files.write(imageFile, imageData);
                downloadedImages.add(imageFile);
                logger.info("Downloaded image " + (i + 1) + "/" + imageUrls.size() + ": " + filename + " from " + imageUrl);
                
            } catch (Exception e) {
                logger.warning("Error downloading image " + imageUrl + ": " + e.getMessage());
            }
        }
        
        // Sort images by numeric value in filename (e.g., "1.jpg", "2.jpg", "10.jpg")
        // This ensures proper ordering even if files were created out of order
        downloadedImages.sort(Comparator.comparing(p -> {
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
        
        return downloadedImages;
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

    private Path createChapterHtml(String chapterTitle, List<Path> imageFiles, int chapterNumber, Path tmpHtmlDir) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        html.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n");
        html.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        html.append("<head><title>").append(chapterTitle).append("</title></head>\n");
        html.append("<body>\n");
        html.append("<h1>").append(chapterTitle).append("</h1>\n");
        
        for (Path imageFile : imageFiles) {
            String imageName = imageFile.getFileName().toString();
            // Use subdirectory structure: images/10/1.jpg to match EPUB storage
            html.append("<img src=\"images/").append(chapterNumber).append("/").append(imageName)
                .append("\" style=\"width:100%;max-width:100%;\" />\n");
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

