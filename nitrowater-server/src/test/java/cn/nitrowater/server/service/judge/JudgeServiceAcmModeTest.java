package cn.nitrowater.server.service.judge;

import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.infra.entity.Problem;
import cn.nitrowater.server.infra.entity.ProblemTestCase;
import cn.nitrowater.server.infra.entity.Submission;
import cn.nitrowater.server.infra.entity.UserProblemStatusId;
import cn.nitrowater.server.infra.enums.IoMode;
import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.infra.enums.SubmissionStatus;
import cn.nitrowater.server.infra.enums.Verdict;
import cn.nitrowater.server.infra.repository.BizUserRepository;
import cn.nitrowater.server.infra.repository.ProblemRepository;
import cn.nitrowater.server.infra.repository.ProblemTestCaseRepository;
import cn.nitrowater.server.infra.repository.SubmissionRepository;
import cn.nitrowater.server.infra.repository.UserPointLogRepository;
import cn.nitrowater.server.infra.repository.UserProblemStatusRepository;
import cn.nitrowater.server.infra.third.PistonClient;
import cn.nitrowater.server.service.judge.harness.FunctionRunner;
import cn.nitrowater.server.service.judge.harness.JavaHarness;
import cn.nitrowater.server.service.judge.harness.PythonHarness;
import cn.nitrowater.server.service.shared.ExecResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks the ACM judging path's efficiency switches: by default it stops at the first non-AC case
 * (the verdict is already decided), and a problem can opt into running every case
 * ({@code judge_config.runAll}) to get the full per-case breakdown.
 */
@ExtendWith(MockitoExtension.class)
class JudgeServiceAcmModeTest {

    private static final long SUBMISSION_ID = 100L;
    private static final long USER_ID = 7L;
    private static final long PROBLEM_ID = 1L;

    @Mock private SubmissionRepository submissions;
    @Mock private ProblemRepository problems;
    @Mock private ProblemTestCaseRepository testCases;
    @Mock private BizUserRepository users;
    @Mock private UserProblemStatusRepository userProblemStatus;
    @Mock private UserPointLogRepository pointLogs;
    @Mock private PistonClient pistonClient;
    @Mock private TransactionTemplate tx;

    private JudgeService newService() {
        SandboxProperties sandbox = new SandboxProperties(
                "http://localhost:2000", 2000, 15000, 65536, 8, 20000, 60, 10000, 268435456L);
        FunctionRunner functionRunner = new FunctionRunner(pistonClient, sandbox,
                List.of(new JavaHarness(), new PythonHarness()));
        return new JudgeService(submissions, problems, testCases, users, userProblemStatus,
                pointLogs, pistonClient, sandbox, functionRunner, tx);
    }

    private void runInlineTransactions() {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    private static Submission queuedSubmission() {
        Submission s = new Submission();
        s.setId(SUBMISSION_ID);
        s.setUserId(USER_ID);
        s.setUid(999L);
        s.setProblemId(PROBLEM_ID);
        s.setLanguage(Language.JAVA);
        s.setCode("class Main { public static void main(String[] a) {} }");
        s.setStatus(SubmissionStatus.QUEUED);
        return s;
    }

    private static Problem acmProblem(String judgeConfig) {
        Problem p = new Problem();
        p.setId(PROBLEM_ID);
        p.setSlug("a-plus-b");
        p.setIoMode(IoMode.ACM);
        p.setJudgeConfig(judgeConfig);
        p.setTimeLimitMs(2000);
        p.setMemoryLimitKb(262144);
        p.setRewardPoints(10);
        return p;
    }

    private static ProblemTestCase caseOf(int idx, String expected) {
        ProblemTestCase c = new ProblemTestCase();
        c.setProblemId(PROBLEM_ID);
        c.setIdx(idx);
        c.setStdin("");
        c.setExpectedOutput(expected);
        c.setScore(10);
        return c;
    }

    private void stubCommon(Problem problem, List<ProblemTestCase> cases) {
        when(problems.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));
        when(testCases.findByProblemIdOrderByIdxAsc(PROBLEM_ID)).thenReturn(cases);
        when(submissions.findById(SUBMISSION_ID)).thenAnswer(inv -> Optional.of(queuedSubmission()));
        when(userProblemStatus.findById(any(UserProblemStatusId.class))).thenReturn(Optional.empty());
    }

    private static ExecResult result(String stdout) {
        ExecResult.Stage run = new ExecResult.Stage(stdout, "", stdout, 0, null, null, null, 1L, 5L, 1024L);
        return new ExecResult(null, run, "java", "15.0.2");
    }

    private Submission finishedSubmission() {
        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissions, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(s -> s.getStatus() == SubmissionStatus.DONE)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void stopsAtFirstFailureByDefault() {
        JudgeService service = newService();
        runInlineTransactions();
        stubCommon(acmProblem(null), List.of(caseOf(0, "1"), caseOf(1, "2"), caseOf(2, "3")));
        when(pistonClient.execute(any()))
                .thenReturn(result("1"))
                .thenReturn(result("999"))
                .thenReturn(result("3"));

        service.judge(SUBMISSION_ID);

        // Case 0 passes, case 1 fails -> case 2 is never run.
        verify(pistonClient, times(2)).execute(any());
        Submission finished = finishedSubmission();
        assertThat(finished.getVerdict()).isEqualTo(Verdict.WA);
        assertThat(finished.getPassed()).isEqualTo(1);
        assertThat(finished.getTotal()).isEqualTo(3);
        assertThat(finished.getScore()).isEqualTo(10);
    }

    @Test
    void runsEveryCaseWhenRunAllIsEnabled() {
        JudgeService service = newService();
        runInlineTransactions();
        stubCommon(acmProblem("{\"runAll\":true}"),
                List.of(caseOf(0, "1"), caseOf(1, "2"), caseOf(2, "3")));
        when(pistonClient.execute(any()))
                .thenReturn(result("1"))
                .thenReturn(result("999"))
                .thenReturn(result("3"));

        service.judge(SUBMISSION_ID);

        verify(pistonClient, times(3)).execute(any());
        Submission finished = finishedSubmission();
        assertThat(finished.getVerdict()).isEqualTo(Verdict.WA);
        assertThat(finished.getPassed()).isEqualTo(2);
        assertThat(finished.getTotal()).isEqualTo(3);
        assertThat(finished.getScore()).isEqualTo(20);
    }
}
