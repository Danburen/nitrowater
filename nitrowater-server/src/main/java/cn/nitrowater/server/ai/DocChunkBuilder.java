package cn.nitrowater.server.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Turns the markdown documentation tree into vector-store chunks.
 *
 * <p>This class is deliberately free of Qdrant/HTTP concerns so it can be unit tested against a
 * temporary directory. Per file it:</p>
 * <ol>
 *   <li>strips a leading UTF-8 BOM and YAML front matter (VitePress home pages carry one);</li>
 *   <li>gives bare code fences a {@code text} info string (the Spring AI reader stores the fence
 *       info as metadata and rejects a null value);</li>
 *   <li>splits by markdown headings via {@link MarkdownDocumentReader}, then by token count;</li>
 *   <li>attaches stable metadata (corpus, path, title, section, language, public URL) and a
 *       deterministic id so a re-index updates the same points instead of duplicating them.</li>
 * </ol>
 *
 * <p>Sources may live on the filesystem or under {@code classpath:}. The classpath path is read
 * with {@link Resource#getInputStream()} so it also works from a packaged Spring Boot jar.</p>
 */
@Component
public class DocChunkBuilder {

    /** Logical corpus name, used to delete exactly one corpus on re-index. */
    public static final String META_CORPUS = "corpus";
    /** Repository-relative markdown path, e.g. {@code guide/config/main.md}. */
    public static final String META_DOC_PATH = "doc_path";
    /** Human-readable document title (first H1, falling back to the file name). */
    public static final String META_DOC_TITLE = "doc_title";
    /** Heading of the section the chunk came from. */
    public static final String META_SECTION = "section";
    /** Document language slug: {@code zh} or {@code en}. */
    public static final String META_LANG = "lang";
    /** Absolute-or-root-relative URL of the rendered page, used by the UI citations. */
    public static final String META_URL = "url";
    /** Zero-based chunk position inside the source file. */
    public static final String META_CHUNK = "chunk";

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final Pattern H1 = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");
    private static final String FRONT_MATTER_DELIMITER = "---";

    private final AiProperties properties;
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    public DocChunkBuilder(AiProperties properties) {
        this.properties = properties;
    }

    /** Reads every configured collection. */
    public Result build() throws IOException {
        return build(properties.collections());
    }


    /**
     * Reads all collections and concatenates their chunks.
     *
     * @param collections collections to index
     * @return the chunks plus the aggregate number of source files
     * @throws IOException when a source cannot be read
     */
    public Result build(List<DocCollection> collections) throws IOException {
        List<Document> chunks = new ArrayList<>();
        int files = 0;
        for (DocCollection collection : collections) {
            Result result = build(collection);
            chunks.addAll(result.chunks());
            files += result.files();
        }
        return new Result(List.copyOf(chunks), files);
    }

    /**
     * Reads one collection from its filesystem or classpath location.
     *
     * @param collection corpus plus its source location
     * @return the chunks plus the number of source files that produced them
     * @throws IOException when the tree cannot be read
     */
    public Result build(DocCollection collection) throws IOException {
        List<DocFile> files = load(collection.location());
        if (files.isEmpty()) {
            return new Result(List.of(), 0);
        }

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(Math.max(100, properties.chunkSize()))
                .withMinChunkSizeChars(Math.max(0, properties.minChunkSizeChars()))
                .withMaxNumChunks(Math.max(1, properties.maxChunksPerDoc()))
                .withKeepSeparator(true)
                .build();

        List<Document> chunks = new ArrayList<>();
        for (DocFile file : files) {
            String relative = file.relativePath();
            String markdown = normalizeFences(stripFrontMatter(file.content()));
            if (markdown.isBlank()) {
                continue;
            }
            String title = extractTitle(markdown, file.fileName());
            String language = relative.startsWith("en/") ? "en" : "zh";
            String url = toPublicUrl(relative, collection.publicBaseUrl());

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(META_CORPUS, collection.corpus());
            metadata.put(META_DOC_PATH, relative);
            metadata.put(META_DOC_TITLE, title);
            metadata.put(META_LANG, language);
            metadata.put(META_URL, url);

            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withIncludeCodeBlock(true)
                    .withIncludeBlockquote(true)
                    .withHorizontalRuleCreateDocument(false)
                    .withAdditionalMetadata(metadata)
                    .build();

            ByteArrayResource resource = new ByteArrayResource(markdown.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getFilename() {
                    return file.fileName();
                }
            };

            List<Document> sections = new MarkdownDocumentReader(resource, config).get();
            List<Document> parts = splitter.split(sections);
            int index = 0;
            for (Document part : parts) {
                String text = part.getText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                Map<String, Object> chunkMetadata = new LinkedHashMap<>();
                part.getMetadata().forEach((key, value) -> {
                    if (value != null) {
                        chunkMetadata.put(key, value);
                    }
                });
                Object heading = chunkMetadata.get("title");
                chunkMetadata.put(META_SECTION, heading == null ? title : heading.toString());
                chunkMetadata.put(META_CHUNK, index);
                chunks.add(Document.builder()
                        .id(chunkId(collection.corpus(), relative, index))
                        .text(text)
                        .metadata(chunkMetadata)
                        .build());
                index++;
            }
        }
        return new Result(List.copyOf(chunks), files.size());
    }


    /**
     * @param corpus logical corpus name
     * @param relativePath repository-relative markdown path
     * @param chunkIndex zero-based chunk position
     * @return a deterministic point id (stable across restarts and re-indexes)
     */
    public String chunkId(String corpus, String relativePath, int chunkIndex) {
        String key = corpus + "|" + relativePath + "|" + chunkIndex;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * Maps a markdown source path to the VitePress page URL.
     *
     * <p>VitePress is built with its default {@code cleanUrls: false}, so pages end in
     * {@code .html}; index files map to their directory. A blank {@code baseUrl} means the corpus
     * is markdown-only (no published site), so citations carry no URL and the UI renders them as
     * plain labels.</p>
     *
     * @param relativePath repository-relative markdown path (uses {@code /} separators)
     * @param baseUrl public mount point, e.g. {@code /docs/velochatx/}; blank for a markdown-only corpus
     * @return page URL beginning with {@code baseUrl}, or an empty string when no site is published
     */
    public static String toPublicUrl(String relativePath, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String base = baseUrl;
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        String path = relativePath.replace('\\', '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.equals("index.md")) {
            return base;
        }
        if (path.endsWith("/index.md")) {
            return base + path.substring(0, path.length() - "index.md".length());
        }
        if (path.endsWith(".md")) {
            path = path.substring(0, path.length() - 3);
        }
        return base + path + ".html";
    }

    /**
     * Removes a leading YAML front matter block.
     *
     * @param raw file contents
     * @return contents without the front matter block
     */
    static String stripFrontMatter(String raw) {
        String text = raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
        if (!text.startsWith(FRONT_MATTER_DELIMITER)) {
            return text;
        }
        int firstLineEnd = text.indexOf('\n');
        if (firstLineEnd < 0 || !text.substring(0, firstLineEnd).trim().equals(FRONT_MATTER_DELIMITER)) {
            return text;
        }
        int cursor = firstLineEnd + 1;
        while (cursor < text.length()) {
            int lineEnd = text.indexOf('\n', cursor);
            String line = (lineEnd < 0 ? text.substring(cursor) : text.substring(cursor, lineEnd)).trim();
            if (line.equals(FRONT_MATTER_DELIMITER)) {
                return lineEnd < 0 ? "" : text.substring(lineEnd + 1);
            }
            if (lineEnd < 0) {
                break;
            }
            cursor = lineEnd + 1;
        }
        // Unclosed front matter is not front matter: keep the original text.
        return text;
    }

    /**
     * Adds a {@code text} info string to bare opening code fences.
     *
     * <p>The Spring AI markdown reader copies the fence info into document metadata; a bare fence
     * yields a null value which {@code Document} rejects.</p>
     *
     * @param markdown markdown source
     * @return markdown with every bare opening fence annotated
     */
    static String normalizeFences(String markdown) {
        String[] lines = markdown.split("\n", -1);
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!trimmed.startsWith("```")) {
                continue;
            }
            if (inFence) {
                inFence = false;
            } else {
                inFence = true;
                if (trimmed.equals("```")) {
                    lines[i] = lines[i] + "text";
                }
            }
        }
        return String.join("\n", lines);
    }

    /**
     * @param markdown front-matter-free markdown
     * @param fileName source file name (fallback title)
     * @return first H1 text, else the file name without its extension
     */
    static String extractTitle(String markdown, String fileName) {
        Matcher matcher = H1.matcher(markdown);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        if (fileName == null || fileName.isBlank()) {
            return "untitled";
        }
        return fileName.toLowerCase().endsWith(".md")
                ? fileName.substring(0, fileName.length() - 3)
                : fileName;
    }

    private List<DocFile> load(String location) throws IOException {
        if (location.startsWith(CLASSPATH_PREFIX)) {
            return loadClasspath(location.substring(CLASSPATH_PREFIX.length()));
        }
        return loadFileSystem(Path.of(location));
    }

    private List<DocFile> loadFileSystem(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<DocFile> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".md"))
                    .filter(path -> !hasHiddenSegment(root.relativize(path)))
                    .sorted(Comparator.comparing(path -> normalize(root.relativize(path))))
                    .toList();
            for (Path path : paths) {
                files.add(new DocFile(normalize(root.relativize(path)), path.getFileName().toString(),
                        Files.readString(path, StandardCharsets.UTF_8)));
            }
        }
        return List.copyOf(files);
    }

    private List<DocFile> loadClasspath(String rawBase) throws IOException {
        String base = rawBase.replace('\\', '/');
        if (base.startsWith("/")) {
            base = base.substring(1);
        }
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + base + "**/*.md";
        Resource[] resources = resourceResolver.getResources(pattern);
        Map<String, DocFile> files = new LinkedHashMap<>();
        for (Resource resource : resources) {
            String relative = relativePath(resource, base);
            if (relative == null || relative.isBlank() || hasHiddenSegment(relative)) {
                continue;
            }
            try (InputStream input = resource.getInputStream()) {
                String fileName = resource.getFilename();
                if (fileName == null || fileName.isBlank()) {
                    int slash = relative.lastIndexOf('/');
                    fileName = slash >= 0 ? relative.substring(slash + 1) : relative;
                }
                files.putIfAbsent(relative, new DocFile(relative, fileName,
                        new String(input.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
        List<DocFile> sorted = new ArrayList<>(files.values());
        sorted.sort(Comparator.comparing(DocFile::relativePath));
        return List.copyOf(sorted);
    }

    private static String relativePath(Resource resource, String base) {
        try {
            String url = resource.getURL().toString().replace('\\', '/');
            String marker = "/" + base;
            int index = url.indexOf(marker);
            if (index >= 0) {
                return url.substring(index + marker.length());
            }
        } catch (IOException ignored) {
            // Fall through to the file name; the caller still gets a usable document.
        }
        return resource.getFilename();
    }

    private static boolean hasHiddenSegment(Path relative) {
        for (Path segment : relative) {
            if (isHidden(Objects.toString(segment, ""))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasHiddenSegment(String relative) {
        for (String segment : relative.split("/")) {
            if (isHidden(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHidden(String name) {
        return name.startsWith(".") || name.equals("node_modules");
    }

    private static String normalize(Path relative) {
        return relative.toString().replace('\\', '/');
    }

    private record DocFile(String relativePath, String fileName, String content) {
    }

    /**
     * @param chunks indexable chunks in deterministic order
     * @param files number of markdown files that were read
     */
    public record Result(List<Document> chunks, int files) {
    }
}
