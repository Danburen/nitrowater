package cn.nitrowater.server.service.judge.harness;

import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.service.judge.JudgeSignature;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Generates a single-file C harness that drives a LeetCode-style function over a JSON array of cases
 * read from stdin, printing one JSON result per case.
 *
 * <p>C has no standard JSON library and no single-file launcher convention, so the shared template
 * carries a hand-written JSON reader and a {@code main} that binds each case's named parameters,
 * calls the user's function and serialises the result. The user's own function(s) are pasted verbatim
 * after a {@code #line 1} directive, so gcc diagnostics keep the user's line numbers (the same trick
 * {@code LanguagePrelude} uses for C).</p>
 *
 * <p>Supported types (LeetCode C conventions): a parameter may be {@code int}, {@code long},
 * {@code String} ({@code char *}), {@code int[]} (bound as {@code int *name, int nameSize}),
 * {@code ListNode} or {@code TreeNode} (bound as {@code struct ... *name}); a return may be
 * {@code int}, {@code long}, {@code String} or {@code int[]} (the callee receives a trailing
 * {@code int *returnSize}), or {@code ListNode}/{@code TreeNode}. Anything else raises
 * {@link IllegalArgumentException}, which the judge surfaces as a {@code SE} outcome.</p>
 */
@Component
public final class CHarness extends CJsonHarness {

    private static final String HEADER = "#include <stdio.h>\n"
            + "#include <stdlib.h>\n"
            + "#include <string.h>";

    /**
     * A {@code struct ListNode}/{@code struct TreeNode} <b>definition</b> (not a mere reference, which
     * appears in a user's {@code struct ListNode *f(...)} signature); when the user already defines the
     * node type the harness must not inject a duplicate.
     */
    private static final Pattern NODE_DEF = Pattern.compile("struct\\s+(ListNode|TreeNode)\\s*\\{");

    /** {@code ListNode} typedef, injected only when the signature uses {@code ListNode} and the user has not. */
    private static final String LIST_NODE_STRUCT = """
            typedef struct ListNode {
                int val;
                struct ListNode *next;
            } ListNode;

            """;

    /** {@code ListNode} reader/writer, always emitted when the signature uses {@code ListNode}. */
    private static final String LIST_NODE_HELPERS = """
            static struct ListNode *__nt_to_list(Nv *o, const char *k) {
                Nv *v = __nt_get(o, k);
                if (!v || v->type != 4) return NULL;
                struct ListNode *head = NULL, *tail = NULL;
                for (int i = 0; i < v->len; i++) {
                    struct ListNode *n = (struct ListNode *) malloc(sizeof(struct ListNode));
                    n->val = (int) v->items[i]->num;
                    n->next = NULL;
                    if (tail) tail->next = n; else head = n;
                    tail = n;
                }
                return head;
            }

            static void __nt_put_list(struct ListNode *h) {
                putchar('[');
                int first = 1;
                for (; h; h = h->next) {
                    if (!first) putchar(',');
                    first = 0;
                    printf("%d", h->val);
                }
                putchar(']');
            }

            """;

    /** {@code TreeNode} typedef, injected only when the signature uses {@code TreeNode} and the user has not. */
    private static final String TREE_NODE_STRUCT = """
            typedef struct TreeNode {
                int val;
                struct TreeNode *left;
                struct TreeNode *right;
            } TreeNode;

            """;

    /** {@code TreeNode} reader/writer, always emitted when the signature uses {@code TreeNode}. */
    private static final String TREE_NODE_HELPERS = """
            static struct TreeNode *__nt_to_tree(Nv *o, const char *k) {
                Nv *v = __nt_get(o, k);
                if (!v || v->type != 4 || v->len == 0 || v->items[0]->type == 0) return NULL;
                struct TreeNode *root = (struct TreeNode *) malloc(sizeof(struct TreeNode));
                root->val = (int) v->items[0]->num;
                root->left = NULL;
                root->right = NULL;
                int cap = 16, head = 0, tail = 0;
                struct TreeNode **q = (struct TreeNode **) malloc(sizeof(struct TreeNode *) * cap);
                q[tail++] = root;
                int i = 1;
                while (head < tail && i < v->len) {
                    struct TreeNode *n = q[head++];
                    if (i < v->len) {
                        if (v->items[i]->type != 0) {
                            struct TreeNode *l = (struct TreeNode *) malloc(sizeof(struct TreeNode));
                            l->val = (int) v->items[i]->num; l->left = NULL; l->right = NULL;
                            n->left = l;
                            if (tail == cap) { cap *= 2; q = (struct TreeNode **) realloc(q, sizeof(struct TreeNode *) * cap); }
                            q[tail++] = l;
                        }
                        i++;
                    }
                    if (i < v->len) {
                        if (v->items[i]->type != 0) {
                            struct TreeNode *r = (struct TreeNode *) malloc(sizeof(struct TreeNode));
                            r->val = (int) v->items[i]->num; r->left = NULL; r->right = NULL;
                            n->right = r;
                            if (tail == cap) { cap *= 2; q = (struct TreeNode **) realloc(q, sizeof(struct TreeNode *) * cap); }
                            q[tail++] = r;
                        }
                        i++;
                    }
                }
                return root;
            }

            static void __nt_put_tree(struct TreeNode *r) {
                if (!r) { fputs("[]", stdout); return; }
                int cap = 16, len = 0, head = 0;
                struct TreeNode **seq = (struct TreeNode **) malloc(sizeof(struct TreeNode *) * cap);
                seq[len++] = r;
                while (head < len) {
                    struct TreeNode *n = seq[head++];
                    if (!n) continue;
                    if (len + 2 > cap) {
                        cap *= 2;
                        seq = (struct TreeNode **) realloc(seq, sizeof(struct TreeNode *) * cap);
                    }
                    seq[len++] = n->left;
                    seq[len++] = n->right;
                }
                while (len > 0 && seq[len - 1] == NULL) len--;
                putchar('[');
                for (int i = 0; i < len; i++) {
                    if (i) putchar(',');
                    if (seq[i]) printf("%d", seq[i]->val);
                    else fputs("null", stdout);
                }
                putchar(']');
            }

            """;

    @Override
    public Language language() {
        return Language.C;
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
            case "String" -> "        char *" + arg + " = __nt_str(__c, \"" + param.name() + "\");";
            case "int[]" -> "        int " + arg + "_n = 0;\n"
                    + "        int *" + arg + " = __nt_intarr(__c, \"" + param.name() + "\", &" + arg + "_n);";
            case "ListNode" -> "        struct ListNode *" + arg + " = __nt_to_list(__c, \"" + param.name() + "\");";
            case "TreeNode" -> "        struct TreeNode *" + arg + " = __nt_to_tree(__c, \"" + param.name() + "\");";
            default -> throw new IllegalArgumentException("Unsupported C parameter type: " + param.type());
        };
    }

    @Override
    protected String paramCallArg(JudgeSignature.Param param) {
        String arg = local(param);
        return switch (normalized(param.type())) {
            case "int", "long", "String", "ListNode", "TreeNode" -> arg;
            case "int[]" -> arg + ", " + arg + "_n";
            default -> throw new IllegalArgumentException("Unsupported C parameter type: " + param.type());
        };
    }

    /**
     * C has no method receiver, so the method is always a free function — C submissions never declare
     * a {@code Solution} class.
     */
    @Override
    protected String callAndPrint(String returnType, String method, String args, String userCode) {
        return switch (normalized(returnType)) {
            case "int" -> "        int __r = " + method + "(" + args + ");\n"
                    + "        printf(\"%d\", __r);";
            case "long" -> "        long __r = " + method + "(" + args + ");\n"
                    + "        printf(\"%ld\", __r);";
            case "String" -> "        char *__r = " + method + "(" + args + ");\n"
                    + "        __nt_put_str(__r);";
            case "int[]" -> "        int __rs = 0;\n"
                    + "        int *__r = " + method + "(" + (args.isEmpty() ? "" : args + ", ") + "&__rs);\n"
                    + "        __nt_put_intarr(__r, __rs);";
            case "ListNode" -> "        struct ListNode *__r = " + method + "(" + args + ");\n"
                    + "        __nt_put_list(__r);";
            case "TreeNode" -> "        struct TreeNode *__r = " + method + "(" + args + ");\n"
                    + "        __nt_put_tree(__r);";
            default -> throw new IllegalArgumentException("Unsupported C return type: " + returnType);
        };
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
        if (needsType(signature, "ListNode")) {
            sb.append(LIST_NODE_HELPERS);
        }
        if (needsType(signature, "TreeNode")) {
            sb.append(TREE_NODE_HELPERS);
        }
        return sb.toString();
    }
}
