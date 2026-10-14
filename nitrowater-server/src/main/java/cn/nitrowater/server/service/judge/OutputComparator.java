package cn.nitrowater.server.service.judge;

import cn.nitrowater.lib.utils.JsonUtil;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Compares a program's stdout against the expected output.
 *
 * <p>Normalisation first: {@code \r\n}/\r → {@code \n} and trailing whitespace stripped, so
 * platform line endings and a missing final newline do not cause a false WA.</p>
 *
 * <p>{@link Strategy#JSON} compares two JSON values structurally (used by the LeetCode-style
 * function harness, where each case's expected/actual is a JSON value), ignoring key order and
 * comparing numbers within a small epsilon.</p>
 */
public final class OutputComparator {

    /** Comparison strategy, selectable per problem via {@code judge_config.compare}. */
    public enum Strategy {
        /** Whole output must match after normalisation. */
        EXACT,
        /** Whitespace-separated tokens must match. */
        TOKEN,
        /** Per-line match after trimming each line. */
        LINES,
        /** Structural JSON equality of a single JSON value (order-insensitive for objects). */
        JSON
    }

    private static final double EPSILON = 1e-6;

    private OutputComparator() {
    }

    /**
     * @param expected expected output (may be {@code null})
     * @param actual program stdout (may be {@code null})
     * @param strategy comparison strategy
     * @return whether the outputs match
     */
    public static boolean matches(String expected, String actual, Strategy strategy) {
        if (strategy == Strategy.JSON) {
            return jsonEquals(expected, actual);
        }
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

    private static boolean jsonEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return normalize(expected).equals(normalize(actual));
        }
        try {
            ObjectMapper mapper = JsonUtil.getMapper();
            return deepEquals(mapper.readValue(expected.trim(), Object.class),
                    mapper.readValue(actual.trim(), Object.class));
        } catch (Exception ex) {
            return normalize(expected).equals(normalize(actual));
        }
    }

    private static boolean deepEquals(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected instanceof Number e && actual instanceof Number a) {
            return Math.abs(e.doubleValue() - a.doubleValue()) <= EPSILON;
        }
        if (expected instanceof List<?> e && actual instanceof List<?> a) {
            if (e.size() != a.size()) {
                return false;
            }
            for (int i = 0; i < e.size(); i++) {
                if (!deepEquals(e.get(i), a.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (expected instanceof Map<?, ?> e && actual instanceof Map<?, ?> a) {
            if (!e.keySet().equals(a.keySet())) {
                return false;
            }
            for (Object key : e.keySet()) {
                if (!deepEquals(e.get(key), a.get(key))) {
                    return false;
                }
            }
            return true;
        }
        return expected.equals(actual);
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
