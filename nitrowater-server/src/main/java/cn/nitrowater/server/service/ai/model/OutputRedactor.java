package cn.nitrowater.server.service.ai.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks credentials and contact / internal-network identifiers in an answer before it is persisted
 * or streamed.
 *
 * <p>Deliberately independent of the moderation posture: echoing an API key or an internal address
 * back to the user is a data-hygiene defect, not a content-policy decision, so this runs in every
 * mode including {@link ModerationMode#OFF}. The patterns are specific (prefixed tokens, real key
 * shapes, RFC 1918 ranges) so ordinary technical prose is left alone.</p>
 */
public final class OutputRedactor {

    /** Rules run in order; a rule never re-processes text produced by an earlier rule. */
    private static final List<Rule> RULES = List.of(
            new Rule("private_key",
                    Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
                    matcher -> "***"),
            new Rule("jwt",
                    Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"),
                    matcher -> "eyJ***"),
            new Rule("openai_key",
                    Pattern.compile("\\bsk-[A-Za-z0-9_-]{16,}\\b"),
                    matcher -> "sk-***"),
            new Rule("aws_key",
                    Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
                    matcher -> "AKIA***"),
            new Rule("github_token",
                    Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{20,}\\b"),
                    matcher -> "***"),
            new Rule("credential",
                    Pattern.compile("(?i)\\b(password|passwd|pwd|secret|api[_-]?key|access[_-]?token|token)\\b"
                            + "\\s*[=:]\\s*[\"']?[A-Za-z0-9_\\-./+=]{8,}[\"']?"),
                    matcher -> matcher.group(1) + "=***"),
            new Rule("email",
                    Pattern.compile("(?<![\\w.+-])[\\w.+-]+@([\\w-]+\\.[\\w.-]{2,})"),
                    matcher -> "***@" + matcher.group(1)),
            new Rule("phone",
                    Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)"),
                    matcher -> "***"),
            new Rule("private_ip",
                    Pattern.compile("\\b(?:10(?:\\.\\d{1,3}){3}|192\\.168(?:\\.\\d{1,3}){2}"
                            + "|172\\.(?:1[6-9]|2\\d|3[01])(?:\\.\\d{1,3}){2})\\b"),
                    matcher -> "***"));

    /**
     * @param text text to clean, may be {@code null}
     * @return the cleaned text plus the kinds that matched (empty when nothing changed)
     */
    public static Result redact(String text) {
        if (text == null || text.isEmpty()) {
            return new Result(text, List.of());
        }
        String current = text;
        List<String> kinds = new ArrayList<>(2);
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(current);
            StringBuilder replaced = null;
            while (matcher.find()) {
                if (replaced == null) {
                    replaced = new StringBuilder(current.length());
                }
                matcher.appendReplacement(replaced, Matcher.quoteReplacement(rule.replacement().apply(matcher)));
            }
            if (replaced == null) {
                continue;
            }
            matcher.appendTail(replaced);
            current = replaced.toString();
            if (!kinds.contains(rule.kind())) {
                kinds.add(rule.kind());
            }
        }
        return new Result(current, List.copyOf(kinds));
    }

    /**
     * @param text cleaned text
     * @param kinds matched rule names, e.g. {@code openai_key, private_ip}
     */
    public record Result(String text, List<String> kinds) {

        /** @return whether at least one rule matched */
        public boolean changed() {
            return !kinds.isEmpty();
        }
    }

    private record Rule(String kind, Pattern pattern, Function<Matcher, String> replacement) {
    }

    private OutputRedactor() {
    }
}
