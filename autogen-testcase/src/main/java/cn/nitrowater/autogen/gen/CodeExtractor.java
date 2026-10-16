package cn.nitrowater.autogen.gen;

/**
 * Extracts the Java source from a model response.
 *
 * <p>Even when the prompt asks for bare code, chat models frequently wrap the
 * answer in Markdown fences. This normaliser strips such fences (and any
 * surrounding prose) so the compile check sees real Java.
 */
public final class CodeExtractor {

    private CodeExtractor() {
    }

    public static String extractJava(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").strip();

        int fenced = text.indexOf("```");
        if (fenced >= 0) {
            int bodyStart = text.indexOf('\n', fenced);
            int end = bodyStart < 0 ? -1 : text.indexOf("```", bodyStart);
            if (bodyStart >= 0 && end > bodyStart) {
                return text.substring(bodyStart + 1, end).strip();
            }
        }

        // No fences: fall back to the first line that looks like a Java type
        // declaration, dropping any leading prose.
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.startsWith("package ") || line.startsWith("import ")
                    || line.startsWith("public ") || line.startsWith("class ")
                    || line.startsWith("final ")) {
                return String.join("\n", java.util.Arrays.copyOfRange(lines, i, lines.length)).strip();
            }
        }
        return text;
    }
}
