package cn.nitrowater.lib.utils.generator;

/**
 * Strategy that romanizes arbitrary text (CJK, accented latin, ...) into latin-ish text suitable
 * for slug generation.
 *
 * <p>Pluggable on purpose: an offline pinyin transliterator today, a machine-translation-backed
 * implementation later — consumers ({@link IdentifierGenerator}) never change.</p>
 */
@FunctionalInterface
public interface Romanizer {

    /**
     * @param raw raw text (may be {@code null} or blank)
     * @return latin-ish text; may be empty when nothing could be romanized
     */
    String romanize(String raw);
}
