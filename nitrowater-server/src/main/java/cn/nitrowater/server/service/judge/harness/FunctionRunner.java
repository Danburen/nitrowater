package cn.nitrowater.server.service.judge.harness;

import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.infra.entity.Problem;
import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.infra.enums.Verdict;
import cn.nitrowater.server.infra.third.PistonClient;
import cn.nitrowater.server.service.judge.JudgeSignature;
import cn.nitrowater.server.service.judge.OutputComparator;
import cn.nitrowater.server.service.shared.CompileErrors;
import cn.nitrowater.server.service.shared.ExecResult;
import cn.nitrowater.server.service.shared.ExecSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Executes a LeetCode-style function submission: compiles and runs the language's generated
 * harness <b>once</b> with every sample bundled into a JSON array on stdin, then compares the
 * harness's one-JSON-line-per-sample stdout against each sample's expected output.
 *
 * <p>This is the single source of truth for function-mode execution, shared by the judge (submit)
 * and the playground self-test run. It is language-agnostic apart from the {@link FunctionHarness}
 * it selects; all Piston specifics stay behind {@link PistonClient}.</p>
 *
 * <p>Because a single process covers every sample, the wall-clock limit is scaled by the sample
 * count; a run-level failure (TLE/OLE/RE/CE) applies to every sample, since per-sample output can
 * no longer be trusted.</p>
 */
@Slf4j
@Component
public class FunctionRunner {

    private final PistonClient pistonClient;
    private final SandboxProperties sandbox;
    private final Map<Language, FunctionHarness> harnesses;

    public FunctionRunner(PistonClient pistonClient, SandboxProperties sandbox,
            List<FunctionHarness> harnesses) {
        this.pistonClient = pistonClient;
        this.sandbox = sandbox;
        Map<Language, FunctionHarness> byLanguage = new EnumMap<>(Language.class);
        for (FunctionHarness harness : harnesses) {
            byLanguage.put(harness.language(), harness);
        }
        this.harnesses = Map.copyOf(byLanguage);
    }

    /** True when a harness is registered for the language. */
    public boolean supports(Language language) {
        return harnesses.containsKey(language);
    }

    /**
     * Compiles and runs the harness once for all samples.
     *
     * @param problem problem whose limits/signature drive the run (its id is only used for logging)
     * @param language submission language (must be {@link #supports supported})
     * @param code the user's source code
     * @param samples inputs (and optional expected outputs) to run, in order
     * @param signature the parsed judge signature
     * @return one {@link RunCase} per sample, plus the run-level status/compile message
     */
    public HarnessRun run(Problem problem, Language language, String code, List<Sample> samples,
            JudgeSignature signature) {
        FunctionHarness harness = harnesses.get(language);
        if (harness == null) {
            throw new IllegalArgumentException("No harness for language: " + language);
        }
        GeneratedHarness generated;
        try {
            generated = harness.generate(signature, code);
        } catch (IllegalArgumentException ex) {
            return fatal(samples.size(), "SE", Verdict.SE, ex.getMessage());
        }

        String pistonLanguage = language.getPistonName();
        int timeoutMs = (int) Math.min(Integer.MAX_VALUE,
                (long) problem.getTimeLimitMs() * Math.max(1, samples.size()));
        ExecSpec spec = new ExecSpec(
                pistonLanguage,
                "*",
                language.fileName(),
                generated.source(),
                combinedStdin(samples),
                sandbox.compileTimeoutMs(),
                timeoutMs,
                problem.getMemoryLimitKb() * 1024L,
                problem.getMemoryLimitKb() * 1024L);

        ExecResult result;
        try {
            result = pistonClient.execute(spec);
        } catch (Exception ex) {
            log.warn("Sandbox call failed for problem {}: {}", problem.getId(), ex.getMessage());
            return fatal(samples.size(), "SE", Verdict.SE, "sandbox unavailable");
        }

        String diagnostic = CompileErrors.diagnostic(pistonLanguage, result, generated.userCodeLineOffset());
        if (diagnostic != null) {
            ExecResult.Stage stage = result.compile() != null ? result.compile() : result.run();
            return fatal(samples.size(), "CE", Verdict.CE, CompileErrors.oneLine(diagnostic),
                    stage == null ? null : millis(stage.wallTimeMs()),
                    stage == null ? null : kb(stage.memoryBytes()));
        }
        ExecResult.Stage run = result.run() != null ? result.run() : result.compile();
        if (run == null) {
            return fatal(samples.size(), "SE", Verdict.SE, "no execution result");
        }
        Integer timeMs = millis(run.wallTimeMs());
        Integer memoryKb = kb(run.memoryBytes());
        String status = run.status();
        if ("TO".equals(status)) {
            return fatal(samples.size(), "TO", Verdict.TLE, "time limit exceeded", timeMs, memoryKb);
        }
        if ("OL".equals(status) || "EL".equals(status)) {
            return fatal(samples.size(), "OL", Verdict.OLE, "output limit exceeded", timeMs, memoryKb);
        }
        if ("RE".equals(status) || "SG".equals(status) || (run.code() != null && run.code() != 0)) {
            return fatal(samples.size(), "RE", Verdict.RE, "runtime error", timeMs, memoryKb);
        }

        String[] lines = (run.stdout() == null ? "" : run.stdout()).split("\n", -1);
        List<RunCase> cases = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            if (i >= lines.length || lines[i].isBlank()) {
                cases.add(new RunCase(i, Verdict.RE, null, timeMs, memoryKb, "no output for this case"));
                continue;
            }
            String actual = lines[i];
            String expected = samples.get(i).expected();
            if (expected == null) {
                cases.add(new RunCase(i, null, actual, timeMs, memoryKb, null));
                continue;
            }
            boolean match = OutputComparator.matches(expected, actual, OutputComparator.Strategy.JSON);
            cases.add(new RunCase(i, match ? Verdict.AC : Verdict.WA, actual, timeMs, memoryKb,
                    match ? null : "wrong answer"));
        }
        return new HarnessRun("OK", null, cases);
    }

    /**
     * One sample to run.
     *
     * @param stdin the sample input (a JSON parameter object for LeetCode-style problems)
     * @param expected the expected stdout, or {@code null} to only display the actual output
     */
    public record Sample(String stdin, String expected) {
    }

    /**
     * Outcome of one sample.
     *
     * @param index zero-based sample index
     * @param verdict the verdict, or {@code null} when the sample had no expectation (ran OK, unjudged)
     * @param actual the actual stdout line for this sample, or {@code null} when unavailable
     * @param timeMs wall-clock time in milliseconds
     * @param memoryKb peak memory in kilobytes
     * @param message human-readable failure reason, or {@code null}
     */
    public record RunCase(int index, Verdict verdict, String actual, Integer timeMs, Integer memoryKb,
            String message) {
    }

    /**
     * Run-level result.
     *
     * @param status overall status: {@code OK}/{@code CE}/{@code TO}/{@code OL}/{@code RE}/{@code SE}
     * @param compileMessage compiler diagnostics when the status is {@code CE}, else {@code null}
     * @param cases one entry per sample
     */
    public record HarnessRun(String status, String compileMessage, List<RunCase> cases) {
    }

    private static HarnessRun fatal(int count, String status, Verdict verdict, String message) {
        return fatal(count, status, verdict, message, null, null);
    }

    private static HarnessRun fatal(int count, String status, Verdict verdict, String message,
            Integer timeMs, Integer memoryKb) {
        List<RunCase> cases = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cases.add(new RunCase(i, verdict, null, timeMs, memoryKb, message));
        }
        String compileMessage = verdict == Verdict.CE ? message : null;
        return new HarnessRun(status, compileMessage, cases);
    }

    /** Bundles every sample's parameter JSON object into one JSON array for the single harness run. */
    private static String combinedStdin(List<Sample> samples) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < samples.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String stdin = samples.get(i).stdin();
            sb.append(stdin == null || stdin.isBlank() ? "null" : stdin.trim());
        }
        return sb.append(']').toString();
    }

    private static Integer millis(Long value) {
        return value == null ? null : (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static Integer kb(Long bytes) {
        return bytes == null ? null : (int) Math.min(Integer.MAX_VALUE, bytes / 1024);
    }
}
