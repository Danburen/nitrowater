package cn.nitrowater.server.service.judge;

import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.service.judge.harness.CHarness;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks the shape of the generated C harness: a self-contained JSON reader, the user's function pasted
 * verbatim after a {@code #line} directive, and a {@code main} that binds each case, calls the method
 * and prints one JSON result per case.
 */
class CHarnessTest {

    private static JudgeSignature twoSum() {
        return JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "twoSum",
                "returnType", "int[]",
                "params", List.of(
                        Map.of("name", "nums", "type", "int[]"),
                        Map.of("name", "target", "type", "int")))));
    }

    @Test
    void reportsCAsItsLanguage() {
        assertThat(new CHarness().language()).isEqualTo(Language.C);
    }

    @Test
    void emitsSelfContainedJsonReaderAndMain() {
        String source = new CHarness().generate(twoSum(), "int *twoSum(int* n, int ns, int t, int* rs) { return n; }").source();

        assertThat(source).contains("#include <stdio.h>");
        assertThat(source).contains("static Nv *__nt_val(void)");
        assertThat(source).contains("int main(void)");
        assertThat(source).contains("__nt_src = __nt_buf;");
    }

    @Test
    void bindsArrayAndScalarParamsAndCallsWithSizeCompanion() {
        String source = new CHarness().generate(twoSum(), "int *twoSum(int* n, int ns, int t, int* rs) { return n; }").source();

        assertThat(source).contains("int __nt_a_nums_n = 0;");
        assertThat(source).contains("int *__nt_a_nums = __nt_intarr(__c, \"nums\", &__nt_a_nums_n);");
        assertThat(source).contains("int __nt_a_target = __nt_int(__c, \"target\");");
        assertThat(source).contains("twoSum(__nt_a_nums, __nt_a_nums_n, __nt_a_target, &__rs)");
        assertThat(source).contains("__nt_put_intarr(__r, __rs);");
    }

    @Test
    void placesLineDirectiveBeforeUserCodeAndMainAfter() {
        String user = "int *twoSum(int* nums, int numsSize, int target, int* returnSize) { return nums; }\n";

        String source = new CHarness().generate(twoSum(), user).source();

        assertThat(source.indexOf("#line 1")).isLessThan(source.indexOf("int *twoSum(int* nums"));
        assertThat(source.indexOf("int *twoSum(int* nums")).isLessThan(source.indexOf("int main(void)"));
    }

    @Test
    void bindsStringParamAndSerialisesStringReturn() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "echo",
                "returnType", "String",
                "params", List.of(Map.of("name", "s", "type", "String")))));

        String source = new CHarness().generate(signature, "char *echo(char *s) { return s; }").source();

        assertThat(source).contains("char *__nt_a_s = __nt_str(__c, \"s\");");
        assertThat(source).contains("char *__r = echo(__nt_a_s);");
        assertThat(source).contains("__nt_put_str(__r);");
    }

    @Test
    void bindsLongParamAndReturn() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "twice",
                "returnType", "long",
                "params", List.of(Map.of("name", "x", "type", "long")))));

        String source = new CHarness().generate(signature, "long twice(long x) { return x * 2; }").source();

        assertThat(source).contains("long __nt_a_x = __nt_long(__c, \"x\");");
        assertThat(source).contains("printf(\"%ld\", __r);");
    }

    @Test
    void handlesNoParamSignature() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "solve", "returnType", "int")));

        String source = new CHarness().generate(signature, "int solve(void) { return 1; }").source();

        assertThat(source).contains("int __r = solve();");
    }

    @Test
    void keepsUserCodeVerbatim() {
        String user = "int f(int x) {\n    /* custom */\n    return x + 41;\n}\n";

        String source = new CHarness().generate(
                JudgeSignature.parse(Map.of("signature", Map.of(
                        "method", "f", "returnType", "int",
                        "params", List.of(Map.of("name", "x", "type", "int"))))),
                user).source();

        assertThat(source).contains(user);
    }

    @Test
    void keepsMainLocalsPrefixedSoTheyCannotShadowTheMethod() {
        // A method named `len` used to be shadowed by main's own `size_t ... len` local, producing
        // gcc's "called object 'len' is not a function or function pointer". All main locals must be
        // __nt_-prefixed so no user method name can ever collide with them.
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "len",
                "returnType", "int",
                "params", List.of(Map.of("name", "len", "type", "int[]")))));

        String source = new CHarness().generate(signature, "int len(int* len, int size) { return size; }").source();

        assertThat(source).contains("size_t __nt_cap = 65536, __nt_len = 0;");
        assertThat(source).contains("char *__nt_buf = (char *) malloc(__nt_cap);");
        assertThat(source).contains("int __r = len(__nt_a_len, __nt_a_len_n);");
    }

    @Test
    void rejectsUnsupportedParamType() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "f",
                "returnType", "int",
                "params", List.of(Map.of("name", "x", "type", "Bitmap")))));

        assertThatThrownBy(() -> new CHarness().generate(signature, "int f(void) { return 0; }"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bitmap");
    }

    @Test
    void rejectsUnsupportedReturnType() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "f",
                "returnType", "Tree",
                "params", List.of(Map.of("name", "x", "type", "int")))));

        assertThatThrownBy(() -> new CHarness().generate(signature, "int f(int x) { return x; }"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tree");
    }

    @Test
    void injectsListNodeStructAndHelpersForListNodeSignature() {
        String source = new CHarness().generate(reverseList(),
                "struct ListNode* reverseList(struct ListNode* head) { return head; }").source();

        assertThat(source).contains("typedef struct ListNode {")
                .contains("static struct ListNode *__nt_to_list(Nv *o, const char *k)")
                .contains("static void __nt_put_list(struct ListNode *h)");
        assertThat(source).contains("struct ListNode *__nt_a_head = __nt_to_list(__c, \"head\");");
        assertThat(source).contains("struct ListNode *__r = reverseList(__nt_a_head);");
        assertThat(source).contains("__nt_put_list(__r);");
    }

    @Test
    void injectsTreeNodeStructAndHelpersForTreeNodeSignature() {
        JudgeSignature signature = JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "maxDepth", "returnType", "int",
                "params", List.of(Map.of("name", "root", "type", "TreeNode")))));

        String source = new CHarness().generate(signature,
                "int maxDepth(struct TreeNode* root) { return root ? 1 : 0; }").source();

        assertThat(source).contains("typedef struct TreeNode {")
                .contains("static struct TreeNode *__nt_to_tree(Nv *o, const char *k)")
                .contains("static void __nt_put_tree(struct TreeNode *r)");
        assertThat(source).contains("struct TreeNode *__nt_a_root = __nt_to_tree(__c, \"root\");");
        assertThat(source).contains("int __r = maxDepth(__nt_a_root);");
    }

    @Test
    void skipsNodeStructButKeepsHelpersWhenUserDefinesTheNodeType() {
        // The struct definition is the user's, but the harness's reader/writer still compile against
        // it, so they must remain or the generated program would reference undefined functions.
        String source = new CHarness().generate(reverseList(),
                "struct ListNode { int val; struct ListNode *next; };\n"
                        + "struct ListNode* reverseList(struct ListNode* head) { return head; }").source();

        assertThat(source).doesNotContain("typedef struct ListNode {");
        assertThat(source).contains("static struct ListNode *__nt_to_list(Nv *o, const char *k)");
        assertThat(source).contains("static void __nt_put_list(struct ListNode *h)");
    }

    private static JudgeSignature reverseList() {
        return JudgeSignature.parse(Map.of("signature", Map.of(
                "method", "reverseList", "returnType", "ListNode",
                "params", List.of(Map.of("name", "head", "type", "ListNode")))));
    }
}
