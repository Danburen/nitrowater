package cn.nitrowater.autogen.ast;

import java.util.List;

/**
 * The AST facts extracted for one top-level Java type: identity, imports,
 * fields, constructors and methods. This is what the analyzer produces and what
 * the prompt builder and {@code --show-facts} consume.
 */
public record ClassFact(
        String filePath,
        String packageName,
        String className,
        String kind,
        List<String> modifiers,
        String classJavadoc,
        List<String> imports,
        List<FieldFact> fields,
        List<MethodFact> constructors,
        List<MethodFact> methods) {

    /** Fully-qualified name of the analysed type, e.g. {@code demo.Calculator}. */
    public String qualifiedName() {
        return packageName == null || packageName.isEmpty() ? className : packageName + "." + className;
    }
}
