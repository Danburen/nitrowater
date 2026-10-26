package cn.nitrowater.server.service.judge.harness;

import cn.nitrowater.server.service.judge.JudgeSignature;

/**
 * Helpers shared by every harness generator (Java, Python and the C family).
 *
 * <p>Each harness used to carry its own copy of these; they must agree exactly — the type test
 * decides whether a node class is injected, and the newline count is what rewinds compiler
 * diagnostics to the user's own line numbers.</p>
 */
final class HarnessSupport {

    private HarnessSupport() {
    }

    /** Strips insignificant whitespace so {@code "int []"} and {@code "int[]"} compare equal. */
    static String normalized(String type) {
        return type.replace(" ", "");
    }

    /** @return whether the signature uses {@code type} as a parameter or as the return type */
    static boolean usesType(JudgeSignature signature, String type) {
        String wanted = normalized(type);
        if (wanted.equals(normalized(signature.returnType()))) {
            return true;
        }
        for (JudgeSignature.Param param : signature.params()) {
            if (wanted.equals(normalized(param.type()))) {
                return true;
            }
        }
        return false;
    }

    /** @return how many lines precede the user's own code, used to rewind diagnostic line numbers */
    static int countNewlines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }
}
