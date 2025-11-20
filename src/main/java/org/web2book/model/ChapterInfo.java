// Generated code - Model: Auto (Cursor AI)
// Date: 2025-01-27
package org.web2book.model;

/**
 * Represents information about a manga chapter.
 */
public class ChapterInfo {
    private final int chapterNumber;
    private final String relativeUrl;
    private final String fullUrl;

    public ChapterInfo(int chapterNumber, String relativeUrl, String fullUrl) {
        this.chapterNumber = chapterNumber;
        this.relativeUrl = relativeUrl;
        this.fullUrl = fullUrl;
    }

    public int getChapterNumber() {
        return chapterNumber;
    }

    public String getRelativeUrl() {
        return relativeUrl;
    }

    public String getFullUrl() {
        return fullUrl;
    }

    @Override
    public String toString() {
        return "ChapterInfo{" +
                "chapterNumber=" + chapterNumber +
                ", fullUrl='" + fullUrl + '\'' +
                '}';
    }
}

