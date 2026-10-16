package cn.nitrowater.autogen;

import cn.nitrowater.autogen.ast.ClassFact;
import cn.nitrowater.autogen.ast.JavaAstAnalyzer;
import cn.nitrowater.autogen.ast.MethodFact;
import cn.nitrowater.autogen.gen.JUnitTestGenerator;
import cn.nitrowater.autogen.gen.PromptBuilder;
import cn.nitrowater.autogen.llm.LlmClient;
import cn.nitrowater.autogen.run.JUnitRunner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/**
 * Command line entry point for the Java AutoGenTestCase tool.
 *
 * <p>Pipeline: {@code --file} --(AST)--> facts --(prompt)--> DeepSeek
 * --(extract+compile check)--> JUnit 5 test file --({@code --run})--> executed.
 *
 * <pre>
 * flags:
 *   -f, --file &lt;path&gt;    target .java (default: examples/Calculator.java)
 *   -m, --method &lt;name&gt;  only generate tests for this method (default: all)
 *       --list-methods    print methods found in the AST, then exit
 *       --show-facts      print AST facts as JSON, then exit
 *       --dry-run         print the prompt that would be sent, then exit (no API call)
 *       --out &lt;path&gt;      output test file (default: generated-tests/&lt;Class&gt;Test.java)
 *       --model &lt;name&gt;    DeepSeek model (default: deepseek-chat)
 *       --base-url &lt;url&gt;  API base url (default: https://api.deepseek.com)
 *       --run             compile &amp; execute the generated test, then report
 *   -h, --help            show this help
 * </pre>
 */
public final class AutoGenTestCaseCli {

    private static final Path DEFAULT_FILE = Path.of("examples", "Calculator.java");

    private AutoGenTestCaseCli() {
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    static int execute(String[] args) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            printUsage();
            return 2;
        }
        if (options.help) {
            printUsage();
            return 0;
        }

        Path target = options.file;
        if (!Files.isRegularFile(target)) {
            System.err.println("error: target file not found: " + target.toAbsolutePath());
            return 2;
        }

        ClassFact facts;
        try {
            facts = new JavaAstAnalyzer().analyze(target);
        } catch (Exception e) {
            System.err.println("error: failed to analyse " + target + ": " + e.getMessage());
            return 2;
        }

        if (options.listMethods) {
            System.out.println("Type: " + facts.kind() + " " + facts.qualifiedName());
            System.out.println("Methods:");
            for (MethodFact m : facts.methods()) {
                System.out.printf("  %-70s complexity=%d lines=%d%n",
                        m.signature(), m.complexity(), m.lineCount());
            }
            return 0;
        }

        if (options.showFacts) {
            try {
                String json = JsonMapper.builder().build()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(facts);
                System.out.println(json);
            } catch (Exception e) {
                System.err.println("error: cannot render facts: " + e.getMessage());
                return 1;
            }
            return 0;
        }

        List<MethodFact> targets;
        try {
            targets = selectMethods(facts, options.method);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            return 2;
        }

        if (options.dryRun) {
            System.out.println("===== SYSTEM PROMPT =====");
            System.out.println(PromptBuilder.systemPrompt());
            System.out.println();
            System.out.println("===== USER PROMPT =====");
            System.out.println(PromptBuilder.userPrompt(facts, targets));
            return 0;
        }

        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("error: DEEPSEEK_API_KEY is not set "
                    + "(use --dry-run to preview the prompt without calling the API).");
            return 3;
        }

        LlmClient llm = new LlmClient(apiKey, options.baseUrl, options.model);
        System.out.printf("Analysed %s -> %d target method(s); calling %s ...%n",
                facts.qualifiedName(), targets.size(), llm.endpoint());

        JUnitTestGenerator.Generation generation;
        try {
            generation = new JUnitTestGenerator(llm).generate(facts, targets);
        } catch (Exception e) {
            System.err.println("error: generation failed: " + e.getMessage());
            return 1;
        }

        System.out.println("Model returned " + generation.source().lines().count() + " line(s).");
        if (!generation.validation().ok()) {
            System.err.println("error: generated test did not compile: "
                    + generation.fullyQualifiedName());
            generation.validation().messages().forEach(m -> System.err.println("  - " + m));
            return 4;
        }

        Path out = options.out != null
                ? options.out
                : Path.of("generated-tests", facts.className() + "Test.java");
        try {
            if (out.toAbsolutePath().getParent() != null) {
                Files.createDirectories(out.toAbsolutePath().getParent());
            }
            Files.writeString(out, generation.source(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("error: cannot write " + out + ": " + e.getMessage());
            return 1;
        }
        System.out.println("Wrote " + out.toAbsolutePath()
                + " [" + generation.fullyQualifiedName() + "] (compiles OK).");

        if (!options.run) {
            return 0;
        }

        try {
            JUnitRunner.Summary summary = JUnitRunner.run(target, out, generation.fullyQualifiedName());
            System.out.printf("JUnit result: found=%d, succeeded=%d, failed=%d, containerFailed=%d%n",
                    summary.found(), summary.succeeded(), summary.failed(), summary.containersFailed());
            summary.failures().forEach(f -> System.out.println("  FAILED: " + f));
            if (!summary.allGreen()) {
                System.err.println("error: generated tests are not all green.");
                return 5;
            }
            System.out.println("All generated tests passed.");
            return 0;
        } catch (Exception e) {
            System.err.println("error: running tests failed: " + e.getMessage());
            return 5;
        }
    }

    private static List<MethodFact> selectMethods(ClassFact facts, String method) {
        if (method != null) {
            List<MethodFact> matched = new ArrayList<>();
            for (MethodFact m : facts.methods()) {
                if (m.name().equalsIgnoreCase(method)) {
                    matched.add(m);
                }
            }
            if (matched.isEmpty()) {
                throw new IllegalArgumentException(
                        "no method named '" + method + "' in " + facts.qualifiedName());
            }
            return matched;
        }
        List<MethodFact> all = new ArrayList<>();
        for (MethodFact m : facts.methods()) {
            if (!"main".equals(m.name())) {
                all.add(m);
            }
        }
        if (all.isEmpty()) {
            throw new IllegalArgumentException("no testable methods in " + facts.qualifiedName());
        }
        return all;
    }

    private static void printUsage() {
        System.out.println("""
                AutoGenTestCase (Java + JUnit) - AST analysis + DeepSeek -> JUnit 5 tests.

                usage: gradlew run --args="[options]"

                options:
                  -f, --file <path>     target .java (default: examples/Calculator.java)
                  -m, --method <name>   only generate tests for this method (default: all except main)
                      --list-methods    print the methods found in the AST, then exit
                      --show-facts      print the AST facts as JSON, then exit
                      --dry-run         print the prompt that would be sent, then exit (no API call)
                      --out <path>      output test file (default: generated-tests/<Class>Test.java)
                      --model <name>    DeepSeek model (default: deepseek-chat)
                      --base-url <url>  API base url (default: https://api.deepseek.com)
                      --run             compile & execute the generated test, then report
                  -h, --help            show this help

                The API key is read from the DEEPSEEK_API_KEY environment variable.
                """);
    }

    /** Parsed command-line options. */
    static final class Options {
        Path file = DEFAULT_FILE;
        String method;
        Path out;
        String model;
        String baseUrl;
        boolean listMethods;
        boolean showFacts;
        boolean dryRun;
        boolean run;
        boolean help;

        static Options parse(String[] args) {
            Options o = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "-h", "--help" -> o.help = true;
                    case "--list-methods" -> o.listMethods = true;
                    case "--show-facts" -> o.showFacts = true;
                    case "--dry-run" -> o.dryRun = true;
                    case "--run" -> o.run = true;
                    case "-f", "--file" -> o.file = Path.of(next(args, ++i, arg));
                    case "-m", "--method" -> o.method = next(args, ++i, arg);
                    case "--out" -> o.out = Path.of(next(args, ++i, arg));
                    case "--model" -> o.model = next(args, ++i, arg);
                    case "--base-url" -> o.baseUrl = next(args, ++i, arg);
                    default -> throw new IllegalArgumentException("unknown option: " + arg);
                }
            }
            return o;
        }

        private static String next(String[] args, int index, String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException("missing value for " + flag);
            }
            return args[index];
        }
    }
}
