package cn.nitrowater.server.service.judge;

import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.infra.entity.BizUser;
import cn.nitrowater.server.infra.entity.Problem;
import cn.nitrowater.server.infra.entity.ProblemTestCase;
import cn.nitrowater.server.infra.entity.Submission;
import cn.nitrowater.server.infra.entity.UserProblemStatus;
import cn.nitrowater.server.infra.entity.UserProblemStatusId;
import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.infra.enums.ProblemStatus;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks the write-back side of judging: an accepted submission must persist its awarded score as the
 * user's per-problem {@code best_score}.
 *
 * <p>Regression guard: {@code JudgeService} used to read the score off the {@link Submission} entity
 * captured at the start of {@link JudgeService#judge}, which still carried its initial zero because
 * {@code finish} persists the real score through a separate transaction and a separate managed
 * instance. {@code best_score} therefore stayed {@code 0} forever. The repository mocks below return
 * a <em>fresh</em> entity per {@code findById} call so that staleness is reproduced faithfully.</p>
 */
@ExtendWith(MockitoExtension.class)
class JudgeServiceTest {

    private static final long SUBMISSION_ID = 100L;
    private static final long USER_ID = 7L;
    private static final long PROBLEM_ID = 1L;
    private static final int CASE_SCORE = 10;

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

    /** A fresh queued submission whose score starts at zero (mirrors the pre-judge DB row). */
    private static Submission queuedSubmission() {
        Submission s = new Submission();
        s.setId(SUBMISSION_ID);
        s.setUserId(USER_ID);
        s.setUid(999L);
        s.setProblemId(PROBLEM_ID);
        s.setLanguage(Language.JAVA);
        s.setCode("class Main {}");
        s.setStatus(SubmissionStatus.QUEUED);
        return s;
    }

    private static Problem problem() {
        Problem p = new Problem();
        p.setId(PROBLEM_ID);
        p.setSlug("two-sum");
        p.setTimeLimitMs(2000);
        p.setMemoryLimitKb(262144);
        p.setRewardPoints(10);
        return p;
    }

    private static ProblemTestCase acceptedCase() {
        ProblemTestCase c = new ProblemTestCase();
        c.setProblemId(PROBLEM_ID);
        c.setIdx(0);
        c.setStdin("");
        c.setExpectedOutput("42\n");
        c.setScore(CASE_SCORE);
        return c;
    }

    private void runInlineTransactions() {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    private void stubAcceptedRun() {
        ExecResult.Stage run = new ExecResult.Stage(
                "42\n", "", "42\n", 0, null, null, null, 1L, 5L, 1024L);
        when(pistonClient.execute(any())).thenReturn(new ExecResult(null, run, "java", "15.0.2"));
        when(problems.findById(PROBLEM_ID)).thenReturn(Optional.of(problem()));
        when(testCases.findByProblemIdOrderByIdxAsc(PROBLEM_ID)).thenReturn(List.of(acceptedCase()));
    }

    @Test
    void persistsAwardedScoreAsBestScoreOnSolve() {
        JudgeService service = newService();
        runInlineTransactions();
        stubAcceptedRun();
        // Each read returns a distinct managed-like instance; only the last (finish) is mutated.
        when(submissions.findById(SUBMISSION_ID)).thenAnswer(inv -> Optional.of(queuedSubmission()));
        when(userProblemStatus.findById(any(UserProblemStatusId.class))).thenReturn(Optional.empty());
        BizUser user = new BizUser();
        user.setId(USER_ID);
        user.setPoints(0);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));

        service.judge(SUBMISSION_ID);

        ArgumentCaptor<UserProblemStatus> captor = ArgumentCaptor.forClass(UserProblemStatus.class);
        verify(userProblemStatus).save(captor.capture());
        UserProblemStatus saved = captor.getValue();
        assertThat(saved.getBestScore()).isEqualTo(CASE_SCORE);
        assertThat(saved.getStatus()).isEqualTo(ProblemStatus.SOLVED);
        assertThat(saved.getBestVerdict()).isEqualTo(Verdict.AC);
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getFirstSolvedAt()).isNotNull();
        verify(pointLogs).save(any());
    }

    @Test
    void keepsHigherBestScoreWhenResubmittedWithLowerScore() {
        JudgeService service = newService();
        runInlineTransactions();
        stubAcceptedRun();
        when(submissions.findById(SUBMISSION_ID)).thenAnswer(inv -> Optional.of(queuedSubmission()));

        UserProblemStatus existing = new UserProblemStatus();
        existing.setId(new UserProblemStatusId(USER_ID, PROBLEM_ID));
        existing.setStatus(ProblemStatus.SOLVED);
        existing.setBestVerdict(Verdict.AC);
        existing.setBestScore(50);
        when(userProblemStatus.findById(any(UserProblemStatusId.class))).thenReturn(Optional.of(existing));

        service.judge(SUBMISSION_ID);

        ArgumentCaptor<UserProblemStatus> captor = ArgumentCaptor.forClass(UserProblemStatus.class);
        verify(userProblemStatus).save(captor.capture());
        assertThat(captor.getValue().getBestScore()).isEqualTo(50);
        // Already solved -> no duplicate reward and no user lookup.
        verify(users, never()).findById(any());
        verify(pointLogs, never()).save(any());
    }
}
