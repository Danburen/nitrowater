package cn.nitrowater.core.web.legal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Lists and reads the operator's legal texts (privacy policy, terms, any further documents).
 *
 * <p>The texts are host state: the BFF mounts the directory and reads it per request, so an edit is
 * live immediately and a deployment holds one copy per document.</p>
 *
 * <p>One plain-text file per document, no front matter, no Markdown, no ordering: the index follows
 * the directory listing and the page renders the documents in that order.</p>
 * <ul>
 *   <li>{@code <key>.txt}, where {@code key} matches {@link #KEY_PATTERN}; other names are ignored
 *       and cannot become an endpoint.</li>
 *   <li>title: the first non-blank line of the file, i.e. the heading the operator already writes;
 *       the file falls back to its key when that line is a paragraph instead.</li>
 *   <li>body: the file without that heading line, so the page does not print the title twice.</li>
 * </ul>
 *
 * <p>The requested key is never joined into a path — it is validated and then matched against the
 * directory listing, which rules out traversal.</p>
 *
 * <p>A {@code @Service} so controllers inject it, but not picked up by a broad component scan: services
 * that scan {@code cn.nitrowater} exclude {@code cn.nitrowater.core.web} (see the account service's
 * boundary test), and the rest scan only their own package. The BFF therefore opts in explicitly with
 * {@code @Import(LegalDocuments.class)}, and the directory is injected as {@code app.legal.dir}.</p>
 */
@Service
@Slf4j
public class LegalDocuments {

    /** Addressable document keys, i.e. the file name without {@code .txt}. */
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    private static final String EXTENSION = ".txt";

    /** A first line longer than this is a paragraph, not a heading — the key is used instead. */
    private static final int MAX_TITLE_LENGTH = 40;

    /** Tried when no directory is configured, so {@code bootRun} works from root or module. */
    private static final List<String> DEFAULT_CANDIDATES = List.of(
            "deploy/content/legal",
            "../deploy/content/legal",
            "/etc/nitrowater/legal");

    private final Path directory;

    /**
     * @param configured {@code app.legal.dir}; blank auto-detects the mounted directory
     */
    public LegalDocuments(@Value("${app.legal.dir:}") String configured) {
        this.directory = resolveDirectory(configured);
        log.info("Legal documents directory: {}", directory);
    }

    /**
     * @param configured {@code app.legal.dir}; blank means auto-detect
     * @return the configured path, else the first existing candidate, else the first candidate
     *         (a missing directory yields an empty index and 404s)
     */
    public static Path resolveDirectory(String configured) {
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        for (String candidate : DEFAULT_CANDIDATES) {
            Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path.toAbsolutePath().normalize();
            }
        }
        return Path.of(DEFAULT_CANDIDATES.getFirst()).toAbsolutePath().normalize();
    }

    /**
     * @return one entry per document, in directory order; empty when nothing is published yet
     * @throws IOException when the directory exists but cannot be listed
     */
    public List<LegalDocumentSummary> index() throws IOException {
        List<LegalDocumentSummary> documents = new ArrayList<>();
        for (Path file : files()) {
            String key = keyOf(file);
            if (key == null) {
                continue;
            }
            // The heading lives inside the file, so the title is read from it — publishing a new
            // document needs no code change.
            String text = "";
            Instant updatedAt = Instant.EPOCH;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
                updatedAt = Files.getLastModifiedTime(file).toInstant();
            } catch (IOException ex) {
                // Cosmetic only: an unreadable file still lists under its key.
                log.warn("Unreadable legal document {}: {}", file, ex.toString());
            }
            String heading = headingOf(text);
            documents.add(new LegalDocumentSummary(key, heading == null ? key : heading, updatedAt));
        }
        return List.copyOf(documents);
    }

    /**
     * @param key requested document key
     * @return the document's current UTF-8 text without its heading line, or {@code null} when the
     *         key is malformed or nothing publishes it
     * @throws IOException when the file exists but cannot be read
     */
    public String read(String key) throws IOException {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            return null;
        }
        for (Path file : files()) {
            if (key.equals(keyOf(file))) {
                return bodyOf(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return null;
    }

    /**
     * @param text whole document
     * @return the file's first non-blank line when it can serve as a heading: at most
     *         {@link #MAX_TITLE_LENGTH} characters and free of control / format characters (an RTL
     *         override or ANSI escape would let the file spoof the link text), else {@code null}
     */
    private static String headingOf(String text) {
        for (String line : text.split("\\R")) {
            String candidate = clean(line);
            if (candidate.isEmpty()) {
                continue;
            }
            boolean spoofable = candidate.codePoints().anyMatch(cp ->
                    Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT);
            return !spoofable && candidate.length() <= MAX_TITLE_LENGTH ? candidate : null;
        }
        return null;
    }

    /**
     * @param line one line of a document
     * @return the line without surrounding whitespace and a leading byte-order mark; a text editor
     *         that saves UTF-8 <em>with</em> BOM would otherwise hide the heading behind
     *         {@code \uFEFF}
     */
    private static String clean(String line) {
        String candidate = line.strip();
        return candidate.startsWith("\uFEFF") ? candidate.substring(1).strip() : candidate;
    }

    /**
     * @param text whole document
     * @return {@code text} without its heading line and the blank lines around it, so the title is
     *         printed once (as the page heading); unchanged when the first line is not a heading
     */
    private static String bodyOf(String text) {
        String[] lines = text.split("\\R", -1);
        String heading = headingOf(text);
        int start = 0;
        while (start < lines.length && lines[start].isBlank()) {
            start++;
        }
        if (heading == null || start == lines.length || !clean(lines[start]).equals(heading)) {
            return text;
        }
        // Drop the heading line itself, then the blank line(s) the operator left under it.
        start++;
        while (start < lines.length && lines[start].isBlank()) {
            start++;
        }
        return String.join("\n", Arrays.copyOfRange(lines, start, lines.length));
    }

    private List<Path> files() throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            // NOFOLLOW: a symlink in the mounted directory would otherwise publish an arbitrary
            // readable file (e.g. /etc/passwd) as a "policy document".
            return entries
                    .filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .toList();
        }
    }

    /**
     * @return the key the file publishes (its name without {@code .txt}), or {@code null}
     */
    private static String keyOf(Path file) {
        String name = file.getFileName().toString();
        if (!name.endsWith(EXTENSION)) {
            return null;
        }
        String key = name.substring(0, name.length() - EXTENSION.length());
        return KEY_PATTERN.matcher(key).matches() ? key : null;
    }
}
