package cn.nitrowater.server.ai.moderation;

/**
 * Detects an answer that echoes the system prompt.
 *
 * <p>Lightweight by design: both system prompts carry a {@link #CANARY} marker that appears in no
 * other content, so a hit is a certain leak rather than a heuristic. A model that paraphrases the
 * prompt without the marker is not caught — that needs a classifier and is out of scope.</p>
 */
public final class PromptLeakGuard {

    /** Marker embedded in both system prompts; any occurrence in an answer means the prompt leaked. */
    public static final String CANARY = "NITROBOT-INTERNAL-PROMPT-7F3A";

    /** Audit label recorded when a leak is detected. */
    public static final String HIT = "system_prompt";

    /** Answer returned instead of leaked text when the posture enforces. */
    public static final String REFUSAL =
            "抱歉，这涉及我的内部配置，我不能提供。如果你需要帮助，请告诉我具体想解决的问题。";

    /**
     * @param text finished answer, may be {@code null}
     * @return whether the answer contains the system-prompt marker
     */
    public static boolean leaked(String text) {
        return text != null && text.contains(CANARY);
    }

    private PromptLeakGuard() {
    }
}
