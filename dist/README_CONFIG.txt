CONFIGURATION:
--------------
Before running the application, you may want to configure it:

1. Global Configuration (web2book.properties):
   - Edit web2book.properties in this folder
   - This file contains global settings for all books
   - All settings can be overridden in book-specific config files
   
   Book List:
   * book.config.1=config/your-book.properties  (Required: at least one)
   * book.config.2=config/another-book.properties  (Optional: add more books)
   * book.config.N=config/book-name.properties  (Add as many as needed)
   
   Global Settings:
   * default.output.dir - Default output directory for all books (default: output)
   * default.log.dir - Default directory for log files (default: output/logs)
   * default.temp.dir - Default directory for temporary files (default: output/tmp)
   * default.output.format - Default output format: epub or pdf (default: pdf)
   * default.regenerate.existing.books - Regenerate existing books: true/false (default: false)
   * default.thinking.time.ms - Default delay between requests in milliseconds (default: 1500)
   * max.concurrent.image.downloads - Max concurrent image downloads globally (default: 4)
   * log.level - Application log level: SEVERE, WARNING, INFO, FINE, FINER, FINEST, or ALL (default: INFO)
   * pdfbox.log.level - PDFBox log level: SEVERE, WARNING, INFO, FINE, FINER, FINEST, OFF, or ALL (default: WARNING)

2. Book-Specific Configuration (config/*.properties):
   - Edit files in the config/ folder
   - Each .properties file configures one series of books for one web manga
   - Paths in config files should use forward slashes (/)
   - Example paths: output/my-book, tmp/my-book, logs/my-book
   
   Book Information:
   * manga.name - Readable manga name (used in templates)
   * book.author - Author name (optional, displayed in PDF/EPUB)
   * chapter.start - Starting chapter number (e.g., 1)
   * chapter.end - Ending chapter number (e.g., 100)
   
   Source Configuration:
   * starting.url - URL of one chapter page (used to extract chapter list)
   * chapter.base.url - Root URL to prefix relative chapter URLs
   * chapter.pattern - Regex pattern to extract chapter URLs and numbers from HTML
                     Group 1: relative URL, Group 2: chapter number
   * image.pattern - Regex pattern to extract image URLs from chapter pages
                   Group 1: image URL
   * thinking.time.ms - Delay between HTTP requests in milliseconds (overrides default)
   * max.concurrent.image.downloads - Max concurrent image downloads (overrides default)
   
   Output Configuration:
   * output.dir - Output directory for this book (overrides default.output.dir)
   * temp.dir - Temporary directory for this book (overrides default.temp.dir)
   * log.dir - Log directory for this book (overrides default.log.dir)
   * output.format - Output format: epub or pdf (overrides default.output.format)
   
   Volume/Splitting Configuration:
   * max.chapters.per.book - Maximum chapters per volume/book
                          If set to 50, chapters 1-50, 51-100, etc. are split into separate files
                          If not set or 0, all chapters go into a single file
   * max.images.per.page - Maximum images per PDF page (default: no limit)
                            When reached, a new page is created for remaining images
                            Only applies to PDF format
   
   Template Configuration:
   * book.title.template - Template for book title (shown in PDF/EPUB)
                          Placeholders: ${manga}, ${start}, ${end}
                          Example: ${manga}\nChapters ${start} to ${end}
   * book.filename.template - Template for output filename (without extension)
                             Placeholders: ${manga}, ${start}, ${end}
                             Example: ${manga} - Chapters ${start} to ${end}
   * chapter.title.template - Template for each chapter title
                             Placeholder: ${chapter}
                             Example: Chapter ${chapter}
   
   Cleanup Configuration:
   * delete.images.after.generation - Delete downloaded images after generation: true/false (default: false)
   * delete.xhtml.after.generation - Delete XHTML files after generation: true/false (default: false)
   * regenerate.existing.books - Regenerate existing books: true/false (overrides default)
                                If false, existing files are skipped

3. Adding New Book Configurations:
   a. Create a new .properties file in config/ folder
   b. Add a reference to it in web2book.properties:
      book.config.N=config/your-book-name.properties

FOLDER STRUCTURE:
-----------------
This package includes the following folders:

- config/          → Book configuration files (.properties files)
- logs/            → Log files (created automatically)
- tmp/             → Temporary files during processing (created automatically)
- output/          → Generated books (EPUB/PDF files)

OUTPUT LOCATIONS:
-----------------
- Generated books (EPUB/PDF files):
  → output/ folder (or subfolders like output/martial-peak/)
  
- Log files:
  → logs/ folder (default location)
  → Individual book logs may be in logs/[book-name]/ if configured
  
- Temporary files:
  → tmp/ folder (default location)
  → Individual book temp files may be in tmp/[book-name]/ if configured

==========================================
APPENDIX
==========================================

LLM prompts to generate the chapter and image patterns


Chapter pattern:
------------------------------------------------------------------
I want to extract content from an HTML document

I need to identify the 2 following HTML blocks
 <option value="/chaptered.php?manga=1&chapter=593" selected="">Chapter:593</option>
<option value="/chaptered.php?manga=1&chapter=594">Chapter:594</option>

The data I am extracting are
- Group 1: relative URL of the chapter
- Group 2: numeric chapter number

For now I use this pattern (regex) to extract chapter URLs and chapter numbers from the document
chapter.pattern=<option[^>]*value=['"]([^'"]+)['"][^>]*>Chapter:(\\d+)</option>

The pattern handles both single and double quotes, optional whitespace, and optional 'selected' attribute



Create the chapter.pattern for the following HTML to extract 
- Group 1: relative URL of the chapter
- Group 2: numeric chapter number

First example of block of HTML
I want to extract content from an HTML document

I need to identify the 2 following HTML blocks
 <option value="/chaptered.php?manga=1&chapter=593" selected="">Chapter:593</option>
<option value="/chaptered.php?manga=1&chapter=594">Chapter:594</option>

The data I am extracting are
- Group 1: relative URL of the chapter
- Group 2: numeric chapter number

For now I use this pattern (regex) to extract chapter URLs and chapter numbers from the document
chapter.pattern=<option[^>]*value=['"]([^'"]+)['"][^>]*>Chapter:(\\d+)</option>

The pattern handles both single and double quotes, optional whitespace, and optional 'selected' attribute



Create the chapter.pattern for the following HTML to extract 
- Group 1: relative URL of the chapter
- Group 2: numeric chapter number

First example of block of HTML:

                         <option data-c="/manga/du-beonjjae-salmeun-hillingnaipeu/chapter-2"
                >
                Chapter 2
            </option>

Note that the spaces and new lines are part of block

Second example of block of HTML:

                        <option data-c="/manga/du-beonjjae-salmeun-hillingnaipeu/chapter-1"
                selected>
                Chapter 1
            </option>

Here again the spaces and new lines are part of the block of HTML we look for

A Java app reads the regex from a properties file
You must escape backslashes
------------------------------------------------------------------


Image pattern:
------------------------------------------------------------------

I want to extract content from an HTML document

For now, I identify the 2 following HTML blocks
<img onerror="tryAgain(this)" class="imgholder" src="https://demonicscans.org/images/Martial-Peak/593/1.jpg" />
<img onerror="tryAgain(this)" class="imgholder" src="https://demonicscans.org/images/Martial-Peak/594/1.jpg" />


The data I am extracting is
- Group 1: the image URL

For now I use this pattern (regex) to extract all image URLs from the document
image.pattern=<img(?=[^>]*onerror="tryAgain\\(this\\)")(?=[^>]*class="imgholder")[^>]*src="([^"]+)"[^>]*>

Requires both onerror="tryAgain(this)" AND class="imgholder" to exclude ad images
Note: Backslashes in properties files need to be escaped, so \\( becomes \( in regex
Pattern matches img tags that have both attributes (in any order) and extracts the src URL

Create the chapter.pattern for the following HTML to extract 
- Group 1: the image URL

First example of block of HTML:
<img  src='https://img-r1.2xstorage.com/du-beonjjae-salmeun-hillingnaipeu/23/94.webp' alt='Du Beonjjae Salmeun Hillingnaipeu? Chapter 23 page 95 - MangaNato' title='Du Beonjjae Salmeun Hillingnaipeu? Chapter 23 page 95 - MangaNato'
            onerror="this.onerror=null;this.src='https://imgs-2.2xstorage.com/du-beonjjae-salmeun-hillingnaipeu/23/94.webp';"
            loading='lazy'>

Note that the spaces and new lines are part of block

Second example of block of HTML:

<img  src='https://img-r1.2xstorage.com/du-beonjjae-salmeun-hillingnaipeu/23/95.webp' alt='Du Beonjjae Salmeun Hillingnaipeu? Chapter 23 page 96 - MangaNato' title='Du Beonjjae Salmeun Hillingnaipeu? Chapter 23 page 96 - MangaNato'
            onerror="this.onerror=null;this.src='https://imgs-2.2xstorage.com/du-beonjjae-salmeun-hillingnaipeu/23/95.webp';"
            loading='lazy'>

Here again the spaces and new lines are part of the block of HTML we look for
onerror="this.onerror=null
loading='lazy'>

Make sure that the following String are clearly identified in the pattern to filer properly the HTML
Also the URRL should start with https://img-r1.2xstorage.com/du-beonjjae-salmeun-hillingnaipeu

That will help exclude these HTML blocks as others
 <img src="/images/logo-manganato.webp" alt="Manga Online" title="Manga Online">
<img src="https://yougetwhatyoupayfor.net/banners-web/_Sticky-man-hook_29_07_2025_10_49_31.gif" style="width: 100%;">
<img src="/images/loadingimg.gif" style="height: 6px;"></img>
 <img src="https://www.natomanga.com/images/bns/common/info_top_mobile_01.gif" alt="ei0qg">
<img src="/images/no-avatar.jpg" alt="${userData.name}">
<img src="/images/loadingimg.gif" style="height: 6px;" />


Give me only the image.pattern with no comment and no explanation
-------------------------------------------------------------------------

