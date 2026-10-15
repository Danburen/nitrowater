package cn.nitrowater.server.service.judge;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.infra.entity.Problem;
import cn.nitrowater.server.infra.entity.ProblemTestCase;
import cn.nitrowater.server.infra.enums.IoMode;
import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.infra.repository.ProblemRepository;
import cn.nitrowater.server.infra.repository.ProblemTestCaseRepository;
import cn.nitrowater.server.service.judge.harness.FunctionRunner;
import cn.nitrowater.server.service.sandbox.SandboxService;
import cn.nitrowater.server.web.dto.PlaygroundCaseResult;
import cn.nitrowater.server.web.dto.PlaygroundRunRequest;
import cn.nitrowater.server.web.dto.PlaygroundRunResponse;
import cn.nitrowater.server.web.dto.RunRequest;
import cn.nitrowater.server.web.dto.RunResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs a playground self-test: the user's code against user-supplied samples (or the problem's
 * public sample cases), without persisting a submission.
 *
 * <p>LeetCode-style problems whose language has a harness and a signature go through the same
 * {@link FunctionRunner} used by the judge, so "run" and "submit" agree. ACM problems fall back to
 * a raw {@link SandboxService} run per sample. Only the problem's <em>sample</em> cases are ever
 * used as a fallback, so hidden cases never leak.</p>
 */
@Service
public class PlaygroundRunService {

    private final ProblemRepository problems;
    private final ProblemTestCaseRepository testCases;
    private final FunctionRunner functionRunner;
    private final SandboxService sandboxService;
    private final SandboxProperties sandbox;

    public PlaygroundRunService(ProblemRepository problems, ProblemTestCaseRepository testCases,
            FunctionRunner functionRunner, SandboxService sandboxService, SandboxProperties sandbox) {
        this.problems = problems;
        this.testCases = testCases;
        this.functionRunner = functionRunner;
        this.sandboxService = sandboxService;
        this.sandbox = sandbox;
    }

    /**
     * Runs {@code request} and returns the per-sample outcome.
     *
     * @param request the self-test payload
     * @return the run result
     */
    public PlaygroundRunResponse run(PlaygroundRunRequest request) {
        Problem problem = problems.findById(request.problemId())
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found"));

        Language language;
        try {
            language = Language.fromId(request.language());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported language: " + request.language());
        }
        if (request.code().getBytes(StandardCharsets.UTF_8).length > sandbox.maxCodeBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code too large");
        }

        List<FunctionRunner.Sample> samples = resolveSamples(request, problem);
        if (samples.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No samples to run");
        }

        JudgeSignature signature = JudgeSignature.parse(judgeConfigOf(problem));
        if (problem.getIoMode() == IoMode.LEETCODE && signature != null
                && functionRunner.supports(language)) {
            return runFunction(problem, language, request.code(), samples, signature);
        }
        return runAcm(request.language(), request.code(), samples, strategyOf(problem));
    }

    /** LeetCode-style path: one harness run covers every sample (mirrors the judge). */
    private PlaygroundRunResponse runFunction(Problem problem, Language language, String code,
            List<FunctionRunner.Sample> samples, JudgeSignature signature) {
        FunctionRunner.HarnessRun run = functionRunner.run(problem, language, code, samples, signature);
        List<PlaygroundCaseResult> cases = new ArrayList<>(run.cases().size());
        for (int i = 0; i < run.cases().size(); i++) {
            FunctionRunner.RunCase outcome = run.cases().get(i);
            FunctionRunner.Sample sample = samples.get(i);
            cases.add(new PlaygroundCaseResult(i, sample.stdin(), sample.expected(), outcome.actual(),
                    outcome.verdict() == null ? "OK" : outcome.verdict().name(),
                    outcome.timeMs(), outcome.memoryKb(), outcome.message()));
        }
        return new PlaygroundRunResponse(run.status(), run.compileMessage(), cases);
    }

    /** ACM path: one raw sandbox run per sample, compared with the problem's comparator. */
    private PlaygroundRunResponse runAcm(String languageId, String code,
            List<FunctionRunner.Sample> samples, OutputComparator.Strategy strategy) {
        List<PlaygroundCaseResult> cases = new ArrayList<>(samples.size());
        String overall = "OK";
        String compileMessage = null;
        for (int i = 0; i < samples.size(); i++) {
            FunctionRunner.Sample sample = samples.get(i);
            RunResponse result = sandboxService.run(new RunRequest(languageId, code, sample.stdin()));
            Integer timeMs = millis(result.wallTimeMs());
            Integer memoryKb = kb(result.memoryBytes());
            String actual = result.stdout();
            String message = result.message();
            String verdict;
            if ("CE".equals(result.status())) {
                verdict = "CE";
                actual = null;
                if (compileMessage == null) {
                    compileMessage = result.stderr() == null ? result.message() : result.stderr();
                }
                overall = "CE";
            } else if (!"OK".equals(result.status())) {
                verdict = runStatusVerdict(result.status());
                if ("OK".equals(overall)) {
                    overall = result.status();
                }
            } else if (sample.expected() == null) {
                verdict = "OK";
            } else {
                boolean match = OutputComparator.matches(sample.expected(), result.stdout(), strategy);
                verdict = match ? "AC" : "WA";
                message = match ? null : "wrong answer";
            }
            cases.add(new PlaygroundCaseResult(i, sample.stdin(), sample.expected(), actual, verdict,
                    timeMs, memoryKb, message));
        }
        return new PlaygroundRunResponse(overall, compileMessage, cases);
    }

    /**
     * Resolves the samples to run: the caller's own when present, otherwise the problem's
     * <b>sample</b> cases only (never the hidden ones).
     */
    private List<FunctionRunner.Sample> resolveSamples(PlaygroundRunRequest request, Problem problem) {
        if (request.samples() != null && !request.samples().isEmpty()) {
            List<FunctionRunner.Sample> samples = new ArrayList<>(request.samples().size());
            for (PlaygroundRunRequest.Sample sample : request.samples()) {
                samples.add(new FunctionRunner.Sample(
                        sample.stdin() == null ? "" : sample.stdin(), sample.expected()));
            }
            return samples;
        }
        List<FunctionRunner.Sample> samples = new ArrayList<>();
        for (ProblemTestCase testCase : testCases.findByProblemIdOrderByIdxAsc(problem.getId())) {
            if (Boolean.TRUE.equals(testCase.getIsSample())) {
                samples.add(new FunctionRunner.Sample(testCase.getStdin(), testCase.getExpectedOutput()));
            }
        }
        return samples;
    }

    private static String runStatusVerdict(String status) {
        return switch (status) {
            case "TO" -> "TLE";
            case "OL", "EL" -> "OLE";
            case "SE" -> "SE";
            default -> "RE";
        };
    }

    private static Map<String, Object> judgeConfigOf(Problem problem) {
        return JsonUtil.fromJson(problem.getJudgeConfig(), Map.class);
    }

    private static OutputComparator.Strategy strategyOf(Problem problem) {
        Map<String, Object> config = judgeConfigOf(problem);
        Object compare = config == null ? null : config.get("compare");
        return OutputComparator.strategyOf(compare == null ? null : String.valueOf(compare));
    }

    private static Integer millis(Long value) {
        return value == null ? null : (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static Integer kb(Long bytes) {
        return bytes == null ? null : (int) Math.min(Integer.MAX_VALUE, bytes / 1024);
    }
}
