package cn.nitrowater.server.service.shared;

import java.util.regex.Pattern;

/**
 * Recognises whether a sandbox execution failed to compile (as opposed to failing at runtime),
 * across Piston's two shapes: a distinct {@code compile} stage (C/C++/TypeScript) and languages
 * whose compiler output arrives on the {@code run} stage instead (Java, Python — Piston has no
 * compile stage for them).
 *
 * <p>Submitted results show only a coarse verdict; {@link #oneLine} yields a single, path-free
 * line for the rare {@code CE} case.</p>
 */
public final class CompileErrors {

    /**
     * Matches the diagnostic word in a line: the standalone {@code error} ({@code error:},
     * {@code error TS1109:}) or a trailing type name such as {@code SyntaxError}/{@code
     * IndentationError}. The type is matched case-sensitively as {@code ...Error} so unrelated words
     * (e.g. {@code terror}) are not mistaken for it.
     */
    private static final Pattern ERROR_WORD = Pattern.compile("\\berror\\b|[A-Za-z]*Error\\b");

    private static final int MAX_LENGTH = 300;

    private CompileErrors() {
    }

    /**
     * @param pistonLanguage Piston runtime name
     * @param result normalised sandbox result
     * @return the compiler's diagnostic text (line numbers de-offset to the user's own lines) when
     *         the code failed to compile, otherwise {@code null}
     */
    public static String diagnostic(String pistonLanguage, ExecResult result) {
        return diagnostic(pistonLanguage, result, LanguagePrelude.offsetLines(pistonLanguage));
    }

    /**
     * @param pistonLanguage Piston runtime name
     * @param result normalised sandbox result
     * @param offsetLines how many leading lines to subtract from diagnostic line numbers
     * @return the compiler's diagnostic text (line numbers de-offset by {@code offsetLines}) when the
     *         code failed to compile, otherwise {@code null}
     */
    public static String diagnostic(String pistonLanguage, ExecResult result, int offsetLines) {
        ExecResult.Stage compile = result.compile();
        ExecResult.Stage run = result.run();
        if (compile != null && compile.code() != null && compile.code() != 0) {
            // TypeScript reports tsc diagnostics on the run stage, so fall back to it.
            String text = firstNonBlank(compile.stderr(), compile.stdout(), compile.output(),
                    compile.message(), run == null ? null : run.stdout(),
                    run == null ? null : run.stderr());
            return LanguagePrelude.deoffsetBy(pistonLanguage, text, offsetLines);
        }
        if (run != null && looksLikeCompileError(pistonLanguage, run.stderr())) {
            return LanguagePrelude.deoffsetBy(pistonLanguage, firstNonBlank(run.stderr(), run.message()),
                    offsetLines);
        }
        return null;
    }

    /**
     * Collapses compiler diagnostics into a single, path-free line for compact display.
     *
     * <p>Prefers the line that actually carries the error (e.g. Python's {@code SyntaxError: ...} or
     * a C/Java {@code <loc>: error: ...}) over an earlier, less useful header such as Python's
     * {@code File "main.py", line N} or gcc's {@code main.c: In function 'main':}; when no line looks
     * like a diagnostic it falls back to the first meaningful line.</p>
     *
     * @param diagnostic multi-line compiler output (may be {@code null})
     * @return the chosen line with leading paths stripped, or {@code null}
     */
    public static String oneLine(String diagnostic) {
        if (diagnostic == null) {
            return null;
        }
        String first = null;
        String errorLine = null;
        for (String raw : diagnostic.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("chmod:") || line.equals("error: compilation failed")) {
                continue;
            }
            if (first == null) {
                first = line;
            }
            if (errorLine == null && looksLikeErrorLine(line)) {
                errorLine = line;
            }
            if (first != null && errorLine != null) {
                break;
            }
        }
        String chosen = errorLine != null ? errorLine : first;
        if (chosen == null) {
            return null;
        }
        chosen = chosen.replaceAll("\"[^\"]*/", "\"");
        return chosen.length() <= MAX_LENGTH ? chosen : chosen.substring(0, MAX_LENGTH);
    }

    /**
     * @return whether a line is an actual compiler diagnostic — it carries the word {@code error}
     *         (covers {@code error:}, {@code SyntaxError:}, {@code error TS1109:}) and looks
     *         location-bearing (contains a colon), so context/summary lines are not mistaken for it
     */
    private static boolean looksLikeErrorLine(String line) {
        return line.indexOf(':') >= 0 && ERROR_WORD.matcher(line).find();
    }

    private static boolean looksLikeCompileError(String pistonLanguage, String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return false;
        }
        return switch (pistonLanguage) {
            case "java" -> stderr.contains("error:") || stderr.contains("compilation failed");
            case "python" -> stderr.contains("SyntaxError")
                    || stderr.contains("IndentationError") || stderr.contains("TabError");
            default -> false;
        };
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
