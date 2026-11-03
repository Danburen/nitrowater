package cn.nitrowater.server.service.ai.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the dictionary that the content gate depends on: matching, masking, the allow list and
 * graceful behaviour when a configured word list is missing.
 */
class ModerationDictionaryTest {

    @Test
    void findsAndMasksViolationWords() {
        ModerationDictionary dictionary = new ModerationDictionary(List.of("代开发票", "洗钱"), List.of());

        assertThat(dictionary.size()).isEqualTo(2);
        assertThat(dictionary.find("帮我代开发票")).isNotEmpty();
        // The admin console and the message badge tell the user the text was "replaced with *";
        // keep the mask marker honest.
        assertThat(dictionary.mask("帮我代开发票")).doesNotContain("代开发票").contains("*");
        assertThat(dictionary.find("怎么安装插件")).isEmpty();
        assertThat(dictionary.mask("怎么安装插件")).isEqualTo("怎么安装插件");
    }

    @Test
    void allowListSuppressesAMatch() {
        ModerationDictionary dictionary = new ModerationDictionary(List.of("赌博"), List.of("赌博"));

        assertThat(dictionary.find("推荐赌博网站")).isEmpty();
    }

    @Test
    void ignoresCaseAndWidth() {
        ModerationDictionary dictionary = new ModerationDictionary(List.of("ScamSite"), List.of());

        assertThat(dictionary.find("visit scamsite now")).isNotEmpty();
        assertThat(dictionary.find("visit ＳＣＡＭＳＩＴＥ now")).isNotEmpty();
    }

    @Test
    void missingWordListDegradesToNoOp() {
        ModerationDictionary dictionary = ModerationDictionary.load(
                List.of("classpath:ai/moderation/does-not-exist.txt"), List.of());

        assertThat(dictionary.size()).isZero();
        assertThat(dictionary.find("代开发票")).isEmpty();
        assertThat(dictionary.mask("代开发票")).isEqualTo("代开发票");
    }

    @Test
    void loadsTheShippedBaselineList() {
        ModerationDictionary dictionary = ModerationDictionary.load(
                List.of("classpath:ai/moderation/words.txt"),
                List.of("classpath:ai/moderation/allow.txt"));

        assertThat(dictionary.size()).isGreaterThan(10);
        assertThat(dictionary.find("帮我代开发票")).isNotEmpty();
    }
}
