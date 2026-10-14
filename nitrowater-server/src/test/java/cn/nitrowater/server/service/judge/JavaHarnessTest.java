package cn.nitrowater.server.service.judge;

import cn.nitrowater.server.service.judge.harness.GeneratedHarness;
import cn.nitrowater.server.service.judge.harness.JavaHarness;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Locks the shape of the generated LeetCode-style harness (single file, Main first, typed bindings). */
class JavaHarnessTest {

    private static JudgeSignature twoSum() {
        return JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "twoSum",
                "returnType", "int[]",
                "params", List.of(
                        Map.of("name", "nums", "type", "int[]"),
                        Map.of("name", "target", "type", "int")))));
    }

    @Test
    void placesUserClassAfterMainAndHoistsImports() {
        String user = "import java.util.*;\n\npublic class Solution {\n"
                + "  public int[] twoSum(int[] a, int t) { return new int[]{0, 1}; }\n}\n";

        String source = new JavaHarness().generate(twoSum(), user).source();

        assertThat(source).contains("public class Main");
        assertThat(source.indexOf("public class Main")).isLessThan(source.indexOf("class Solution"));
        assertThat(source.indexOf("import java.util.*;")).isLessThan(source.indexOf("public class Main"));
        assertThat(source).doesNotContain("public class Solution");
    }

    @Test
    void emitsTypedParamBindingsAndTheSolutionCall() {
        String source = new JavaHarness().generate(twoSum(), "class Solution {}").source();

        assertThat(source).contains("int[] nums = H.intArr(__m.get(\"nums\"));");
        assertThat(source).contains("int target = H.i(__m.get(\"target\"));");
        assertThat(source).contains("new Solution().twoSum(nums, target)");
    }

    @Test
    void userCodeLineOffsetPointsAtTheUsersFirstLine() {
        String user = "class Solution {\n  // body\n}\n";

        GeneratedHarness generated = new JavaHarness().generate(twoSum(), user);

        String[] lines = generated.source().split("\n", -1);
        assertThat(lines[generated.userCodeLineOffset()]).isEqualTo("class Solution {");
    }

    @Test
    void rejectsUnsupportedParamType() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "f",
                "returnType", "int",
                "params", List.of(Map.of("name", "x", "type", "Bitmap")))));

        assertThatThrownBy(() -> new JavaHarness().generate(signature, "class Solution {}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bitmap");
    }

    @Test
    void bindsTreeNodeParamAndInjectsNodeClass() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "maxDepth",
                "returnType", "int",
                "params", List.of(Map.of("name", "root", "type", "TreeNode")))));

        String source = new JavaHarness().generate(signature, "class Solution {}").source();

        assertThat(source).contains("TreeNode root = H.toTreeNode(__m.get(\"root\"));");
        assertThat(source).contains("class TreeNode {");
        assertThat(source).doesNotContain("class ListNode {");
    }

    @Test
    void serialisesListNodeReturn() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "reverseList",
                "returnType", "ListNode",
                "params", List.of(Map.of("name", "head", "type", "ListNode")))));

        String source = new JavaHarness().generate(signature, "class Solution {}").source();

        assertThat(source).contains("ListNode head = H.toListNode(__m.get(\"head\"));");
        assertThat(source).contains("H.listNodeJson((ListNode) __r)");
        assertThat(source).contains("class ListNode {");
        assertThat(source).doesNotContain("class TreeNode {");
    }

    @Test
    void doesNotInjectNodeClassTheUserAlreadyDefines() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "reverseList",
                "returnType", "ListNode",
                "params", List.of(Map.of("name", "head", "type", "ListNode")))));
        String user = "class Solution {\n"
                + "  ListNode reverseList(ListNode head) { return head; }\n}\n"
                + "class ListNode { int val; ListNode next; }\n";

        String source = new JavaHarness().generate(signature, user).source();

        assertThat(source.split("class ListNode", -1).length - 1).isEqualTo(1);
    }

    @Test
    void offsetStillPointsAtUserCodeWhenNodeClassesInjected() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "maxDepth",
                "returnType", "int",
                "params", List.of(Map.of("name", "root", "type", "TreeNode")))));
        String user = "class Solution {\n  int maxDepth(TreeNode root) { return 0; }\n}\n";

        GeneratedHarness generated = new JavaHarness().generate(signature, user);

        String[] lines = generated.source().split("\n", -1);
        assertThat(lines[generated.userCodeLineOffset()]).isEqualTo("class Solution {");
    }

    @Test
    void judgeSignatureParseReturnsNullWhenAbsentOrInvalid() {
        assertThat(JudgeSignature.parse(null)).isNull();
        assertThat(JudgeSignature.parse(Map.of())).isNull();
        assertThat(JudgeSignature.parse(Map.of("compare", "JSON"))).isNull();
        assertThat(JudgeSignature.parse(Map.of("signature", Map.of("returnType", "int[]")))).isNull();
    }
}
