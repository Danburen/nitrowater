package cn.nitrowater.server.service.judge;

import java.util.Arrays;

/**
 * Compares a program's stdout against the expected output.
 *
 * <p>Normalisation first: {@code \r\n}/\r → {@code \n} and trailing whitespace stripped, so
 * platform line endings and a missing final newline do not cause a false WA.</p>
 */
public final class OutputComparator {

    /** Comparison strategy, selectable per problem via {@code judge_config.compare}. */
    public enum Strategy {
        /** Whole output must match after normalisation. */
        EXACT,
        /** Whitespace-separated tokens must match. */
        TOKEN,
        /** Per-line match after trimming each line. */
        LINES
    }

    private OutputComparator() {
    }

    /**
     * @param expected expected output (may be {@code null})
     * @param actual program stdout (may be {@code null})
     * @param strategy comparison strategy
     * @return whether the outputs match
     */
    public static boolean matches(String expected, String actual, Strategy strategy) {
        String e = normalize(expected);
        String a = normalize(actual);
        return switch (strategy) {
            case TOKEN -> Arrays.equals(tokens(e), tokens(a));
            case LINES -> Arrays.equals(trimmedLines(e), trimmedLines(a));
            default -> e.equals(a);
        };
    }

    /**
     * @param name strategy name (case-insensitive)
     * @return the matching strategy, defaulting to {@link Strategy#EXACT}
     */
    public static Strategy strategyOf(String name) {
        if (name == null || name.isBlank()) {
            return Strategy.EXACT;
        }
        try {
            return Strategy.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Strategy.EXACT;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
    }

    private static String[] tokens(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }

    private static String[] trimmedLines(String value) {
        return Arrays.stream(value.split("\n", -1)).map(String::strip).toArray(String[]::new);
    }
}
