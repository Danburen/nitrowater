package cn.nitrowater.core.web.legal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Resolves and reads the operator's legal texts (privacy policy / terms of service).
 *
 * <p>The texts are <em>host state</em>, not build inputs: the SSO account service and the BFF
 * both read the same mounted directory ({@code /etc/nitrowater/legal} in Docker,
 * {@code deploy/content/legal} in dev), so a deployment has exactly one copy and one version per
 * document. Nothing is cached — an edit is visible on the very next request.</p>
 *
 * <p>Only {@value #PRIVACY} and {@value #TERMS} are addressable. The requested name is never used
 * as a path directly, which removes path traversal as a category. This class carries no Spring
 * annotations on purpose: the account service excludes {@code cn.nitrowater.core.web} from
 * component scanning, and both services own their public endpoint declaration.</p>
 */
public final class LegalDocuments {

    /** Privacy policy document name; mapped to {@code privacy.txt}. */
    public static final String PRIVACY = "privacy";

    /** Terms of service document name; mapped to {@code terms.txt}. */
    public static final String TERMS = "terms";

    /** Whitelisted document names. */
    public static final Set<String> NAMES = Set.of(PRIVACY, TERMS);

    /**
     * Locations tried when no directory is configured, so {@code bootRun} works from both the
     * repository root and the module directory without environment variables.
     */
    private static final List<String> DEFAULT_CANDIDATES = List.of(
            "deploy/content/legal",
            "../deploy/content/legal",
            "/etc/nitrowater/legal");

    private LegalDocuments() {
    }

    /**
     * Resolves the legal directory.
     *
     * @param configured value of {@code app.legal.dir}; blank means auto-detect
     * @return the configured path when present, otherwise the first existing default candidate,
     *         otherwise the first candidate so a missing directory deterministically yields 404
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
     * @param document requested name
     * @return {@code true} when the name is one of {@link #NAMES}
     */
    public static boolean isSupported(String document) {
        return document != null && NAMES.contains(document);
    }

    /**
     * Resolves the file of a supported document inside the legal directory.
     *
     * @param directory legal directory
     * @param document  requested name
     * @return the readable file, or {@code null} when the name is unsupported, the file is absent,
     *         or it is not a regular readable file (i.e. "not uploaded yet")
     */
    public static Path fileOf(Path directory, String document) {
        if (!isSupported(document)) {
            return null;
        }
        Path file = directory.resolve(document + ".txt").normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file) || !Files.isReadable(file)) {
            return null;
        }
        return file;
    }

    /**
     * Reads a whitelisted document as UTF-8.
     *
     * @param directory legal directory
     * @param document  requested name
     * @return the file's current text, or {@code null} when {@link #fileOf} finds no file
     * @throws IOException when the file exists but cannot be read
     */
    public static String read(Path directory, String document) throws IOException {
        Path file = fileOf(directory, document);
        return file == null ? null : Files.readString(file, StandardCharsets.UTF_8);
    }
}
