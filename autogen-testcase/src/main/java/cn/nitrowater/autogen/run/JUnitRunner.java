package cn.nitrowater.autogen.run;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Compiles the target class together with the generated test and executes the
 * test with the JUnit Platform Launcher, entirely in-process.
 *
 * <p>This closes the loop of the design: generation is not trusted, it is
 * <em>run</em>. JUnit 5 is already on the runtime classpath (see build.gradle),
 * so no external test tooling is required.
 */
public final class JUnitRunner {

    /** Outcome of one test execution. */
    public record Summary(int found, int succeeded, int failed, int containersFailed,
                          List<String> failures) {
        public boolean allGreen() {
            return found > 0 && failed == 0 && containersFailed == 0;
        }
    }

    private JUnitRunner() {
    }

    public static Summary run(Path targetSource, Path testSource, String testFqcn)
            throws Exception {
        Path workDir = Files.createTempDirectory("autogen-run-");
        compile(List.of(targetSource, testSource), workDir);

        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{workDir.toUri().toURL()}, JUnitRunner.class.getClassLoader())) {
            Class<?> testClass = Class.forName(testFqcn, false, loader);

            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectClass(testClass))
                    .build();

            Launcher launcher = LauncherFactory.create();
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            launcher.execute(request, listener);

            TestExecutionSummary summary = listener.getSummary();
            List<String> failures = new ArrayList<>();
            for (TestExecutionSummary.Failure failure : summary.getFailures()) {
                failures.add(failure.getTestIdentifier().getDisplayName()
                        + " -> " + failure.getException());
            }
            return new Summary(
                    (int) summary.getTestsFoundCount(),
                    (int) summary.getTestsSucceededCount(),
                    (int) summary.getTestsFailedCount(),
                    (int) summary.getContainersFailedCount(),
                    failures);
        }
    }

    private static void compile(List<Path> sources, Path outDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available (need a JDK).");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<JavaFileObject> units = new ArrayList<>();
            for (Path source : sources) {
                fileManager.getJavaFileObjects(source.toFile()).forEach(units::add);
            }
            List<String> options = List.of(
                    "-d", outDir.toString(),
                    "-encoding", "UTF-8",
                    "-classpath", System.getProperty("java.class.path"));
            boolean ok = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!ok) {
                StringBuilder message = new StringBuilder("Failed to compile generated test:\n");
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    message.append("  ").append(d.getKind())
                            .append(" line ").append(d.getLineNumber())
                            .append(": ").append(d.getMessage(null)).append('\n');
                }
                throw new IllegalStateException(message.toString());
            }
        }
    }
}
