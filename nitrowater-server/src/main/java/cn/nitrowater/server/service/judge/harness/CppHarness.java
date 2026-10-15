package cn.nitrowater.server.service.judge.harness;

import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.service.judge.JudgeSignature;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Generates a single-file C++ harness that drives a LeetCode-style function over a JSON array of
 * cases read from stdin, printing one JSON result per case.
 *
 * <p>Reuses the C-family JSON reader in {@code c-main.tpl} (valid C++ too) headed by
 * {@code <bits/stdc++.h>} / {@code using namespace std;}, so submissions may use the STL freely. The
 * user's function(s) are pasted verbatim after a {@code #line 1} directive, keeping g++ diagnostics on
 * the user's own line numbers.</p>
 *
 * <p>Supported types (the shared judge vocabulary, mapped to C++ STL/node types): a parameter may be
 * {@code int}, {@code long}, {@code String} ({@code std::string}), {@code int[]}
 * ({@code std::vector<int>}), {@code ListNode} or {@code TreeNode} (pointers); a return may be any of
 * the same. Anything else raises {@link IllegalArgumentException}, which the judge surfaces as
 * {@code SE}.</p>
 */
@Component
public final class CppHarness extends CJsonHarness {

    private static final String HEADER = "#include <bits/stdc++.h>\nusing namespace std;";

    /** A {@code struct/class ListNode}/{@code TreeNode} definition in the user's own code. */
    private static final Pattern NODE_DEF =
            Pattern.compile("(?:struct|class)\\s+(ListNode|TreeNode)\\s*\\{");

    /** Detects the LeetCode C++ convention of declaring the method inside a {@code Solution} class. */
    private static final Pattern SOLUTION_CLASS_DEF = Pattern.compile("\\bclass\\s+Solution\\b");

    /** {@code vector<int>} reader, needed only when a parameter is {@code int[]}. */
    private static final String VEC_INT = """
            static vector<int> __nt_vec_int(Nv *o, const char *k) {
                int n = 0;
                int *p = __nt_intarr(o, k, &n);
                return vector<int>(p, p + n);
            }

            """;

    /** {@code ListNode} definition, injected only when the signature uses {@code ListNode} and the user has not. */
    private static final String LIST_NODE_STRUCT = """
            struct ListNode {
                int val;
                ListNode *next;
                ListNode() : val(0), next(nullptr) {}
                ListNode(int x) : val(x), next(nullptr) {}
            };

            """;

    /** {@code ListNode} reader/writer, always emitted when the signature uses {@code ListNode}. */
    private static final String LIST_NODE_HELPERS = """
            static ListNode *__nt_new_list(int val) {
                ListNode *n = new ListNode();
                n->val = val;
                n->next = nullptr;
                return n;
            }

            static ListNode *__nt_to_list(Nv *o, const char *k) {
                Nv *v = __nt_get(o, k);
                if (!v || v->type != 4) return nullptr;
                ListNode dummy{};
                ListNode *cur = &dummy;
                for (int i = 0; i < v->len; i++) {
                    cur->next = __nt_new_list((int) v->items[i]->num);
                    cur = cur->next;
                }
                return dummy.next;
            }

            static void __nt_put_list(const ListNode *h) {
                putchar('[');
                bool first = true;
                for (const ListNode *p = h; p; p = p->next) {
                    if (!first) putchar(',');
                    first = false;
                    printf("%d", p->val);
                }
                putchar(']');
            }

            """;

    /** {@code TreeNode} definition, injected only when the signature uses {@code TreeNode} and the user has not. */
    private static final String TREE_NODE_STRUCT = """
            struct TreeNode {
                int val;
                TreeNode *left;
                TreeNode *right;
                TreeNode() : val(0), left(nullptr), right(nullptr) {}
                TreeNode(int x) : val(x), left(nullptr), right(nullptr) {}
            };

            """;

    /** {@code TreeNode} reader/writer, always emitted when the signature uses {@code TreeNode}. */
    private static final String TREE_NODE_HELPERS = """
            static TreeNode *__nt_new_tree(int val) {
                TreeNode *n = new TreeNode();
                n->val = val;
                n->left = nullptr;
                n->right = nullptr;
                return n;
            }

            static TreeNode *__nt_to_tree(Nv *o, const char *k) {
                Nv *v = __nt_get(o, k);
                if (!v || v->type != 4 || v->len == 0 || v->items[0]->type == 0) return nullptr;
                TreeNode *root = __nt_new_tree((int) v->items[0]->num);
                deque<TreeNode *> q;
                q.push_back(root);
                int i = 1;
                while (!q.empty() && i < v->len) {
                    TreeNode *n = q.front();
                    q.pop_front();
                    if (i < v->len) {
                        if (v->items[i]->type != 0) { n->left = __nt_new_tree((int) v->items[i]->num); q.push_back(n->left); }
                        i++;
                    }
                    if (i < v->len) {
                        if (v->items[i]->type != 0) { n->right = __nt_new_tree((int) v->items[i]->num); q.push_back(n->right); }
                        i++;
                    }
                }
                return root;
            }

            static void __nt_put_tree(const TreeNode *r) {
                if (!r) { fputs("[]", stdout); return; }
                vector<string> out;
                deque<const TreeNode *> q;
                q.push_back(r);
                while (!q.empty()) {
                    const TreeNode *n = q.front();
                    q.pop_front();
                    if (!n) { out.push_back("null"); continue; }
                    out.push_back(to_string(n->val));
                    q.push_back(n->left);
                    q.push_back(n->right);
                }
                while (!out.empty() && out.back() == "null") out.pop_back();
                putchar('[');
                for (size_t i = 0; i < out.size(); i++) {
                    if (i) putchar(',');
                    fputs(out[i].c_str(), stdout);
                }
                putchar(']');
            }

            """;

    @Override
    public Language language() {
        return Language.CPP;
    }

    @Override
    protected String languageHeader() {
        return HEADER;
    }

    @Override
    protected String paramDecl(JudgeSignature.Param param) {
        String arg = local(param);
        return switch (normalized(param.type())) {
            case "int" -> "        int " + arg + " = __nt_int(__c, \"" + param.name() + "\");";
            case "long" -> "        long " + arg + " = __nt_long(__c, \"" + param.name() + "\");";
            case "String" -> "        string " + arg + " = __nt_str(__c, \"" + param.name() + "\");";
            case "int[]" -> "        vector<int> " + arg + " = __nt_vec_int(__c, \"" + param.name() + "\");";
            case "ListNode" -> "        ListNode *" + arg + " = __nt_to_list(__c, \"" + param.name() + "\");";
            case "TreeNode" -> "        TreeNode *" + arg + " = __nt_to_tree(__c, \"" + param.name() + "\");";
            default -> throw new IllegalArgumentException("Unsupported C++ parameter type: " + param.type());
        };
    }

    @Override
    protected String paramCallArg(JudgeSignature.Param param) {
        String type = normalized(param.type());
        if (!type.equals("int") && !type.equals("long") && !type.equals("String")
                && !type.equals("int[]") && !type.equals("ListNode") && !type.equals("TreeNode")) {
            throw new IllegalArgumentException("Unsupported C++ parameter type: " + param.type());
        }
        return local(param);
    }

    /**
     * LeetCode-style C++ declares the method inside a {@code Solution} class, so the call is
     * {@code Solution().method(...)}; a submission that instead declares a free function (the common
     * shape for problems with a plain C-style contract) is still supported by calling it bare.
     *
     * @param returnType declared return type of the method
     * @param method     method name to invoke
     * @param args       argument expressions
     * @param userCode   used to detect whether the method lives inside a {@code Solution} class
     */
    @Override
    protected String callAndPrint(String returnType, String method, String args, String userCode) {
        String receiver = definesSolutionClass(userCode) ? "Solution()." : "";
        String call = "        auto __r = " + receiver + method + "(" + args + ");\n";
        return call + switch (normalized(returnType)) {
            case "int" -> "        printf(\"%d\", (int) __r);";
            case "long" -> "        printf(\"%lld\", (long long) __r);";
            case "String" -> "        __nt_put_str(string(__r).c_str());";
            case "int[]" -> "        __nt_put_intarr(__r.data(), (int) __r.size());";
            case "ListNode" -> "        __nt_put_list(__r);";
            case "TreeNode" -> "        __nt_put_tree(__r);";
            default -> throw new IllegalArgumentException("Unsupported C++ return type: " + returnType);
        };
    }

    /**
     * @param userCode the user's source
     * @return whether the submission declares a {@code Solution} class (LeetCode C++ convention)
     */
    private static boolean definesSolutionClass(String userCode) {
        return SOLUTION_CLASS_DEF.matcher(userCode == null ? "" : userCode).find();
    }

    @Override
    protected String typeDefinitions(JudgeSignature signature, String userCode) {
        StringBuilder sb = new StringBuilder();
        if (needsType(signature, "ListNode") && !userDefinesNode(userCode, NODE_DEF, "ListNode")) {
            sb.append(LIST_NODE_STRUCT);
        }
        if (needsType(signature, "TreeNode") && !userDefinesNode(userCode, NODE_DEF, "TreeNode")) {
            sb.append(TREE_NODE_STRUCT);
        }
        return sb.toString();
    }

    @Override
    protected String helpers(JudgeSignature signature, String userCode) {
        StringBuilder sb = new StringBuilder();
        if (usesParamType(signature, "int[]")) {
            sb.append(VEC_INT);
        }
        if (needsType(signature, "ListNode")) {
            sb.append(LIST_NODE_HELPERS);
        }
        if (needsType(signature, "TreeNode")) {
            sb.append(TREE_NODE_HELPERS);
        }
        return sb.toString();
    }

    /** @return whether any parameter has the given (whitespace-normalised) type */
    private static boolean usesParamType(JudgeSignature signature, String type) {
        for (JudgeSignature.Param param : signature.params()) {
            if (type.equals(normalized(param.type()))) {
                return true;
            }
        }
        return false;
    }
}
