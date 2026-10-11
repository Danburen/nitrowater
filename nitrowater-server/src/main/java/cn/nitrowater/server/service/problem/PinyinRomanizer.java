package cn.nitrowater.server.service.problem;

import cn.nitrowater.lib.utils.generator.Romanizer;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.springframework.stereotype.Component;

/**
 * Offline {@link Romanizer}: transliterates CJK characters to Hanyu Pinyin (without tone marks) and
 * passes every other character through unchanged.
 *
 * <p>Deterministic and dependency-light (pinyin4j), used instead of the heavyweight ICU4J
 * transliterator that WaterFun relies on. Polyphonic characters use pinyin4j's first reading;
 * syllables are separated by {@code -} so the final slug stays human-readable
 * (e.g. {@code 两数之和 -> liang-shu-zhi-he}).</p>
 */
@Component
public class PinyinRomanizer implements Romanizer {

    private static final HanyuPinyinOutputFormat FORMAT = buildFormat();

    @Override
    public String romanize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() * 2);
        for (char c : raw.toCharArray()) {
            String syllable = firstReading(c);
            if (syllable == null || syllable.isBlank()) {
                out.append(c);
            } else {
                out.append(syllable).append('-');
            }
        }
        return out.toString();
    }

    /** @return the first Hanyu Pinyin reading of {@code c}, or {@code null} when it is not CJK */
    private static String firstReading(char c) {
        try {
            String[] readings = PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
            return (readings == null || readings.length == 0) ? null : readings[0];
        } catch (BadHanyuPinyinOutputFormatCombination ex) {
            return null;
        }
    }

    private static HanyuPinyinOutputFormat buildFormat() {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
        return format;
    }
}
