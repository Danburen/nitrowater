package cn.nitrowater.server.service.ai.model.moderation;

import java.util.regex.Pattern;

/**
 * Regex gates for text that is stored and re-injected into later prompts (memories, profile
 * preferences, conversation summaries).
 *
 * <p>A stored string is replayed in every following turn, so it must stay a statement of fact: a
 * credential must never be persisted, and an "ignore the rules above" shape would turn the stored
 * text into a permanent prompt injection. These checks are the cheap, high-yield subset of that;
 * deeper semantic judging is out of scope.</p>
 */
public final class ContentGuards {

    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(password|passwd|pwd|密码|口令|api[_-]?key|secret|token|bearer\\s+|"
                    + "private[_-]?key|access[_-]?key|sk-[a-z0-9]{8,}|ghp_[a-z0-9]{20,}|"
                    + "akia[0-9a-z]{16})");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)");
    private static final Pattern INSTRUCTION = Pattern.compile(
            "(?i)(<\\|im_start\\|>|<\\|im_end\\|>|<\\|system\\|>|\\[/?(inst|sys)\\]|<<sys>>)"
                    + "|(忽略|无视|忘记|跳过)(以上|之前|上述|前面|先前|你的)(所有)?(指令|规则|设定|限制|提示)"
                    + "|ignore\\s+(all\\s+)?(previous|above|prior)\\s+(instruction|rule|prompt)s?"
                    + "|disregard\\s+(all\\s+)?(previous|above|prior)");

    /**
     * @param text candidate text
     * @return whether it looks like it carries a credential or an identifier
     */
    public static boolean hasSensitive(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return SENSITIVE.matcher(text).find()
                || PHONE.matcher(text).find()
                || ID_CARD.matcher(text).find();
    }

    /**
     * @param text candidate text
     * @return whether it looks like a prompt-injection attempt rather than a fact
     */
    public static boolean hasInstruction(String text) {
        return text != null && !text.isEmpty() && INSTRUCTION.matcher(text).find();
    }

    /**
     * @param text candidate text
     * @return whether the text must not be persisted and replayed
     */
    public static boolean unsafe(String text) {
        return hasSensitive(text) || hasInstruction(text);
    }

    private ContentGuards() {
    }
}
