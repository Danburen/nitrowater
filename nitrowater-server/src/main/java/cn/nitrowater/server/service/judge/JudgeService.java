package cn.nitrowater.server.service.judge;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.infra.entity.Submission;
import cn.nitrowater.server.infra.entity.Problem;
import cn.nitrowater.server.infra.entity.ProblemTestCase;
import cn.nitrowater.server.infra.entity.UserPointLog;
import cn.nitrowater.server.infra.entity.UserProblemStatus;
import cn.nitrowater.server.infra.entity.UserProblemStatusId;
import cn.nitrowater.server.infra.enums.IoMode;
import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.infra.enums.PointReason;
import cn.nitrowater.server.infra.enums.PointRefType;
import cn.nitrowater.server.infra.enums.ProblemStatus;
import cn.nitrowater.server.infra.enums.SubmissionStatus;
import cn.nitrowater.server.infra.enums.Verdict;
import cn.nitrowater.server.infra.repository.BizUserRepository;
import cn.nitrowater.server.infra.repository.ProblemRepository;
import cn.nitrowater.server.infra.repository.ProblemTestCaseRepository;
import cn.nitrowater.server.infra.repository.SubmissionRepository;
import cn.nitrowater.server.infra.repository.UserPointLogRepository;
import cn.nitrowater.server.infra.repository.UserProblemStatusRepository;
import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.service.judge.harness.FunctionRunner;
import cn.nitrowater.server.service.shared.CompileErrors;
import cn.nitrowater.server.service.shared.ExecResult;
import cn.nitrowater.server.service.shared.ExecSpec;
import cn.nitrowater.server.service.shared.LanguagePrelude;
import cn.nitrowater.server.infra.third.PistonClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Judges one submission: runs its code against every test case via the sandbox, compares outputs,
 * aggregates a verdict, and writes back the submission, the user's per-problem status and points.
 *
 * <p>The long sandbox calls run <b>outside</b> any DB transaction; only the small read/write steps
 * are wrapped, so a slow user program never holds a database connection.</p>
 */
@Slf4j
@Service
public class JudgeService {

    private final SubmissionRepository submissions;
    private final ProblemRepository problems;
    private final ProblemTestCaseRepository testCases;
    private final BizUserRepository users;
    private final UserProblemStatusRepository userProblemStatus;
    private final UserPointLogRepository pointLogs;
    private final PistonClient pistonClient;
    private final SandboxProperties sandbox;
    private final FunctionRunner functionRunner;
    private final TransactionTemplate tx;

    public JudgeService(SubmissionRepository submissions, ProblemRepository problems,
            ProblemTestCaseRepository testCases, BizUserRepository users,
            UserProblemStatusRepository userProblemStatus, UserPointLogRepository pointLogs,
            PistonClient pistonClient, SandboxProperties sandbox, FunctionRunner functionRunner,
            TransactionTemplate tx) {
        this.submissions = submissions;
        this.problems = problems;
        this.testCases = testCases;
        this.users = users;
        this.userProblemStatus = userProblemStatus;
        this.pointLogs = pointLogs;
        this.pistonClient = pistonClient;
        this.sandbox = sandbox;
        this.functionRunner = functionRunner;
        this.tx = tx;
    }

    /**
     * Judges the submission with the given id (no-op when it is missing or already finished).
     *
     * @param submissionId submission id
     */
    public void judge(Long submissionId) {
        Submission submission = submissions.findById(submissionId).orElse(null);
        if (submission == null
                || (submission.getStatus() != SubmissionStatus.QUEUED
                        && submission.getStatus() != SubmissionStatus.RUNNING)) {
            return;
        }
        Problem problem = problems.findById(submission.getProblemId()).orElse(null);
        if (problem == null) {
            finish(submissionId, Verdict.SE, 0, 0, 0, List.of(), "Problem no longer exists");
            return;
        }
        List<ProblemTestCase> cases = testCases.findByProblemIdOrderByIdxAsc(problem.getId());
        if (cases.isEmpty()) {
            finish(submissionId, Verdict.SE, 0, 0, 0, List.of(), "Problem has no test cases");
            return;
        }

        markRunning(submissionId);

        List<CaseOutcome> outcomes = useFunctionMode(problem, submission)
                ? runFunctionMode(problem, submission, cases)
                : runPerCase(problem, submission, cases, strategyOf(problem), runAllOf(problem));
        Verdict overall = Verdict.AC;
        for (CaseOutcome outcome : outcomes) {
            if (outcome.verdict() == Verdict.CE) {
                overall = Verdict.CE;
                break;
            }
            if (outcome.verdict() != Verdict.AC && overall == Verdict.AC) {
                overall = outcome.verdict();
            }
        }

        int passed = 0;
        int score = 0;
        long maxWall = 0;
        long maxMemory = 0;
        String compileMessage = null;
        for (int i = 0; i < outcomes.size(); i++) {
            CaseOutcome o = outcomes.get(i);
            if (o.verdict() == Verdict.AC) {
                passed++;
                score += Math.max(0, cases.get(i).getScore() == null ? 0 : cases.get(i).getScore());
            }
            maxWall = Math.max(maxWall, o.timeMs() == null ? 0 : o.timeMs());
            maxMemory = Math.max(maxMemory, o.memoryKb() == null ? 0 : o.memoryKb());
            if (o.verdict() == Verdict.CE) {
                compileMessage = o.message();
            }
        }

        int total = cases.size();
        if (passed == total && total > 0) {
            overall = Verdict.AC;
        }
        finish(submissionId, overall, passed, total, score, outcomes,
                compileMessage, (int) maxWall, (int) maxMemory);
        awardIfSolved(submission, problem, overall == Verdict.AC, score);
    }

    /**
     * ACM path: runs the submission once per test case with the case's {@code stdin}.
     *
     * <p>A compile error stops the loop immediately (no case can pass). Otherwise the loop stops at
     * the first non-AC case unless the problem opts into running everything
     * ({@code judge_config.runAll}), trading the wasted sandbox calls of an already-doomed
     * submission for the full per-case breakdown.</p>
     */
    private List<CaseOutcome> runPerCase(Problem problem, Submission submission,
            List<ProblemTestCase> cases, OutputComparator.Strategy strategy, boolean runAll) {
        List<CaseOutcome> outcomes = new ArrayList<>(cases.size());
        for (ProblemTestCase testCase : cases) {
            CaseOutcome outcome = runCase(problem, submission, testCase, strategy);
            outcomes.add(outcome);
            if (outcome.verdict() == Verdict.CE) {
                break;
            }
            if (!runAll && outcome.verdict() != Verdict.AC) {
                break;
            }
        }
        return outcomes;
    }

    /**
     * LeetCode-style function path: compiles and runs the language's generated harness <b>once</b>
     * with all cases bundled into a JSON array on stdin, then compares the harness's
     * one-JSON-line-per-case stdout against each case's expected output.
     *
     * <p>Actual execution lives in {@link FunctionRunner} (shared with the playground self-test);
     * this method only reshapes the result into the judge's per-case {@link CaseOutcome}.</p>
     */
    private List<CaseOutcome> runFunctionMode(Problem problem, Submission submission,
            List<ProblemTestCase> cases) {
        JudgeSignature signature = JudgeSignature.parse(judgeConfigOf(problem));
        List<FunctionRunner.Sample> samples = new ArrayList<>(cases.size());
        for (ProblemTestCase testCase : cases) {
            samples.add(new FunctionRunner.Sample(testCase.getStdin(), testCase.getExpectedOutput()));
        }
        FunctionRunner.HarnessRun run = functionRunner.run(problem, submission.getLanguage(),
                submission.getCode(), samples, signature);
        List<CaseOutcome> outcomes = new ArrayList<>(run.cases().size());
        for (FunctionRunner.RunCase outcome : run.cases()) {
            Verdict verdict = outcome.verdict() == null ? Verdict.WA : outcome.verdict();
            outcomes.add(new CaseOutcome(verdict, outcome.timeMs(), outcome.memoryKb(),
                    outcome.message()));
        }
        return outcomes;
    }

    private CaseOutcome runCase(Problem problem, Submission submission, ProblemTestCase testCase,
            OutputComparator.Strategy strategy) {
        Language language = submission.getLanguage();
        String pistonLanguage = language.getPistonName();
        ExecSpec spec = new ExecSpec(
                pistonLanguage,
                "*",
                language.fileName(),
                LanguagePrelude.wrap(pistonLanguage, submission.getCode()),
                testCase.getStdin() == null ? "" : testCase.getStdin(),
                sandbox.compileTimeoutMs(),
                problem.getTimeLimitMs(),
                problem.getMemoryLimitKb() * 1024L,
                problem.getMemoryLimitKb() * 1024L);

        ExecResult result;
        try {
            result = pistonClient.execute(spec);
        } catch (Exception ex) {
            log.warn("Sandbox call failed for submission {}: {}", submission.getId(), ex.getMessage());
            return new CaseOutcome(Verdict.SE, null, null, "sandbox unavailable");
        }

        String diagnostic = CompileErrors.diagnostic(pistonLanguage, result);
        if (diagnostic != null) {
            ExecResult.Stage stage = result.compile() != null ? result.compile() : result.run();
            return new CaseOutcome(Verdict.CE,
                    stage == null ? null : millis(stage.wallTimeMs()),
                    stage == null ? null : kb(stage.memoryBytes()),
                    CompileErrors.oneLine(diagnostic));
        }
        ExecResult.Stage run = result.run() != null ? result.run() : result.compile();
        if (run == null) {
            return new CaseOutcome(Verdict.SE, null, null, "no execution result");
        }
        Integer timeMs = millis(run.wallTimeMs());
        Integer memoryKb = kb(run.memoryBytes());
        String status = run.status();
        if ("TO".equals(status)) {
            return new CaseOutcome(Verdict.TLE, timeMs, memoryKb, "time limit exceeded");
        }
        if ("OL".equals(status) || "EL".equals(status)) {
            return new CaseOutcome(Verdict.OLE, timeMs, memoryKb, "output limit exceeded");
        }
        if ("RE".equals(status) || "SG".equals(status) || (run.code() != null && run.code() != 0)) {
            return new CaseOutcome(Verdict.RE, timeMs, memoryKb, "runtime error");
        }
        boolean match = OutputComparator.matches(testCase.getExpectedOutput(), run.stdout(), strategy);
        return new CaseOutcome(match ? Verdict.AC : Verdict.WA, timeMs, memoryKb,
                match ? null : "wrong answer");
    }

    /** True when the problem is LeetCode-mode, the language has a harness, and a signature is set. */
    private boolean useFunctionMode(Problem problem, Submission submission) {
        return problem.getIoMode() == IoMode.LEETCODE
                && functionRunner.supports(submission.getLanguage())
                && JudgeSignature.parse(judgeConfigOf(problem)) != null;
    }

    private static Map<String, Object> judgeConfigOf(Problem problem) {
        return JsonUtil.fromJson(problem.getJudgeConfig(), Map.class);
    }

    /**
     * True when the problem asks to run every ACM case even after the first failure
     * ({@code judge_config.runAll}); defaults to {@code false} (stop at first failure).
     */
    private static boolean runAllOf(Problem problem) {
        Map<String, Object> config = judgeConfigOf(problem);
        Object value = config == null ? null : config.get("runAll");
        if (value instanceof Boolean runAll) {
            return runAll;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private void markRunning(Long submissionId) {
        tx.executeWithoutResult(status -> submissions.findById(submissionId).ifPresent(s -> {
            s.setStatus(SubmissionStatus.RUNNING);
            submissions.save(s);
        }));
    }

    private void finish(Long submissionId, Verdict verdict, int passed, int total, int score,
            List<CaseOutcome> outcomes, String compileMessage) {
        finish(submissionId, verdict, passed, total, score, outcomes, compileMessage, 0, 0);
    }

    private void finish(Long submissionId, Verdict verdict, int passed, int total, int score,
            List<CaseOutcome> outcomes, String compileMessage, int timeMs, int memoryKb) {
        List<Map<String, Object>> cases = new ArrayList<>(outcomes.size());
        for (int i = 0; i < outcomes.size(); i++) {
            CaseOutcome o = outcomes.get(i);
            cases.add(Map.of(
                    "index", i,
                    "verdict", o.verdict().name(),
                    "timeMs", o.timeMs() == null ? 0 : o.timeMs(),
                    "memoryKb", o.memoryKb() == null ? 0 : o.memoryKb(),
                    "message", o.message() == null ? "" : o.message()));
        }
        tx.executeWithoutResult(status -> submissions.findById(submissionId).ifPresent(s -> {
            s.setStatus(SubmissionStatus.DONE);
            s.setVerdict(verdict);
            s.setPassed(passed);
            s.setTotal(total);
            s.setScore(score);
            s.setTimeMs(timeMs);
            s.setMemoryKb(memoryKb);
            s.setCaseResults(cases.isEmpty() ? null : JsonUtil.toJson(cases));
            s.setCompileMessage(compileMessage);
            s.setFinishedAt(Instant.now());
            submissions.save(s);
        }));
    }

    /**
     * Updates the user's per-problem status and awards points on first AC (idempotent per problem).
     *
     * <p>{@code score} is passed explicitly rather than read from {@code submission}: the entity
     * captured before judging still carries its initial (zero) score, because {@link #finish}
     * persists the real score through a separate transaction and a separate managed instance.</p>
     */
    private void awardIfSolved(Submission submission, Problem problem, boolean solved, int score) {
        tx.executeWithoutResult(status -> {
            UserProblemStatusId id = new UserProblemStatusId(submission.getUserId(), problem.getId());
            UserProblemStatus ups = userProblemStatus.findById(id).orElseGet(() -> {
                UserProblemStatus fresh = new UserProblemStatus();
                fresh.setId(id);
                return fresh;
            });
            boolean firstSolve = ups.getStatus() != ProblemStatus.SOLVED;
            ups.setAttempts(ups.getAttempts() + 1);
            ups.setLastSubmissionAt(Instant.now());
            if (solved) {
                ups.setStatus(ProblemStatus.SOLVED);
                ups.setBestVerdict(Verdict.AC);
                int currentBest = ups.getBestScore() == null ? 0 : ups.getBestScore();
                if (currentBest < score) {
                    ups.setBestScore(score);
                }
                if (firstSolve) {
                    ups.setFirstSolvedAt(Instant.now());
                }
            }
            userProblemStatus.save(ups);

            if (solved && firstSolve && problem.getRewardPoints() != null && problem.getRewardPoints() > 0) {
                users.findById(submission.getUserId()).ifPresent(user -> {
                    int balance = user.getPoints() + problem.getRewardPoints();
                    UserPointLog entry = new UserPointLog();
                    entry.setUserId(user.getId());
                    entry.setDelta(problem.getRewardPoints());
                    entry.setReason(PointReason.SOLVE);
                    entry.setRefType(PointRefType.PROBLEM);
                    entry.setRefId(problem.getId());
                    entry.setBalanceAfter(balance);
                    entry.setRemark("First solve: " + problem.getSlug());
                    pointLogs.save(entry);
                    user.setPoints(balance);
                    users.save(user);
                });
            }
        });
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

    /** Outcome of one test case. */
    private record CaseOutcome(Verdict verdict, Integer timeMs, Integer memoryKb, String message) {
    }
}
