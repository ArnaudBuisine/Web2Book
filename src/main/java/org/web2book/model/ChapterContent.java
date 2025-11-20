// Generated code - Model: Auto (Cursor AI)
// Date: 2025-01-27
package org.web2book.model;

import java.nio.file.Path;
import java.util.List;

/**
 * Represents the content of a chapter including images and HTML.
 */
public class ChapterContent {
    private final ChapterInfo info;
    private final List<Path> imageFiles;
    private final Path chapterHtmlFile;

    public ChapterContent(ChapterInfo info, List<Path> imageFiles, Path chapterHtmlFile) {
        this.info = info;
        this.imageFiles = imageFiles;
        this.chapterHtmlFile = chapterHtmlFile;
    }

    public ChapterInfo getInfo() {
        return info;
    }

    public List<Path> getImageFiles() {
        return imageFiles;
    }

    public Path getChapterHtmlFile() {
        return chapterHtmlFile;
    }
}

