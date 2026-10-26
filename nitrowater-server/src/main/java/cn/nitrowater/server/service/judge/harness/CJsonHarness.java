package cn.nitrowater.server.service.judge.harness;

import cn.nitrowater.server.service.judge.JudgeSignature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared base for the C-family harnesses (C and C++). Both drive a LeetCode-style function over a
 * JSON array of cases read from stdin and print one JSON result per case, reusing the same
 * hand-written JSON DOM reader bundled in {@code c-main.tpl} (valid C and C++ alike). Only the
 * language header, the extra helpers and the per-type binding/serialisation differ.
 *
 * <p>Each subclass provides the language-specific pieces via the abstract hooks; the flow —
 * substituting the placeholders, wiring one case parameter per binding, then the call and the
 * result serialisation — is implemented once here.</p>
 */
abstract class CJsonHarness implements FunctionHarness {

    private static final String TEMPLATE = loadTemplate();

    /** Statement(s) that declare and bind one case parameter from the case object {@code __c}. */
    protected abstract String paramDecl(JudgeSignature.Param param);

    /** Argument expression passed for one parameter. */
    protected abstract String paramCallArg(JudgeSignature.Param param);

    /**
     * Statement(s) that call the user's function and print its JSON-serialised result.
     *
     * @param returnType declared return type of the method
     * @param method     method name to invoke
     * @param args       argument expressions
     * @param userCode   the user's own source, so a harness can adapt to how it declares the method
     */
    protected abstract String callAndPrint(String returnType, String method, String args, String userCode);

    /** Language header placed at the top of the generated program (includes / {@code using}). */
    protected abstract String languageHeader();

    /**
     * Type definitions injected before the user's code (may be {@code ""}). Placed here because the
     * user's own body may use the node type, which must therefore be declared in time.
     */
    protected abstract String typeDefinitions(JudgeSignature signature, String userCode);

    /**
     * Helper functions injected after the user's code and before {@code main} (may be {@code ""}).
     * Placed after the user's code because they reference the node type, which the user may define
     * themselves; only the driver calls them, so their position relative to the user's code is free.
     */
    protected abstract String helpers(JudgeSignature signature, String userCode);

    @Override
    public GeneratedHarness generate(JudgeSignature signature, String userCode) {
        String code = userCode == null ? "" : userCode;
        StringBuilder caseBody = new StringBuilder();
        StringBuilder args = new StringBuilder();
        List<JudgeSignature.Param> params = signature.params();
        for (int i = 0; i < params.size(); i++) {
            JudgeSignature.Param param = params.get(i);
            caseBody.append(paramDecl(param)).append('\n');
            if (i > 0) {
                args.append(", ");
            }
            args.append(paramCallArg(param));
        }
        caseBody.append(callAndPrint(signature.returnType(), signature.method(), args.toString(), code));

        String source = TEMPLATE
                .replace("{{LANG_INCLUDES}}", languageHeader())
                .replace("{{TYPE_DEFS}}", typeDefinitions(signature, code))
                .replace("{{USER_CODE}}", code)
                .replace("{{HELPERS}}", helpers(signature, code))
                .replace("{{CASE_BODY}}", caseBody.toString());
        return new GeneratedHarness(source, 0);
    }

    /** Local variable name for a bound parameter; prefixed so it cannot collide with user symbols. */
    protected static String local(JudgeSignature.Param param) {
        return "__nt_a_" + param.name();
    }

    /** Strips insignificant whitespace so {@code "int []"} and {@code "int[]"} compare equal. */
    protected static String normalized(String type) {
        return HarnessSupport.normalized(type);
    }

    /** @return whether the signature uses the given type as a parameter or as the return type */
    protected static boolean needsType(JudgeSignature signature, String type) {
        return HarnessSupport.usesType(signature, type);
    }

    /**
     * @param userCode the user's source
     * @param definition matches a node type definition (not a mere reference), capturing the type name
     * @param type the node type to test
     * @return whether the user's own code already defines {@code type} (so no duplicate is injected)
     */
    protected static boolean userDefinesNode(String userCode, Pattern definition, String type) {
        Matcher matcher = definition.matcher(userCode == null ? "" : userCode);
        while (matcher.find()) {
            if (type.equals(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static String loadTemplate() {
        try (InputStream in = CJsonHarness.class.getResourceAsStream("/harness/c/c-main.tpl")) {
            if (in == null) {
                throw new IllegalStateException("Missing harness template /harness/c/c-main.tpl");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load harness template", ex);
        }
    }
}
