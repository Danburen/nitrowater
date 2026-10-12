package cn.nitrowater.server.service.judge;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.infra.entity.Submission;
import cn.nitrowater.server.infra.entity.Problem;
import cn.nitrowater.server.infra.entity.ProblemTestCase;
import cn.nitrowater.server.infra.entity.UserPointLog;
import cn.nitrowater.server.infra.entity.UserProblemStatus;
import cn.nitrowater.server.infra.entity.UserProblemStatusId;
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
import cn.nitrowater.server.service.shared.ExecResult;
import cn.nitrowater.server.service.shared.ExecSpec;
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
    private final TransactionTemplate tx;

    public JudgeService(SubmissionRepository submissions, ProblemRepository problems,
            ProblemTestCaseRepository testCases, BizUserRepository users,
            UserProblemStatusRepository userProblemStatus, UserPointLogRepository pointLogs,
            PistonClient pistonClient, SandboxProperties sandbox, TransactionTemplate tx) {
        this.submissions = submissions;
        this.problems = problems;
        this.testCases = testCases;
        this.users = users;
        this.userProblemStatus = userProblemStatus;
        this.pointLogs = pointLogs;
        this.pistonClient = pistonClient;
        this.sandbox = sandbox;
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

        OutputComparator.Strategy strategy = strategyOf(problem);
        List<CaseOutcome> outcomes = new ArrayList<>(cases.size());
        Verdict overall = Verdict.AC;
        for (ProblemTestCase testCase : cases) {
            CaseOutcome outcome = runCase(problem, submission, testCase, strategy);
            outcomes.add(outcome);
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

    private CaseOutcome runCase(Problem problem, Submission submission, ProblemTestCase testCase,
            OutputComparator.Strategy strategy) {
        Language language = submission.getLanguage();
        ExecSpec spec = new ExecSpec(
                language.getPistonName(),
                "*",
                fileNameFor(language),
                submission.getCode(),
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

        ExecResult.Stage compile = result.compile();
        if (compile != null && compile.code() != null && compile.code() != 0) {
            return new CaseOutcome(Verdict.CE, millis(compile.wallTimeMs()), kb(compile.memoryBytes()),
                    truncate(compile.stderr()));
        }
        ExecResult.Stage run = result.run() != null ? result.run() : compile;
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
        Map<String, Object> config = JsonUtil.fromJson(problem.getJudgeConfig(), Map.class);
        Object compare = config == null ? null : config.get("compare");
        return OutputComparator.strategyOf(compare == null ? null : String.valueOf(compare));
    }

    private static String fileNameFor(Language language) {
        return switch (language) {
            case JAVA -> "Main.java";
            case C -> "main.c";
            case CPP -> "main.cpp";
            case PYTHON -> "main.py";
            case JAVASCRIPT -> "main.js";
            case TYPESCRIPT -> "main.ts";
            case LUA -> "main.lua";
        };
    }

    private static Integer millis(Long value) {
        return value == null ? null : (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static Integer kb(Long bytes) {
        return bytes == null ? null : (int) Math.min(Integer.MAX_VALUE, bytes / 1024);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    /** Outcome of one test case. */
    private record CaseOutcome(Verdict verdict, Integer timeMs, Integer memoryKb, String message) {
    }
}
