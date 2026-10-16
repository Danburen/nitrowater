package cn.nitrowater.autogen.ast;

import java.util.List;

/**
 * The AST facts extracted for a single callable (method or constructor).
 *
 * <p>These facts are the ground truth handed to the LLM instead of (or alongside)
 * the raw source: signature, modifiers, thrown exceptions, javadoc, a rough
 * cyclomatic complexity and the line count. {@code source} keeps the verbatim
 * body so the model can reason about concrete behaviour.
 */
public record MethodFact(
        String name,
        String returnType,
        List<ParamFact> parameters,
        List<String> modifiers,
        List<String> thrownExceptions,
        String javadoc,
        boolean isStatic,
        int lineCount,
        int complexity,
        String signature,
        String source) {

    /** True when this fact describes a constructor rather than a method. */
    public boolean isConstructor() {
        return "<init>".equals(returnType);
    }
}
