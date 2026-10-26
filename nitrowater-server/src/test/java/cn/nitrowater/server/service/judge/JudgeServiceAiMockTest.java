package cn.nitrowater.server.service.judge;

import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.infra.entity.Problem;
import cn.nitrowater.server.infra.entity.Submission;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito demo for the complex {@link JudgeService} orchestration.
 *
 * <p>Only early-exit / missing-input branches are pinned here; the full AC/WA/runAll paths
 * already live in {@link JudgeServiceAcmModeTest} and {@link JudgeServiceFunctionModeTest}.
 * The point is to show how repositories and the sandbox client are replaced by mocks so the
 * business class can be tested without MySQL, Redis or Piston.</p>
 */
@ExtendWith(MockitoExtension.class)
class JudgeServiceAiMockTest {

    private static final long SUBMISSION_ID = 100L;
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
        FunctionRunner functionRunner = new FunctionRunner(
                pistonClient, sandbox, List.of(new JavaHarness(), new PythonHarness()));
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

    private static Submission submission(SubmissionStatus status) {
        Submission submission = new Submission();
        submission.setId(SUBMISSION_ID);
        submission.setUserId(7L);
        submission.setUid(999L);
        submission.setProblemId(PROBLEM_ID);
        submission.setStatus(status);
        return submission;
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
    void missingSubmissionDoesNotTouchSandboxOrProblem() {
        when(submissions.findById(SUBMISSION_ID)).thenReturn(Optional.empty());

        newService().judge(SUBMISSION_ID);

        verifyNoInteractions(problems, testCases, pistonClient);
    }

    @Test
    void finishedSubmissionIsIgnored() {
        when(submissions.findById(SUBMISSION_ID))
                .thenReturn(Optional.of(submission(SubmissionStatus.DONE)));

        newService().judge(SUBMISSION_ID);

        verifyNoInteractions(problems, testCases, pistonClient);
    }

    @Test
    void missingProblemFinishesWithSystemError() {
        JudgeService service = newService();
        runInlineTransactions();
        when(submissions.findById(SUBMISSION_ID))
                .thenReturn(Optional.of(submission(SubmissionStatus.QUEUED)));
        when(problems.findById(PROBLEM_ID)).thenReturn(Optional.empty());

        service.judge(SUBMISSION_ID);

        Submission finished = finishedSubmission();
        assertThat(finished.getStatus()).isEqualTo(SubmissionStatus.DONE);
        assertThat(finished.getVerdict()).isEqualTo(Verdict.SE);
        assertThat(finished.getPassed()).isZero();
        verifyNoInteractions(pistonClient);
    }

    @Test
    void problemWithoutTestCasesFinishesWithSystemError() {
        JudgeService service = newService();
        runInlineTransactions();
        when(submissions.findById(SUBMISSION_ID))
                .thenReturn(Optional.of(submission(SubmissionStatus.QUEUED)));
        Problem problem = new Problem();
        problem.setId(PROBLEM_ID);
        when(problems.findById(PROBLEM_ID)).thenReturn(Optional.of(problem));
        when(testCases.findByProblemIdOrderByIdxAsc(PROBLEM_ID)).thenReturn(List.of());

        service.judge(SUBMISSION_ID);

        Submission finished = finishedSubmission();
        assertThat(finished.getVerdict()).isEqualTo(Verdict.SE);
        assertThat(finished.getTotal()).isZero();
        verifyNoInteractions(pistonClient);
    }
}
