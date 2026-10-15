package cn.nitrowater.server.service.judge;

import cn.nitrowater.server.config.properties.SandboxProperties;
import cn.nitrowater.server.infra.entity.BizUser;
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
import cn.nitrowater.server.service.judge.harness.CHarness;
import cn.nitrowater.server.service.judge.harness.CppHarness;
import cn.nitrowater.server.service.judge.harness.FunctionRunner;
import cn.nitrowater.server.service.judge.harness.JavaHarness;
import cn.nitrowater.server.service.judge.harness.PythonHarness;
import cn.nitrowater.server.service.shared.ExecResult;
import cn.nitrowater.server.service.shared.ExecSpec;
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
import static org.mockito.Mockito.when;

/**
 * Locks the LeetCode-style function-mode path: one harness run covers every case, the harness is
 * sent as the single {@code Main} file, and a compile error marks the whole submission CE.
 */
@ExtendWith(MockitoExtension.class)
class JudgeServiceFunctionModeTest {

    private static final long SUBMISSION_ID = 100L;
    private static final long USER_ID = 7L;
    private static final long PROBLEM_ID = 1L;

    private static final String SIGNATURE_JSON = """
            {"compare":"JSON","signature":{"method":"twoSum","returnType":"int[]",
            "params":[{"name":"nums","type":"int[]"},{"name":"target","type":"int"}]}}""";

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
                List.of(new JavaHarness(), new PythonHarness(), new CHarness(), new CppHarness()));
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
        s.setCode("class Solution { public int[] twoSum(int[] nums, int target) { return new int[]{0,1}; } }");
        s.setStatus(SubmissionStatus.QUEUED);
        return s;
    }

    private static Problem leetcodeProblem() {
        Problem p = new Problem();
        p.setId(PROBLEM_ID);
        p.setSlug("two-sum");
        p.setIoMode(IoMode.LEETCODE);
        p.setJudgeConfig(SIGNATURE_JSON);
        p.setTimeLimitMs(2000);
        p.setMemoryLimitKb(262144);
        p.setRewardPoints(10);
        return p;
    }

    private static ProblemTestCase caseOf(int idx, String stdin, String expected) {
        ProblemTestCase c = new ProblemTestCase();
        c.setProblemId(PROBLEM_ID);
        c.setIdx(idx);
        c.setStdin(stdin);
        c.setExpectedOutput(expected);
        c.setScore(10);
        return c;
    }

    private static List<ProblemTestCase> twoCases() {
        return List.of(
                caseOf(0, "{\"nums\":[2,7,11,15],\"target\":9}", "[0,1]"),
                caseOf(1, "{\"nums\":[3,2,4],\"target\":6}", "[1,2]"));
    }

    private void stubCommon(List<ProblemTestCase> cases) {
        when(problems.findById(PROBLEM_ID)).thenReturn(Optional.of(leetcodeProblem()));
        when(testCases.findByProblemIdOrderByIdxAsc(PROBLEM_ID)).thenReturn(cases);
        when(submissions.findById(SUBMISSION_ID)).thenAnswer(inv -> Optional.of(queuedSubmission()));
        when(userProblemStatus.findById(any(UserProblemStatusId.class))).thenReturn(Optional.empty());
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
    void acceptsAllCasesFromASingleHarnessRun() {
        JudgeService service = newService();
        runInlineTransactions();
        stubCommon(twoCases());
        BizUser user = new BizUser();
        user.setId(USER_ID);
        user.setPoints(0);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        ExecResult.Stage run = new ExecResult.Stage("[0,1]\n[1,2]\n", "", "[0,1]\n[1,2]\n", 0, null, null,
                null, 1L, 900L, 1024L);
        when(pistonClient.execute(any())).thenReturn(new ExecResult(null, run, "java", "15.0.2"));

        service.judge(SUBMISSION_ID);

        ArgumentCaptor<ExecSpec> spec = ArgumentCaptor.forClass(ExecSpec.class);
        verify(pistonClient).execute(spec.capture());
        assertThat(spec.getValue().fileName()).isEqualTo("Main");
        assertThat(spec.getValue().code()).contains("public class Main").contains("class Solution");
        assertThat(spec.getValue().stdin())
                .isEqualTo("[{\"nums\":[2,7,11,15],\"target\":9},{\"nums\":[3,2,4],\"target\":6}]");

        Submission finished = finishedSubmission();
        assertThat(finished.getVerdict()).isEqualTo(Verdict.AC);
        assertThat(finished.getPassed()).isEqualTo(2);
        assertThat(finished.getTotal()).isEqualTo(2);
        assertThat(finished.getScore()).isEqualTo(20);
    }

    @Test
    void acceptsAllCasesFromASinglePythonHarnessRun() {
        JudgeService service = newService();
        runInlineTransactions();
        stubCommon(twoCases());
        Submission python = queuedSubmission();
        python.setLanguage(Language.PYTHON);
        python.setCode("class Solution:\n    def twoSum(self, nums, target):\n        return [0, 1]\n");
        when(submissions.findById(SUBMISSION_ID)).thenReturn(Optional.of(python));
        BizUser user = new BizUser();
        user.setId(USER_ID);
        user.setPoints(0);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        ExecResult.Stage run = new ExecResult.Stage("[0,1]\n[1,2]\n", "", "[0,1]\n[1,2]\n", 0, null, null,
                null, 1L, 900L, 1024L);
        when(pistonClient.execute(any())).thenReturn(new ExecResult(null, run, "python", "3.10.0"));

        service.judge(SUBMISSION_ID);

        ArgumentCaptor<ExecSpec> spec = ArgumentCaptor.forClass(ExecSpec.class);
        verify(pistonClient).execute(spec.capture());
        assertThat(spec.getValue().pistonLanguage()).isEqualTo("python");
        assertThat(spec.getValue().fileName()).isEqualTo("main.py");
        assertThat(spec.getValue().code()).contains("class Solution").contains("_nitro_sol.twoSum(");
        assertThat(spec.getValue().stdin())
                .isEqualTo("[{\"nums\":[2,7,11,15],\"target\":9},{\"nums\":[3,2,4],\"target\":6}]");

        Submission finished = finishedSubmission();
        assertThat(finished.getVerdict()).isEqualTo(Verdict.AC);
        assertThat(finished.getPassed()).isEqualTo(2);
        assertThat(finished.getTotal()).isEqualTo(2);
        assertThat(finished.getScore()).isEqualTo(20);
    }

    @Test
    void acceptsAllCasesFromASingleCHarnessRun() {
        JudgeService service = newService();
        runInlineTransactions();
        stubCommon(twoCases());
        Submission c = queuedSubmission();
        c.setLanguage(Language.C);
        c.setCode("int *twoSum(int* nums, int numsSize, int target, int* returnSize) { returnSize[0] = 2; return nums; }");
        when(submissions.findById(SUBMISSION_ID)).thenReturn(Optional.of(c));
        BizUser user = new BizUser();
        user.setId(USER_ID);
        user.setPoints(0);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        ExecResult.Stage run = new ExecResult.Stage("[0,1]\n[1,2]\n", "", "[0,1]\n[1,2]\n", 0, null, null,
                null, 1L, 900L, 1024L);
        when(pistonClient.execute(any())).thenReturn(new ExecResult(null, run, "c", "10.2.0"));

        service.judge(SUBMISSION_ID);

        ArgumentCaptor<ExecSpec> spec = ArgumentCaptor.forClass(ExecSpec.class);
        verify(pistonClient).execute(spec.capture());
        assertThat(spec.getValue().pistonLanguage()).isEqualTo("c");
        assertThat(spec.getValue().fileName()).isEqualTo("main");
        assertThat(spec.getValue().code())
                .contains("int main(void)")
                .contains("#line 1")
                .contains("twoSum(__nt_a_nums, __nt_a_nums_n, __nt_a_target, &__rs)");
        assertThat(spec.getValue().stdin())
                .isEqualTo("[{\"nums\":[2,7,11,15],\"target\":9},{\"nums\":[3,2,4],\"target\":6}]");

        Submission finished = finishedSubmission();
        assertThat(finished.getVerdict()).isEqualTo(Verdict.AC);
        assertThat(finished.getPassed()).isEqualTo(2);
        assertThat(finished.getTotal()).isEqualTo(2);
        assertThat(finished.getScore()).isEqualTo(20);
    }

    @Test
    void acceptsAllCasesFromASingleCppHarnessRun() {
        JudgeService service = newService();
        runInlineTransactions();
        stubCommon(twoCases());
        Submission cpp = queuedSubmission();
        cpp.setLanguage(Language.CPP);
        cpp.setCode("class Solution { public: vector<int> twoSum(vector<int>& nums, int target) { return {0, 1}; } };");
        when(submissions.findById(SUBMISSION_ID)).thenReturn(Optional.of(cpp));
        BizUser user = new BizUser();
        user.setId(USER_ID);
        user.setPoints(0);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        ExecResult.Stage run = new ExecResult.Stage("[0,1]\n[1,2]\n", "", "[0,1]\n[1,2]\n", 0, null, null,
                null, 1L, 900L, 1024L);
        when(pistonClient.execute(any())).thenReturn(new ExecResult(null, run, "c++", "10.2.0"));

        service.judge(SUBMISSION_ID);

        ArgumentCaptor<ExecSpec> spec = ArgumentCaptor.forClass(ExecSpec.class);
        verify(pistonClient).execute(spec.capture());
        assertThat(spec.getValue().pistonLanguage()).isEqualTo("c++");
        assertThat(spec.getValue().fileName()).isEqualTo("main");
        assertThat(spec.getValue().code())
                .contains("using namespace std;")
                .contains("int main(void)")
                .contains("Solution().twoSum(");
        assertThat(spec.getValue().stdin())
                .isEqualTo("[{\"nums\":[2,7,11,15],\"target\":9},{\"nums\":[3,2,4],\"target\":6}]");

        Submission finished = finishedSubmission();
        assertThat(finished.getVerdict()).isEqualTo(Verdict.AC);
        assertThat(finished.getPassed()).isEqualTo(2);
        assertThat(finished.getTotal()).isEqualTo(2);
        assertThat(finished.getScore()).isEqualTo(20);
    }

    @Test
    void marksWrongAnswerWhenOneCaseDiffers() {
        JudgeService service = newService();
        runInlineTransactions();
        stubCommon(twoCases());
        ExecResult.Stage run = new ExecResult.Stage("[0,1]\n[9,9]\n", "", "[0,1]\n[9,9]\n", 0, null, null,
                null, 1L, 900L, 1024L);
        when(pistonClient.execute(any())).thenReturn(new ExecResult(null, run, "java", "15.0.2"));

        service.judge(SUBMISSION_ID);

        Submission finished = finishedSubmission();
        assertThat(finished.getVerdict()).isEqualTo(Verdict.WA);
        assertThat(finished.getPassed()).isEqualTo(1);
        assertThat(finished.getScore()).isEqualTo(10);
    }

    @Test
    void marksTheWholeSubmissionCompileErrorWhenHarnessFailsToCompile() {
        JudgeService service = newService();
        runInlineTransactions();
        stubCommon(twoCases());
        ExecResult.Stage run = new ExecResult.Stage("", "Main.java:2: error: ';' expected\n1 error\nerror: compilation failed\n",
                "", 1, null, "RE", "Exited with error status 1", null, 300L, 1024L);
        when(pistonClient.execute(any())).thenReturn(new ExecResult(null, run, "java", "15.0.2"));

        service.judge(SUBMISSION_ID);

        Submission finished = finishedSubmission();
        assertThat(finished.getVerdict()).isEqualTo(Verdict.CE);
        assertThat(finished.getPassed()).isZero();
        assertThat(finished.getCompileMessage()).contains("Main.java:").contains("error:");
    }
}
