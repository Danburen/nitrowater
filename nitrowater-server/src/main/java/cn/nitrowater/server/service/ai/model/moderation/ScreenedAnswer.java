package cn.nitrowater.server.service.ai.model.moderation;

/**
 * Result of the answer screen.
 *
 * @param text the text to return and persist (altered when {@code masked})
 * @param masked whether the safety layer altered the text: credential/PII redaction, a refused
 *     system-prompt leak, or dictionary masking; drives the stored {@code moderated} flag
 */
public record ScreenedAnswer(String text, boolean masked) {
}
