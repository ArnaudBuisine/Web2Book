# Web2Book

A Java command-line application that converts web manga chapters into EPUB format. The application downloads chapters from manga websites, extracts images, and creates well-formatted EPUB books.

## Quick Start

mvn clean package & java -jar target/web2book-1.0.0.jar

**Build the project:**
```bash
mvn clean package
```

**Run the application:**
```bash
java -jar target/web2book-1.0.0.jar
```

> **Note:** Ensure `web2book.properties` exists in the current working directory before running.

## Features

- Downloads manga chapters from configurable websites
- Extracts images using regex patterns
- Creates EPUB files with proper structure
- Supports volume splitting (multiple EPUBs per book)
- Sequential processing to avoid overwhelming servers
- Configurable delays between chapter downloads
- File-based logging per book
- Image caching to avoid re-downloading

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

## Building the Project

1. Clone or navigate to the project directory:
```bash
cd Web2Book
```

2. Build the project using Maven:
```bash
mvn clean package
```

This will create an executable JAR file at `target/web2book-1.0.0.jar`.

## Configuration

### Global Configuration

Create or edit `web2book.properties` in the project root:

```properties
# List of book configuration files (relative paths from project root)
book.config.1=config/martial-peak-801-1030.properties

# Default output directory for all books (can be overridden per book)
default.output.dir=output

# Default thinking time in milliseconds between chapters
default.thinking.time.ms=1500
```

### Book Configuration

Create book configuration files in the `config/` directory. Example: `config/martial-peak-801-1030.properties`

```properties
# Starting URL of one chapter of the online manga
starting.url=https://demonicscans.org/title/Martial-Peak/chapter/306/1

# Pattern (regex) to extract chapter URLs and chapter numbers from the starting page
# Group 1: relative URL of the chapter
# Group 2: numeric chapter number
chapter.pattern=<option value="(.*?)">Chapter:(\\d+)</option>

# Root URL to prefix to the relative chapter URLs
chapter.base.url=https://demonicscans.org

# Pattern (regex) to extract all image URLs from a chapter page
# Group 1: the image URL
image.pattern=<img[^>]*class="imgholder"[^>]*src="([^"]+)"[^>]*>

# Chapter range to include in this EPUB
chapter.start=801
chapter.end=1030

# Template for the book title
# ${start} and ${end} will be replaced with chapter.start and chapter.end
book.title.template=Martial Peak - Chapters ${start} to ${end}

# Template for each chapter title
# ${chapter} will be replaced with the current chapter number
chapter.title.template=Chapter ${chapter}

# Optional readable manga name (can be used by templates if needed)
manga.name=Martial Peak

# Thinking time override for this book (milliseconds, overrides default.thinking.time.ms)
thinking.time.ms=2000

# Output directory for this specific book (relative to project root)
output.dir=output/martial-peak-801-1030

# Delete downloaded images after book generation (true/false)
delete.images.after.generation=true

# Max number of chapters per EPUB volume
# If max.chapters.per.book=50, then volumes will be created:
#   Martial Peak - Chapters 801-850.epub
#   Martial Peak - Chapters 851-900.epub
#   ... etc
max.chapters.per.book=50
```

## Running the Application

1. Ensure `web2book.properties` exists in the current working directory.

2. Run the application:
```bash
java -jar target/web2book-1.0.0.jar
```

The application will:
- Load the global configuration from `web2book.properties`
- Process each book configuration file listed
- Download chapters and images sequentially
- Generate EPUB files in the specified output directories
- Create log files alongside each EPUB

## Output

- **EPUB files**: Generated in the output directory specified in each book configuration
- **Log files**: Created in the same directory as the EPUB files, named `<book-title>.log`
- **Temporary files**: Images and HTML are stored in `tmp/` subdirectories (cleaned up based on configuration)

## Project Structure

```
Web2Book/
├── pom.xml                          # Maven build configuration
├── web2book.properties              # Global configuration
├── config/                          # Book configuration files
│   └── martial-peak-801-1030.properties
├── src/
│   └── main/
│       └── java/
│           └── org/
│               └── web2book/
│                   ├── Web2BookApp.java      # Main entry point
│                   ├── core/
│                   │   └── BookJob.java      # Book processing logic
│                   ├── model/                # Data models
│                   ├── net/                  # HTTP client
│                   ├── html/                 # HTML extraction
│                   ├── util/                 # Utilities
│                   ├── epub/                 # EPUB builder
│                   └── log/                  # Logging
└── target/                          # Build output (generated)
    └── web2book-1.0.0.jar          # Executable JAR
```

## Configuration Properties

### Global Properties (`web2book.properties`)

- `book.config.N` - Path to book configuration file (N = 1, 2, 3, ...)
- `default.output.dir` - Default output directory for EPUBs
- `default.thinking.time.ms` - Default delay between chapters (milliseconds)

### Book Properties (per book configuration file)

**Required:**
- `starting.url` - Starting chapter URL
- `chapter.pattern` - Regex to extract chapter URLs
- `chapter.base.url` - Base URL for chapter links
- `image.pattern` - Regex to extract image URLs
- `chapter.start` - First chapter number to include
- `chapter.end` - Last chapter number to include
- `book.title.template` - Template for book title
- `chapter.title.template` - Template for chapter titles

**Optional:**
- `manga.name` - Manga name for templates
- `thinking.time.ms` - Override thinking time for this book
- `output.dir` - Override output directory for this book
- `delete.images.after.generation` - Clean up images after EPUB creation (default: false)
- `max.chapters.per.book` - Split into multiple volumes if specified

## Notes

- The application processes books, volumes, and chapters **sequentially** (no parallel processing)
- Images are cached - if a file already exists, it won't be re-downloaded
- Failed chapters are logged but processing continues
- The application respects thinking time delays to avoid overwhelming servers
- Log files are created in the same directory as the EPUB output

## Troubleshooting

- **"Failed to load web2book.properties"**: Ensure the file exists in the current working directory
- **"No chapters found"**: Check your `chapter.pattern` regex matches the HTML structure
- **"No images found"**: Verify your `image.pattern` regex is correct
- **Build errors**: Ensure Java 17+ and Maven are installed and accessible

## License

This project is provided as-is for personal use.

