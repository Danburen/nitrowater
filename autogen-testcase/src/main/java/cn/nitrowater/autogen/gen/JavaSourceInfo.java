package cn.nitrowater.autogen.gen;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

/**
 * Reads the package and primary type name out of a generated Java source string.
 * Used to derive the fully-qualified class name for the compile check / runner.
 */
public final class JavaSourceInfo {

    private JavaSourceInfo() {
    }

    /** Returns e.g. {@code demo.CalculatorTest} for the given source. */
    public static String fullyQualifiedName(String source) {
        CompilationUnit cu = StaticJavaParser.parse(source);
        String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        String simple = cu.getTypes().stream()
                .findFirst()
                .map(TypeDeclaration::getNameAsString)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Generated source declares no top-level type"));
        return pkg.isEmpty() ? simple : pkg + "." + simple;
    }
}
