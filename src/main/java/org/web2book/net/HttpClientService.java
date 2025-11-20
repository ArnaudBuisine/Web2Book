// Generated code - Model: Auto (Cursor AI)
// Date: 2025-01-27
package org.web2book.net;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Service for making HTTP requests with retry logic and Cloudflare protection.
 */
public class HttpClientService {
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 750;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    private final HttpClient httpClient;
    private final Logger logger;
    private String refererUrl;

    public HttpClientService(Logger logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL) // Follow redirects (301, 302, 303, 307, 308)
                // Enable automatic decompression of gzip, deflate, and br
                .build();
    }

    /**
     * Sets the referer URL for subsequent requests.
     */
    public void setRefererUrl(String refererUrl) {
        this.refererUrl = refererUrl;
    }

    /**
     * Fetches HTML content from a URL with retry logic.
     * 
     * @param url The URL to fetch
     * @return The HTML content as a string, or null if all retries failed
     */
    public String getHtml(String url) {
        return executeWithRetry(url, true);
    }

    /**
     * Checks if the HTML content indicates a Cloudflare challenge page.
     * Uses very specific patterns that only appear on actual challenge pages.
     * 
     * @param html The HTML content to check
     * @return true if Cloudflare challenge is detected
     */
    public boolean isCloudflareChallenge(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        
        String lowerHtml = html.toLowerCase();
        
        // Very specific Cloudflare challenge page indicators
        // These must appear together or in specific contexts to avoid false positives
        
        // 1. The exact challenge page message (most reliable)
        if (lowerHtml.contains("checking your browser before accessing")) {
            return true;
        }
        
        // 2. "Just a moment" combined with Cloudflare challenge elements
        if (lowerHtml.contains("just a moment")) {
            // Must also have challenge-specific elements
            if (lowerHtml.contains("cf-browser-verification") || 
                lowerHtml.contains("challenge-platform") ||
                lowerHtml.contains("cf-challenge")) {
                return true;
            }
        }
        
        // 3. Challenge platform with verification elements
        if (lowerHtml.contains("challenge-platform") && 
            (lowerHtml.contains("cf-browser-verification") || lowerHtml.contains("cf-challenge"))) {
            return true;
        }
        
        // 4. Ray ID error page (specific Cloudflare error structure)
        // This pattern: "ray id" near "cloudflare" and "checking" in error context
        if (lowerHtml.contains("ray id") && 
            lowerHtml.contains("cloudflare") &&
            lowerHtml.contains("checking") &&
            (lowerHtml.contains("error") || lowerHtml.contains("blocked") || lowerHtml.contains("access denied"))) {
            return true;
        }
        
        // If none of the specific patterns match, it's not a challenge page
        return false;
    }

    /**
     * Downloads binary content from a URL with retry logic.
     * 
     * @param url The URL to download
     * @return The binary content as a byte array, or null if all retries failed
     */
    public byte[] downloadBinary(String url) {
        return downloadBinaryInternal(url);
    }

    private String executeWithRetry(String url, boolean isHtml) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 1) {
                    logger.info("Retry attempt " + attempt + "/" + MAX_RETRIES + " for URL: " + url);
                }
                
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(TIMEOUT)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.5")
                        .header("Accept-Encoding", "gzip, deflate");
                        // Note: Removed "br" (Brotli) as it may not be fully supported by HttpClient
                        // Note: "Connection" header is restricted and managed automatically by HttpClient
                
                // Add Referer header for HTML requests
                if (isHtml && refererUrl != null) {
                    requestBuilder.header("Referer", refererUrl);
                }
                
                HttpRequest request = requestBuilder.GET().build();

                // Read response as bytes first to handle compression manually
                // This ensures we can properly decompress gzip/deflate responses
                HttpResponse<byte[]> byteResponse = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofByteArray());
                
                if (byteResponse.statusCode() == 200) {
                    byte[] responseBytes = byteResponse.body();
                    
                    // Check Content-Encoding header to determine if decompression is needed
                    String contentEncoding = byteResponse.headers().firstValue("Content-Encoding").orElse("");
                    
                    String body;
                    try {
                        if (contentEncoding.contains("gzip")) {
                            try (GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(responseBytes))) {
                                body = new String(gzipIn.readAllBytes(), StandardCharsets.UTF_8);
                            }
                        } else if (contentEncoding.contains("deflate")) {
                            try (InflaterInputStream inflateIn = new InflaterInputStream(new ByteArrayInputStream(responseBytes))) {
                                body = new String(inflateIn.readAllBytes(), StandardCharsets.UTF_8);
                            }
                        } else {
                            // No compression, decode directly as UTF-8
                            body = new String(responseBytes, StandardCharsets.UTF_8);
                        }
                    } catch (IOException e) {
                        logger.warning("Failed to decompress response (Content-Encoding: " + contentEncoding + 
                            "): " + e.getMessage() + ". Trying direct UTF-8 decode.");
                        // Fallback: try direct UTF-8 decode
                        body = new String(responseBytes, StandardCharsets.UTF_8);
                    }
                    
                    return body;
                } else if (byteResponse.statusCode() >= 300 && byteResponse.statusCode() < 400) {
                    // Redirect status codes (301, 302, 303, 307, 308)
                    // HttpClient should follow redirects automatically, but if we get here,
                    // it might be a redirect loop or the redirect couldn't be followed
                    String location = byteResponse.headers().firstValue("Location").orElse("unknown");
                    throw new IOException("HTTP " + byteResponse.statusCode() + " redirect to: " + location + " for URL: " + url);
                } else {
                    throw new IOException("HTTP " + byteResponse.statusCode() + " for URL: " + url);
                }
            } catch (java.net.http.HttpTimeoutException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    logger.warning(String.format("Attempt %d/%d failed for URL %s: request timed out. Retrying in %d ms...", 
                        attempt, MAX_RETRIES, url, RETRY_DELAY_MS));
                    System.out.println("WARNING: Request timeout for " + url + ", retrying...");
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.severe("Interrupted during retry delay");
                        System.err.println("ERROR: Interrupted during retry delay");
                        return null;
                    }
                } else {
                    logger.severe(String.format("Attempt %d/%d failed for URL %s: request timed out (final attempt)", 
                        attempt, MAX_RETRIES, url));
                    System.err.println("ERROR: Request timed out after " + MAX_RETRIES + " attempts for " + url);
                }
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    logger.warning(String.format("Attempt %d/%d failed for URL %s: %s. Retrying in %d ms...", 
                        attempt, MAX_RETRIES, url, e.getMessage(), RETRY_DELAY_MS));
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.severe("Interrupted during retry delay");
                        System.err.println("ERROR: Interrupted during retry delay");
                        return null;
                    }
                } else {
                    logger.severe(String.format("Attempt %d/%d failed for URL %s: %s (final attempt)", 
                        attempt, MAX_RETRIES, url, e.getMessage()));
                }
            }
        }
        
        String errorMsg = String.format("Failed to fetch %s after %d attempts: %s", 
            url, MAX_RETRIES, lastException != null ? lastException.getMessage() : "Unknown error");
        logger.severe(errorMsg);
        System.err.println("ERROR: " + errorMsg);
        return null;
    }

    /**
     * Checks if a string has high binary content (many non-printable characters).
     */
    private boolean hasHighBinaryContent(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int nonPrintable = 0;
        int sampleSize = Math.min(text.length(), 1000); // Check first 1000 chars
        for (int i = 0; i < sampleSize; i++) {
            char c = text.charAt(i);
            // Check for non-printable ASCII (except common whitespace)
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                nonPrintable++;
            }
        }
        // If more than 5% are non-printable, likely binary
        return (nonPrintable * 100.0 / sampleSize) > 5.0;
    }

    private byte[] downloadBinaryInternal(String url) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(TIMEOUT)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.5")
                        .header("Accept-Encoding", "gzip, deflate");
                        // Note: Removed "br" (Brotli) as it may not be fully supported by HttpClient
                        // Note: "Connection" header is restricted and managed automatically by HttpClient
                
                // Add Referer for image downloads
                if (refererUrl != null) {
                    requestBuilder.header("Referer", refererUrl);
                }
                
                HttpRequest request = requestBuilder.GET().build();

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                
                if (response.statusCode() == 200) {
                    return response.body();
                } else {
                    throw new IOException("HTTP " + response.statusCode() + " for URL: " + url);
                }
            } catch (java.net.http.HttpTimeoutException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    logger.warning(String.format("Attempt %d/%d failed for binary download %s: request timed out. Retrying in %d ms...", 
                        attempt, MAX_RETRIES, url, RETRY_DELAY_MS));
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.severe("Interrupted during retry delay");
                        return null;
                    }
                } else {
                    logger.severe(String.format("Attempt %d/%d failed for binary download %s: request timed out (final attempt)", 
                        attempt, MAX_RETRIES, url));
                }
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    logger.warning(String.format("Attempt %d/%d failed for binary download %s: %s. Retrying in %d ms...", 
                        attempt, MAX_RETRIES, url, e.getMessage(), RETRY_DELAY_MS));
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.severe("Interrupted during retry delay");
                        return null;
                    }
                } else {
                    logger.severe(String.format("Attempt %d/%d failed for binary download %s: %s (final attempt)", 
                        attempt, MAX_RETRIES, url, e.getMessage()));
                }
            }
        }
        
        String errorMsg = String.format("Failed to download binary %s after %d attempts: %s", 
            url, MAX_RETRIES, lastException != null ? lastException.getMessage() : "Unknown error");
        logger.severe(errorMsg);
        return null;
    }
}

