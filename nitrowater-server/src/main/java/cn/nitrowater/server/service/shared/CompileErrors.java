package cn.nitrowater.server.service.shared;

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
     * @param diagnostic multi-line compiler output (may be {@code null})
     * @return the first meaningful line with leading paths stripped, or {@code null}
     */
    public static String oneLine(String diagnostic) {
        if (diagnostic == null) {
            return null;
        }
        for (String raw : diagnostic.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("chmod:") || line.equals("error: compilation failed")) {
                continue;
            }
            line = line.replaceAll("\"[^\"]*/", "\"");
            return line.length() <= 300 ? line : line.substring(0, 300);
        }
        return null;
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
