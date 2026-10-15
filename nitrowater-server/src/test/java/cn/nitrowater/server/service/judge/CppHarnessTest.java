package cn.nitrowater.server.service.judge;

import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.service.judge.harness.CppHarness;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks the shape of the generated C++ harness: the {@code <bits/stdc++.h>} header, the self-contained
 * JSON reader reused from the C template, a {@code main} that binds each case and prints one JSON
 * result per case, and the C++ call convention ({@code Solution().method(...)} when the submission
 * declares a {@code Solution} class, a bare call otherwise).
 */
class CppHarnessTest {

    private static JudgeSignature twoSum() {
        return JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "twoSum",
                "returnType", "int[]",
                "params", List.of(
                        Map.of("name", "nums", "type", "int[]"),
                        Map.of("name", "target", "type", "int")))));
    }

    @Test
    void reportsCppAsItsLanguage() {
        assertThat(new CppHarness().language()).isEqualTo(Language.CPP);
    }

    @Test
    void emitsBitsHeaderAndReusedJsonReaderAndMain() {
        String source = new CppHarness().generate(twoSum(),
                "class Solution { public: vector<int> twoSum(vector<int>& nums, int target) { return {}; } };").source();

        assertThat(source).contains("#include <bits/stdc++.h>").contains("using namespace std;");
        assertThat(source).contains("static Nv *__nt_val(void)").contains("int main(void)");
        assertThat(source).contains("__nt_src = __nt_buf;");
    }

    @Test
    void bindsArrayAsVectorAndCallsWithinSolutionWhenClassDeclared() {
        String source = new CppHarness().generate(twoSum(),
                "class Solution { public: vector<int> twoSum(vector<int>& nums, int target) { return {0, 1}; } };").source();

        assertThat(source).contains("vector<int> __nt_a_nums = __nt_vec_int(__c, \"nums\");");
        assertThat(source).contains("int __nt_a_target = __nt_int(__c, \"target\");");
        assertThat(source).contains("auto __r = Solution().twoSum(__nt_a_nums, __nt_a_target);");
        assertThat(source).contains("__nt_put_intarr(__r.data(), (int) __r.size());");
    }

    @Test
    void callsBareFunctionWhenNoSolutionClass() {
        String source = new CppHarness().generate(twoSum(),
                "vector<int> twoSum(vector<int>& nums, int target) { return {}; }").source();

        assertThat(source).contains("auto __r = twoSum(__nt_a_nums, __nt_a_target);");
    }

    @Test
    void bindsStringAndSerialisesStringReturn() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "echo", "returnType", "String",
                "params", List.of(Map.of("name", "s", "type", "String")))));

        String source = new CppHarness().generate(signature,
                "class Solution { public: string echo(string s) { return s; } };").source();

        assertThat(source).contains("string __nt_a_s = __nt_str(__c, \"s\");");
        assertThat(source).contains("auto __r = Solution().echo(__nt_a_s);");
        assertThat(source).contains("__nt_put_str(string(__r).c_str());");
    }

    @Test
    void injectsListNodeStructAndHelpersForListNodeSignature() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "reverseList", "returnType", "ListNode",
                "params", List.of(Map.of("name", "head", "type", "ListNode")))));

        String source = new CppHarness().generate(signature,
                "class Solution { public: ListNode* reverseList(ListNode* head) { return head; } };").source();

        assertThat(source).contains("struct ListNode {")
                .contains("static ListNode *__nt_to_list(Nv *o, const char *k)")
                .contains("static void __nt_put_list(const ListNode *h)");
        // Allocation must not depend on a constructor: a user-defined node (e.g. a bare aggregate
        // `struct ListNode { int val; ListNode *next; };`) has no `ListNode(int)`, so `new ListNode(x)`
        // would not compile. The factory allocates then assigns fields, which works either way.
        assertThat(source).contains("static ListNode *__nt_new_list(int val)")
                .doesNotContain("new ListNode((int)");
        assertThat(source).contains("ListNode *__nt_a_head = __nt_to_list(__c, \"head\");");
        assertThat(source).contains("auto __r = Solution().reverseList(__nt_a_head);");
        assertThat(source).contains("__nt_put_list(__r);");
    }

    @Test
    void injectsTreeNodeStructAndHelpersForTreeNodeSignature() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "maxDepth", "returnType", "int",
                "params", List.of(Map.of("name", "root", "type", "TreeNode")))));

        String source = new CppHarness().generate(signature,
                "class Solution { public: int maxDepth(TreeNode* root) { return root ? 1 : 0; } };").source();

        assertThat(source).contains("struct TreeNode {")
                .contains("static TreeNode *__nt_to_tree(Nv *o, const char *k)")
                .contains("static void __nt_put_tree(const TreeNode *r)");
        assertThat(source).contains("TreeNode *__nt_a_root = __nt_to_tree(__c, \"root\");");
        assertThat(source).contains("printf(\"%d\", (int) __r);");
    }

    @Test
    void skipsNodeStructButKeepsHelpersWhenUserDefinesTheNodeType() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "reverseList", "returnType", "ListNode",
                "params", List.of(Map.of("name", "head", "type", "ListNode")))));

        String source = new CppHarness().generate(signature,
                "struct ListNode { int val; ListNode *next; };\n"
                        + "class Solution { public: ListNode* reverseList(ListNode* head) { return head; } };").source();

        assertThat(source).doesNotContain("ListNode() : val(0), next(nullptr) {}");
        assertThat(source).contains("static ListNode *__nt_to_list(Nv *o, const char *k)");
        // The user's aggregate node has no `ListNode(int)`; the shared helper must therefore not call it.
        assertThat(source).contains("static ListNode *__nt_new_list(int val)")
                .doesNotContain("new ListNode((int)");
    }

    @Test
    void rejectsUnsupportedParamType() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "f", "returnType", "int",
                "params", List.of(Map.of("name", "x", "type", "Bitmap")))));

        assertThatThrownBy(() -> new CppHarness().generate(signature, "int f(void) { return 0; }"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bitmap");
    }

    @Test
    void rejectsUnsupportedReturnType() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "f", "returnType", "Tree",
                "params", List.of(Map.of("name", "x", "type", "int")))));

        assertThatThrownBy(() -> new CppHarness().generate(signature,
                "class Solution { public: int f(int x) { return x; } };"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tree");
    }
}
