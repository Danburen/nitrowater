package cn.nitrowater.server.service.judge;

import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.service.judge.harness.GeneratedHarness;
import cn.nitrowater.server.service.judge.harness.PythonHarness;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the shape of the generated Python harness: the prelude prefix, the user's code verbatim, and
 * a driver that binds the case's parameters positionally and prints one JSON value per case.
 */
class PythonHarnessTest {

    private static JudgeSignature twoSum() {
        return JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "twoSum",
                "returnType", "int[]",
                "params", List.of(
                        Map.of("name", "nums", "type", "int[]"),
                        Map.of("name", "target", "type", "int")))));
    }

    @Test
    void reportsPythonAsItsLanguage() {
        assertThat(new PythonHarness().language()).isEqualTo(Language.PYTHON);
    }

    @Test
    void emitsPreludeBeforeUserCodeAndBindsParamsByName() {
        String user = "class Solution:\n    def twoSum(self, nums, target):\n        return [0, 1]\n";

        String source = new PythonHarness().generate(twoSum(), user).source();

        assertThat(source).contains("import sys");
        assertThat(source).contains("class Solution:");
        assertThat(source.indexOf("class Solution:")).isLessThan(source.indexOf("def _nitro_run"));
        assertThat(source).contains("_nitro_sol.twoSum(_nitro_map.get(\"nums\"), _nitro_map.get(\"target\"))");
    }

    @Test
    void userCodeLineOffsetPointsAtTheUsersFirstLine() {
        String user = "class Solution:\n    def twoSum(self, nums, target):\n        return [0, 1]\n";

        GeneratedHarness generated = new PythonHarness().generate(twoSum(), user);

        String[] lines = generated.source().split("\n", -1);
        assertThat(lines[generated.userCodeLineOffset()]).isEqualTo("class Solution:");
    }

    @Test
    void bindsNoArgsWhenSignatureHasNoParams() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "solve", "returnType", "int")));
        String user = "class Solution:\n    def solve(self):\n        return 1\n";

        String source = new PythonHarness().generate(signature, user).source();

        assertThat(source).contains("_nitro_sol.solve()");
    }

    @Test
    void injectsNodeClassesAndConvertsParams() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "maxDepth",
                "returnType", "int",
                "params", List.of(Map.of("name", "root", "type", "TreeNode")))));

        String source = new PythonHarness()
                .generate(signature, "class Solution:\n    def maxDepth(self, root):\n        return 0\n")
                .source();

        assertThat(source).contains("class TreeNode:");
        assertThat(source).doesNotContain("class ListNode:");
        assertThat(source).contains("_nitro_to_tree(_nitro_map.get(\"root\"))");
        assertThat(source).contains("_nitro_dump(");
    }

    @Test
    void bindsListNodeParamAndKeepsUserDefinition() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "reverseList",
                "returnType", "ListNode",
                "params", List.of(Map.of("name", "head", "type", "ListNode")))));
        String user = "class ListNode:\n    def __init__(self, val=0, next=None):\n"
                + "        self.val = val\n        self.next = next\n\n"
                + "class Solution:\n    def reverseList(self, head):\n        return head\n";

        String source = new PythonHarness().generate(signature, user).source();

        assertThat(source.split("class ListNode:", -1).length - 1).isEqualTo(1);
        assertThat(source).contains("_nitro_to_list(_nitro_map.get(\"head\"))");
        assertThat(source).contains("_nitro_ser_list(_nitro_sol.reverseList(_nitro_to_list(_nitro_map.get(\"head\"))))");
    }

    @Test
    void offsetStillPointsAtUserCodeWhenNodeClassesInjected() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "maxDepth",
                "returnType", "int",
                "params", List.of(Map.of("name", "root", "type", "TreeNode")))));
        String user = "class Solution:\n    def maxDepth(self, root):\n        return 0\n";

        GeneratedHarness generated = new PythonHarness().generate(signature, user);

        String[] lines = generated.source().split("\n", -1);
        assertThat(lines[generated.userCodeLineOffset()]).isEqualTo("class Solution:");
    }
}
