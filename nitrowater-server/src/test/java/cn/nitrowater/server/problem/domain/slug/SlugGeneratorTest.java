package cn.nitrowater.server.problem.domain.slug;

import cn.nitrowater.lib.common.constratin.UniquenessChecker;
import cn.nitrowater.server.service.problem.PinyinRomanizer;
import cn.nitrowater.server.service.problem.SlugGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure unit tests for {@link SlugGenerator} (offline pinyin via {@link PinyinRomanizer}). */
class SlugGeneratorTest {

    private final SlugGenerator generator = new SlugGenerator(new PinyinRomanizer());

    @Test
    void romanizesChineseTitleToPinyinSlug() {
        assertEquals("liang-shu-zhi-he", generator.toSlug("两数之和"));
    }

    @Test
    void keepsLatinWordsAndSplitsBoundaries() {
        assertEquals("two-sum", generator.toSlug("Two Sum"));
        assertEquals("a-b-problem-liang-shu-zhi-he", generator.toSlug("A+B Problem 两数之和"));
    }

    @Test
    void fallsBackToUntitledWhenNothingUsableRemains() {
        assertEquals("untitled", generator.toSlug(""));
        assertEquals("untitled", generator.toSlug(null));
        assertEquals("untitled", generator.toSlug("！@#"));
    }

    @Test
    void returnsBaseSlugWhenFree() {
        UniquenessChecker free = value -> false;
        assertEquals("liang-shu-zhi-he", generator.generateSlug("两数之和", free));
    }

    @Test
    void appendsSuffixWhenBaseIsTaken() {
        UniquenessChecker taken = "liang-shu-zhi-he"::equals;
        String slug = generator.generateSlug("两数之和", taken);
        assertNotEquals("liang-shu-zhi-he", slug);
        assertTrue(slug.startsWith("liang-shu-zhi-he-"), slug);
    }
}
