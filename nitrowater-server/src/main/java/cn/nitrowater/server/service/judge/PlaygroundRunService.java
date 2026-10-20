package cn.nitrowater.server.service.judge;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.infra.entity.Problem;
import cn.nitrowater.server.infra.enums.IoMode;
import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.infra.repository.ProblemRepository;
import cn.nitrowater.server.service.judge.harness.FunctionRunner;
import cn.nitrowater.server.service.sandbox.SandboxService;
import cn.nitrowater.server.web.dto.PlaygroundRunRequest;
import cn.nitrowater.server.web.dto.PlaygroundRunResponse;
import cn.nitrowater.server.web.dto.RunRequest;
import cn.nitrowater.server.web.dto.RunResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Runs a playground self-test: the user's code against a single free-form input, showing the
 * program's actual output without persisting anything or judging it.
 *
 * <p>This is a "stdin in, stdout out" run (code-runner style), not an online judge: no expected
 * output, no cases, no verdicts. LeetCode-style problems whose language has a harness and a
 * signature still go through {@link FunctionRunner} so the function-mode call convention matches
 * a real submission (the single input is the JSON parameter object); ACM problems run raw via
 * {@link SandboxService}.</p>
 */
@Service
public class PlaygroundRunService {

    private final ProblemRepository problems;
    private final FunctionRunner functionRunner;
    private final SandboxService sandboxService;
    private final SandboxProperties sandbox;

    public PlaygroundRunService(ProblemRepository problems, FunctionRunner functionRunner,
            SandboxService sandboxService, SandboxProperties sandbox) {
        this.problems = problems;
        this.functionRunner = functionRunner;
        this.sandboxService = sandboxService;
        this.sandbox = sandbox;
    }

    /**
     * Runs {@code request} once and returns the program's output.
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

        String stdin = request.stdin() == null ? "" : request.stdin();
        JudgeSignature signature = JudgeSignature.parse(judgeConfigOf(problem));
        if (problem.getIoMode() == IoMode.LEETCODE && signature != null
                && functionRunner.supports(language)) {
            return runFunction(problem, language, request.code(), stdin, signature);
        }
        return runAcm(request.language(), request.code(), stdin);
    }

    /** LeetCode-style path: one harness run with the single input (mirrors the judge's call convention). */
    private PlaygroundRunResponse runFunction(Problem problem, Language language, String code,
            String stdin, JudgeSignature signature) {
        FunctionRunner.HarnessRun run = functionRunner.run(problem, language, code,
                List.of(new FunctionRunner.Sample(stdin, null)), signature);
        FunctionRunner.RunCase outcome = run.cases().isEmpty() ? null : run.cases().get(0);
        String stderr = "CE".equals(run.status()) ? run.compileMessage() : null;
        return new PlaygroundRunResponse(run.status(),
                outcome == null ? null : outcome.actual(),
                stderr,
                outcome == null ? null : outcome.timeMs(),
                outcome == null ? null : outcome.memoryKb(),
                outcome == null ? null : outcome.message());
    }

    /** ACM path: one raw sandbox run with the input; the output is returned as-is (never compared). */
    private PlaygroundRunResponse runAcm(String languageId, String code, String stdin) {
        RunResponse result = sandboxService.run(new RunRequest(languageId, code, stdin));
        return new PlaygroundRunResponse(result.status(), result.stdout(), result.stderr(),
                millis(result.wallTimeMs()), kb(result.memoryBytes()), result.message());
    }

    private static Map<String, Object> judgeConfigOf(Problem problem) {
        return JsonUtil.fromJson(problem.getJudgeConfig(), Map.class);
    }

    private static Integer millis(Long value) {
        return value == null ? null : (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static Integer kb(Long bytes) {
        return bytes == null ? null : (int) Math.min(Integer.MAX_VALUE, bytes / 1024);
    }
}
