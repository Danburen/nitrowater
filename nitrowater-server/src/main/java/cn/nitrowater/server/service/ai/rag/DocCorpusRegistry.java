package cn.nitrowater.server.service.ai.rag;

import cn.nitrowater.lib.utils.StringUtil;
import cn.nitrowater.server.config.ai.AiProperties;
import cn.nitrowater.server.service.ai.model.rag.DocCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Discovers the documentation corpora that feed the RAG index.
 *
 * <p>The corpus root is a plain directory ({@code app.ai.docs-root}, environment variable
 * {@code AI_DOCS_ROOT}). Every sub-directory of it is one corpus, named after the directory; an
 * optional {@code _corpus.yml} inside it supplies the human metadata:</p>
 *
 * <pre>
 * description: VeloChatX 插件文档（安装、命令与权限、占位符、配置、排障）
 * public-base-url: /plugin/velochatx/
 * </pre>
 *
 * <p>This is the whole contract: publishing documentation means dropping a directory into the
 * mount, not editing {@code application.yml} and redeploying the jar. The registry caches the last
 * scan; {@link #reload()} re-reads the tree so a freshly mounted corpus is picked up without a
 * restart.</p>
 */
@Component
public class DocCorpusRegistry {

    private static final Logger log = LoggerFactory.getLogger(DocCorpusRegistry.class);

    /** Optional per-corpus metadata file inside a corpus directory. */
    public static final String CORPUS_META_FILE = "_corpus.yml";

    private static final String FILE_PREFIX = "file:";
    private static final String CLASSPATH_PREFIX = "classpath:";

    /**
     * Conventional mounts, tried in order when {@code app.ai.docs-root} is blank: a repo checkout
     * (bootRun pins the working directory to the repo root), a deploy checkout, then the container
     * mount used by docker-compose.
     */
    private static final List<String> DEFAULT_ROOTS = List.of(
            "deploy/content/docs",
            "../deploy/content/docs",
            "/etc/nitrowater/docs");

    private final AiProperties properties;

    private volatile Snapshot snapshot;

    public DocCorpusRegistry(AiProperties properties) {
        this.properties = properties;
    }

    /**
     * @return the directory the corpora were discovered from, or {@code null} when none exists
     */
    public Path root() {
        return snapshot().root();
    }

    /**
     * @return every discovered corpus, sorted by name; empty when the root is missing or empty
     */
    public List<DocCollection> collections() {
        return snapshot().collections();
    }

    /**
     * Re-reads the corpus tree, picking up directories and {@code _corpus.yml} changes.
     *
     * @return the freshly discovered corpora
     */
    public List<DocCollection> reload() {
        Snapshot scanned = scan();
        snapshot = scanned;
        log.info("Documentation corpora reloaded from {}: {}", describeRoot(scanned.root()),
                scanned.collections().stream().map(DocCollection::corpus).toList());
        return scanned.collections();
    }

    private Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (snapshot == null) {
                snapshot = scan();
                log.info("Documentation corpora discovered under {}: {}", describeRoot(snapshot.root()),
                        snapshot.collections().stream().map(DocCollection::corpus).toList());
            }
            return snapshot;
        }
    }

    private Snapshot scan() {
        Path root = resolveRoot();
        if (root == null) {
            log.warn("No documentation root configured (app.ai.docs-root) and none of {} exists; the "
                    + "assistant will run without documents", DEFAULT_ROOTS);
            return new Snapshot(null, List.of());
        }
        if (!Files.isDirectory(root)) {
            log.warn("Documentation root does not exist: {}", root);
            return new Snapshot(root, List.of());
        }
        List<DocCollection> collections = new ArrayList<>();
        try (Stream<Path> entries = Files.list(root)) {
            entries.filter(Files::isDirectory)
                    .filter(dir -> !isHidden(dir.getFileName().toString()))
                    .sorted(Comparator.comparing(dir -> dir.getFileName().toString()))
                    .forEach(dir -> {
                        if (containsMarkdown(dir)) {
                            collections.add(toCollection(dir));
                        } else {
                            log.warn("Skipping corpus directory without markdown: {}", dir);
                        }
                    });
        } catch (IOException ex) {
            log.warn("Cannot list documentation root {}: {}", root, ex.getMessage());
            return new Snapshot(root, List.of());
        }
        return new Snapshot(root, List.copyOf(collections));
    }

    private Path resolveRoot() {
        String configured = properties.docsRoot();
        if (!StringUtil.isBlank(configured)) {
            String raw = configured.startsWith(FILE_PREFIX)
                    ? configured.substring(FILE_PREFIX.length())
                    : configured;
            if (raw.startsWith(CLASSPATH_PREFIX)) {
                log.warn("app.ai.docs-root no longer supports classpath locations ({}); corpus markdown "
                        + "must live in a mounted directory", configured);
                return null;
            }
            return Path.of(raw).toAbsolutePath().normalize();
        }
        for (String candidate : DEFAULT_ROOTS) {
            Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private DocCollection toCollection(Path dir) {
        String corpus = dir.getFileName().toString();
        Properties meta = readMetadata(dir.resolve(CORPUS_META_FILE));
        String description = text(meta, "description");
        String publicBaseUrl = text(meta, "public-base-url");
        return new DocCollection(corpus, dir.toAbsolutePath().toString(), publicBaseUrl, description);
    }

    /**
     * @param file optional {@code _corpus.yml}
     * @return its flat key/value pairs, or an empty set when the file is absent or unreadable
     */
    private Properties readMetadata(Path file) {
        if (!Files.isRegularFile(file)) {
            return new Properties();
        }
        try {
            YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
            factory.setResources(new FileSystemResource(file));
            Properties properties = factory.getObject();
            return properties == null ? new Properties() : properties;
        } catch (RuntimeException ex) {
            log.warn("Ignoring unreadable corpus metadata {}: {}", file, ex.getMessage());
            return new Properties();
        }
    }

    private static String text(Properties meta, String key) {
        String value = meta.getProperty(key);
        return value == null ? "" : value.trim();
    }

    private static boolean containsMarkdown(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".md"))
                    .anyMatch(path -> !hasHiddenSegment(dir, path));
        } catch (IOException ex) {
            log.warn("Cannot inspect corpus directory {}: {}", dir, ex.getMessage());
            return false;
        }
    }

    private static boolean hasHiddenSegment(Path root, Path path) {
        for (Path segment : root.relativize(path)) {
            if (isHidden(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHidden(String name) {
        return name.startsWith(".") || name.equals("node_modules");
    }

    private static String describeRoot(Path root) {
        return root == null ? "(none)" : root.toString();
    }

    /**
     * @param root directory the corpora came from, {@code null} when nothing was configured
     * @param collections discovered corpora, sorted by name
     */
    private record Snapshot(Path root, List<DocCollection> collections) {
    }
}
