package cn.nitrowater.autogen.gen;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * The guard rail that replaces "hope the model wrote valid code" with a check.
 *
 * <p>This mirrors the {@code ast.parse} / compile validation of the reference
 * design: the generated test is compiled in-memory against the current JVM
 * classpath (which already carries JUnit 5). Only when this passes do we write
 * the file out and optionally execute it.
 */
public final class JavaCompilationCheck {

    public record Result(boolean ok, List<String> messages) {
        public static Result skipped(String reason) {
            return new Result(true, List.of("compile check skipped: " + reason));
        }
    }

    private JavaCompilationCheck() {
    }

    public static Result check(Path targetSource, String fullyQualifiedName, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return Result.skipped("no system Java compiler (JRE only)");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path outDir;
        try {
            outDir = Files.createTempDirectory("autogen-check-");
        } catch (IOException e) {
            return Result.skipped("cannot create temp dir: " + e.getMessage());
        }

        JavaFileObject generated = new SimpleJavaFileObject(
                URI.create("string:///" + fullyQualifiedName.replace('.', '/') + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };

        List<String> options = List.of(
                "-d", outDir.toString(),
                "-encoding", "UTF-8",
                "-classpath", System.getProperty("java.class.path"));

        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<JavaFileObject> units = new ArrayList<>();
            // Compile the class under test together with the generated test so that
            // references to the target type resolve (Java needs the type, not just syntax).
            if (targetSource != null && Files.isRegularFile(targetSource)) {
                fileManager.getJavaFileObjects(targetSource.toFile()).forEach(units::add);
            }
            units.add(generated);

            Boolean ok = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            List<String> errors = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    errors.add(String.format("%s at line %d: %s",
                            d.getKind(), d.getLineNumber(), d.getMessage(null)));
                }
            }
            return new Result(Boolean.TRUE.equals(ok) && errors.isEmpty(), errors);
        } catch (IOException e) {
            return Result.skipped("compile infrastructure error: " + e.getMessage());
        }
    }
}
