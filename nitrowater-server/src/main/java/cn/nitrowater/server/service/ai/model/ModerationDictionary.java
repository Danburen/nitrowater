package cn.nitrowater.server.service.ai.model;

import com.github.houbb.sensitive.word.api.IWordAllow;
import com.github.houbb.sensitive.word.api.IWordDeny;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * The violation-word matcher: a DFA (trie) built by {@code sensitive-word} over a dictionary we
 * own.
 *
 * <p>The library's built-in word list is intentionally <em>not</em> used. It is tuned for social
 * feeds, so it would both bury a technical Minecraft assistant in false positives and ship a
 * political vocabulary inside our build artifact; supplying the word list ourselves keeps the
 * dictionary auditable and lets an operator override it at deploy time.</p>
 *
 * <p>Normalisation is on (case, full/half width, traditional/simplified, repeated characters),
 * which is what catches the cheap evasions ("代 开 发 票" style spacing still slips through, but
 * that is an accepted limitation of any dictionary).</p>
 */
public final class ModerationDictionary {

    private static final Logger LOG = LoggerFactory.getLogger(ModerationDictionary.class);
    private static final String CLASSPATH_PREFIX = "classpath:";
    /** Single-character entries produce far more noise than signal. */
    private static final int MIN_WORD_LENGTH = 2;

    private final SensitiveWordBs wordBs;
    private final int size;

    /**
     * @param dictLocations violation word lists
     * @param allowLocations allow lists
     * @return a ready-to-use dictionary (empty when no list could be read)
     */
    public static ModerationDictionary load(List<String> dictLocations, List<String> allowLocations) {
        return new ModerationDictionary(words(dictLocations), words(allowLocations));
    }

    /**
     * @param deny violation words
     * @param allow terms that must never be reported, even if they contain a violation word
     */
    ModerationDictionary(List<String> deny, List<String> allow) {
        List<String> denyList = List.copyOf(deny);
        List<String> allowList = List.copyOf(allow);
        this.size = denyList.size();
        IWordDeny denySource = () -> denyList;
        IWordAllow allowSource = () -> allowList;
        this.wordBs = SensitiveWordBs.newInstance()
                .ignoreCase(true)
                .ignoreWidth(true)
                .ignoreNumStyle(true)
                .ignoreChineseStyle(true)
                .ignoreEnglishStyle(true)
                .ignoreRepeat(true)
                .wordFailFast(false)
                .wordDeny(denySource)
                .wordAllow(allowSource)
                .init();
    }

    /** @return number of violation words loaded */
    public int size() {
        return size;
    }

    /**
     * @param text arbitrary user or model text
     * @return the distinct matched terms, empty when nothing matched
     */
    public List<String> find(String text) {
        if (size == 0 || text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> matches = wordBs.findAll(text);
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(matches));
    }

    /**
     * @param text arbitrary user or model text
     * @return the text with every matched term masked, unchanged when nothing matched
     */
    public String mask(String text) {
        if (size == 0 || text == null || text.isEmpty()) {
            return text;
        }
        return wordBs.replace(text);
    }

    private static List<String> words(List<String> locations) {
        LinkedHashSet<String> collected = new LinkedHashSet<>();
        for (String location : locations) {
            if (StringUtil.isBlank(location)) {
                continue;
            }
            String trimmed = location.trim();
            try (InputStream in = open(trimmed)) {
                if (in == null) {
                    LOG.warn("Moderation word list not found: {}", trimmed);
                    continue;
                }
                read(in, collected);
            } catch (IOException ex) {
                LOG.warn("Failed to read moderation word list {}: {}", trimmed, ex.toString());
            }
        }
        return List.copyOf(collected);
    }

    private static void read(InputStream in, LinkedHashSet<String> collected) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (word.isEmpty() || word.startsWith("#") || word.length() < MIN_WORD_LENGTH) {
                    continue;
                }
                collected.add(word);
            }
        }
    }

    /**
     * @param location {@code classpath:...} or a filesystem path (an optional {@code file:} prefix
     *        is accepted)
     * @return the stream, or {@code null} when the resource does not exist
     */
    private static InputStream open(String location) throws IOException {
        if (location.startsWith(CLASSPATH_PREFIX)) {
            String path = location.substring(CLASSPATH_PREFIX.length());
            return ModerationDictionary.class.getResourceAsStream(path.startsWith("/") ? path : "/" + path);
        }
        String raw = location.startsWith("file:") ? location.substring("file:".length()) : location;
        Path path = Paths.get(raw);
        return Files.isReadable(path) ? Files.newInputStream(path) : null;
    }
}
