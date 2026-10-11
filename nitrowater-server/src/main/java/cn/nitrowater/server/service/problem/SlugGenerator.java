package cn.nitrowater.server.service.problem;

import cn.nitrowater.lib.common.constratin.UniquenessChecker;
import cn.nitrowater.lib.utils.codec.HashUtil;
import cn.nitrowater.lib.utils.generator.IdentifierGenerator;
import cn.nitrowater.lib.utils.generator.Romanizer;
import org.springframework.stereotype.Component;

/**
 * Default {@link IdentifierGenerator}: turns arbitrary text into a unique kebab-case slug (or an
 * uppercase SNAKE_CASE code) using a pluggable {@link Romanizer} for the latin transliteration.
 *
 * <p>Mirrors WaterFun's {@code ICUSlugGenerator} contract but swaps the ICU transliterator for an
 * injected {@link Romanizer} (offline pinyin today, machine translation later). Collisions get a
 * short base62 suffix appended, retried a few times.</p>
 */
@Component
public class SlugGenerator implements IdentifierGenerator {

    private static final int MAX_ATTEMPTS = 3;

    private final Romanizer romanizer;

    public SlugGenerator(Romanizer romanizer) {
        this.romanizer = romanizer;
    }

    @Override
    public String generateSlug(String raw, UniquenessChecker checker) {
        return uniquify(toSlug(raw), checker);
    }

    @Override
    public String generateCode(String raw, UniquenessChecker checker) {
        return uniquify(toCode(raw), checker);
    }

    @Override
    public String uniquify(String base, UniquenessChecker checker) {
        if (!checker.existsWithUniqueIdentify(base)) {
            return base;
        }
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = base + "-" + HashUtil.next62_6();
            if (!checker.existsWithUniqueIdentify(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Slug conflict after " + MAX_ATTEMPTS + " retries: " + base);
    }

    @Override
    public String fromCode(String code, String fallback, UniquenessChecker checker) {
        if (code == null || code.isBlank()) {
            return generateCode(fallback, checker);
        }
        return checker.existsWithUniqueIdentify(code) ? generateCode(fallback, checker) : code;
    }

    @Override
    public String fromSlug(String slug, String fallback, UniquenessChecker checker) {
        if (slug == null || slug.isBlank()) {
            return generateSlug(fallback, checker);
        }
        return checker.existsWithUniqueIdentify(slug) ? generateSlug(fallback, checker) : slug;
    }

    /**
     * @param raw raw text (e.g. a problem title)
     * @return a lowercase kebab-case slug; {@code "untitled"} when nothing usable remains
     */
    public String toSlug(String raw) {
        String slug = separate(latin(raw), '-')
                .toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return slug.isEmpty() ? "untitled" : slug;
    }

    /**
     * @param raw raw text
     * @return an uppercase SNAKE_CASE identifier; {@code "UNTITLED"} when nothing usable remains
     */
    public String toCode(String raw) {
        String code = separate(latin(raw), '_')
                .toUpperCase()
                .replaceAll("[^A-Z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return code.isEmpty() ? "UNTITLED" : code;
    }

    /** Romanizes {@code raw}, tolerating a {@code null} romanizer result. */
    private String latin(String raw) {
        String romanized = romanizer.romanize(raw);
        return romanized == null ? "" : romanized;
    }

    /** Inserts {@code separator} at camelCase / letter-digit boundaries so words do not run together. */
    private static String separate(String text, char separator) {
        String s = String.valueOf(separator);
        return text
                .replaceAll("(?<=[a-z])(?=[A-Z])", s)
                .replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", s)
                .replaceAll("(?<=[a-zA-Z])(?=[0-9])", s)
                .replaceAll("(?<=[0-9])(?=[a-zA-Z])", s);
    }
}
