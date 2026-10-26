package cn.nitrowater.server.service.judge;

import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.infra.third.PistonClient;
import cn.nitrowater.server.service.judge.harness.CHarness;
import cn.nitrowater.server.service.judge.harness.CppHarness;
import cn.nitrowater.server.service.judge.harness.FunctionHarness;
import cn.nitrowater.server.service.judge.harness.GeneratedHarness;
import cn.nitrowater.server.service.judge.harness.JavaHarness;
import cn.nitrowater.server.service.judge.harness.PythonHarness;
import cn.nitrowater.server.service.shared.ExecResult;
import cn.nitrowater.server.service.shared.ExecSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end golden tests for the function-mode harnesses: the <em>generated</em> program must
 * compile and, fed the judge's stdin shape (a JSON array of samples), print one answer per sample.
 *
 * <p>Unlike the structural harness tests, this drives a real sandbox, so it is what protects a
 * harness refactor from silently changing verdicts. Skipped when Piston is unreachable.</p>
 */
class HarnessSandboxGoldenTest {

    private static final String PISTON_URL =
            System.getenv().getOrDefault("PISTON_URL", "http://localhost:2000");

    /** Two samples, exactly how FunctionRunner hands them to a harness. */
    private static final String STDIN =
            "[{\"nums\":[2,7,11,15],\"target\":9},{\"nums\":[3,2,4],\"target\":6}]";

    private static final String JAVA_SOLUTION = """
            class Solution {
                public int[] twoSum(int[] nums, int target) {
                    for (int i = 0; i < nums.length; i++)
                        for (int j = i + 1; j < nums.length; j++)
                            if (nums[i] + nums[j] == target) return new int[]{i, j};
                    return new int[0];
                }
            }""";

    private static final String PYTHON_SOLUTION = """
            class Solution:
                def twoSum(self, nums, target):
                    for i in range(len(nums)):
                        for j in range(i + 1, len(nums)):
                            if nums[i] + nums[j] == target:
                                return [i, j]
                    return []
            """;

    private static final String C_SOLUTION = """
            int* twoSum(int* nums, int numsSize, int target, int* returnSize) {
                for (int i = 0; i < numsSize; i++)
                    for (int j = i + 1; j < numsSize; j++)
                        if (nums[i] + nums[j] == target) {
                            int* r = malloc(2 * sizeof(int));
                            r[0] = i; r[1] = j; *returnSize = 2; return r;
                        }
                *returnSize = 0;
                return NULL;
            }
            """;

    private static final String CPP_SOLUTION = """
            class Solution {
            public:
                vector<int> twoSum(vector<int>& nums, int target) {
                    for (int i = 0; i < (int) nums.size(); i++)
                        for (int j = i + 1; j < (int) nums.size(); j++)
                            if (nums[i] + nums[j] == target) return {i, j};
                    return {};
                }
            };""";

    private static PistonClient piston;

    @BeforeAll
    static void requirePiston() {
        assumeTrue(reachable(), "Piston not reachable at " + PISTON_URL);
        // HTTP/1.1 is mandatory: the JDK client's default h2c upgrade makes Piston answer 400
        // (same pin as the production bean in PistonConfig).
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(60));
        piston = new PistonClient(RestClient.builder().baseUrl(PISTON_URL).requestFactory(factory).build());
    }

    @Test
    @DisplayName("Java harness prints one answer per sample")
    void javaHarness() {
        runAndExpect(new JavaHarness(), Language.JAVA, JAVA_SOLUTION);
    }

    @Test
    @DisplayName("Python harness prints one answer per sample")
    void pythonHarness() {
        runAndExpect(new PythonHarness(), Language.PYTHON, PYTHON_SOLUTION);
    }

    @Test
    @DisplayName("C harness prints one answer per sample")
    void cHarness() {
        runAndExpect(new CHarness(), Language.C, C_SOLUTION);
    }

    @Test
    @DisplayName("C++ harness prints one answer per sample")
    void cppHarness() {
        runAndExpect(new CppHarness(), Language.CPP, CPP_SOLUTION);
    }

    private static void runAndExpect(FunctionHarness harness, Language language, String userCode) {
        GeneratedHarness generated = harness.generate(twoSum(), userCode);

        ExecSpec spec = new ExecSpec(
                language.getPistonName(),
                "*",
                language.fileName(),
                generated.source(),
                STDIN,
                15_000,
                5_000,
                256L * 1024 * 1024,
                256L * 1024 * 1024);

        ExecResult result = piston.execute(spec);

        assertThat(result.run()).as("%s run stage present", language).isNotNull();
        assertThat(result.run().stdout())
                .as("%s output (stderr=%s)", language, result.run().stderr())
                .contains("[0,1]")
                .contains("[1,2]");
    }

    private static JudgeSignature twoSum() {
        return JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "twoSum",
                "returnType", "int[]",
                "params", List.of(
                        Map.of("name", "nums", "type", "int[]"),
                        Map.of("name", "target", "type", "int")))));
    }

    private static boolean reachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 2000), 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
