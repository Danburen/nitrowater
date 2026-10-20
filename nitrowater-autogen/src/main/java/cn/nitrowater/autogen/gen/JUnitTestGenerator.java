package cn.nitrowater.autogen.gen;

import cn.nitrowater.autogen.ast.ClassFact;
import cn.nitrowater.autogen.ast.MethodFact;
import cn.nitrowater.autogen.llm.LlmClient;
import java.nio.file.Path;
import java.util.List;

/**
 * The generation pipeline in one place: AST facts -> prompt -> LLM -> code
 * extraction -> compile validation. Callers (the CLI) get everything they need
 * to persist and run the result.
 */
public final class JUnitTestGenerator {

    /** The artefacts produced by one generation run. */
    public record Generation(
            String systemPrompt,
            String userPrompt,
            String rawResponse,
            String source,
            String fullyQualifiedName,
            JavaCompilationCheck.Result validation) {
    }

    private final LlmClient llm;

    public JUnitTestGenerator(LlmClient llm) {
        this.llm = llm;
    }

    public Generation generate(ClassFact fact, List<MethodFact> targets)
            throws Exception {
        String system = PromptBuilder.systemPrompt();
        String user = PromptBuilder.userPrompt(fact, targets);
        String raw = llm.chat(system, user);
        String source = CodeExtractor.extractJava(raw);
        String fullyQualifiedName = JavaSourceInfo.fullyQualifiedName(source);
        JavaCompilationCheck.Result validation =
                JavaCompilationCheck.check(Path.of(fact.filePath()), fullyQualifiedName, source);
        return new Generation(system, user, raw, source, fullyQualifiedName, validation);
    }
}
