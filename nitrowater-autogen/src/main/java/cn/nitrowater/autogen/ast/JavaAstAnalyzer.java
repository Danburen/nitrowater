package cn.nitrowater.autogen.ast;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The "based on code AST" half of AutoGenTestCase.
 *
 * <p>It parses a Java source file with JavaParser and turns it into a compact,
 * deterministic {@link ClassFact} description. No LLM is involved here — this
 * step only produces verifiable facts (signatures, modifiers, complexity,
 * verbatim method source) that the prompt builder will feed to the model.
 */
public final class JavaAstAnalyzer {

    /** Parses {@code file} and extracts the facts of its primary top-level type. */
    public ClassFact analyze(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        CompilationUnit cu = StaticJavaParser.parse(source);

        TypeDeclaration<?> type = cu.getTypes().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No top-level type found in " + file));

        List<FieldFact> fields = new ArrayList<>();
        for (FieldDeclaration field : type.getFields()) {
            List<String> fieldModifiers = modifiersOf(field);
            for (VariableDeclarator variable : field.getVariables()) {
                fields.add(new FieldFact(
                        variable.getNameAsString(),
                        variable.getType().asString(),
                        fieldModifiers));
            }
        }

        List<MethodFact> constructors = new ArrayList<>();
        for (ConstructorDeclaration ctor : type.getConstructors()) {
            constructors.add(toConstructorFact(type.getNameAsString(), ctor));
        }

        List<MethodFact> methods = new ArrayList<>();
        for (MethodDeclaration method : type.getMethods()) {
            methods.add(toMethodFact(method));
        }

        List<String> imports = new ArrayList<>();
        for (ImportDeclaration imp : cu.getImports()) {
            imports.add((imp.isStatic() ? "static " : "") + imp.getNameAsString());
        }

        return new ClassFact(
                file.toString(),
                cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse(""),
                type.getNameAsString(),
                kindOf(type),
                modifiersOf(type),
                javadocOf(type),
                imports,
                fields,
                constructors,
                methods);
    }

    private MethodFact toMethodFact(MethodDeclaration method) {
        List<ParamFact> parameters = new ArrayList<>();
        for (Parameter p : method.getParameters()) {
            parameters.add(new ParamFact(p.getType().asString(), p.getNameAsString()));
        }
        List<String> thrown = method.getThrownExceptions().stream()
                .map(t -> t.asString())
                .toList();
        String returnType = method.getType().asString();
        String signature = String.join(" ", modifiersOf(method))
                + " " + returnType + " " + method.getNameAsString()
                + parameters.stream()
                        .map(p -> p.type() + " " + p.name())
                        .reduce((a, b) -> a + ", " + b)
                        .map(s -> "(" + s + ")")
                        .orElse("()");
        return new MethodFact(
                method.getNameAsString(),
                returnType,
                parameters,
                modifiersOf(method),
                thrown,
                javadocOf(method),
                method.isStatic(),
                lineCount(method),
                complexityOf(method),
                signature.strip(),
                method.toString());
    }

    private MethodFact toConstructorFact(String typeName, ConstructorDeclaration ctor) {
        List<ParamFact> parameters = new ArrayList<>();
        for (Parameter p : ctor.getParameters()) {
            parameters.add(new ParamFact(p.getType().asString(), p.getNameAsString()));
        }
        List<String> thrown = ctor.getThrownExceptions().stream()
                .map(t -> t.asString())
                .toList();
        String signature = typeName + ctor.getParameters().stream()
                .map(p -> p.getType().asString() + " " + p.getNameAsString())
                .reduce((a, b) -> a + ", " + b)
                .map(s -> "(" + s + ")")
                .orElse("()");
        return new MethodFact(
                typeName,
                "<init>",
                parameters,
                modifiersOf(ctor),
                thrown,
                javadocOf(ctor),
                false,
                lineCount(ctor),
                complexityOf(ctor),
                signature,
                ctor.toString());
    }

    private static List<String> modifiersOf(com.github.javaparser.ast.nodeTypes.NodeWithModifiers<?> node) {
        return node.getModifiers().stream()
                .map(m -> m.getKeyword().asString())
                .toList();
    }

    private static String javadocOf(NodeWithJavadoc<?> node) {
        return node.getJavadocComment()
                .map(comment -> comment.getContent().strip())
                .orElse("");
    }

    private static int lineCount(com.github.javaparser.ast.Node node) {
        return node.getRange()
                .map(range -> range.end.line - range.begin.line + 1)
                .orElse(0);
    }

    /**
     * A rough cyclomatic complexity: 1 plus the number of branching constructs.
     * Good enough as a hint for how many test cases a method likely deserves.
     */
    private static int complexityOf(com.github.javaparser.ast.Node node) {
        int complexity = 1;
        complexity += node.findAll(IfStmt.class).size();
        complexity += node.findAll(ForStmt.class).size();
        complexity += node.findAll(ForEachStmt.class).size();
        complexity += node.findAll(WhileStmt.class).size();
        complexity += node.findAll(DoStmt.class).size();
        complexity += node.findAll(SwitchEntry.class).size();
        complexity += node.findAll(CatchClause.class).size();
        complexity += node.findAll(ConditionalExpr.class).size();
        return complexity;
    }

    private static String kindOf(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration cid) {
            return cid.isInterface() ? "interface" : "class";
        }
        if (type instanceof EnumDeclaration) {
            return "enum";
        }
        if (type instanceof RecordDeclaration) {
            return "record";
        }
        if (type instanceof AnnotationDeclaration) {
            return "annotation";
        }
        return "type";
    }
}
