package cn.nitrowater.server.service.judge.harness;

import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.service.judge.JudgeSignature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates a single-file Java harness that drives a LeetCode-style {@code Solution} class over a
 * JSON array of cases read from stdin, printing one JSON result per case.
 *
 * <p>Piston's Java runtime runs {@code java Main.java} (the JDK single-file source launcher), which
 * compiles <b>only</b> that one file and executes its <b>first</b> top-level class. The generated
 * unit therefore must be a single {@code Main.java} with {@code public class Main} first. The user's
 * imports are hoisted above {@code Main} (imports are only legal at the top of a file), and a leading
 * {@code public} is stripped from the user's own top-level types so only {@code Main} stays public.</p>
 */
@Component
public final class JavaHarness implements FunctionHarness {

    /** Class name the user's solution must define (LeetCode convention). */
    public static final String SOLUTION_CLASS = "Solution";

    private static final String TEMPLATE = loadTemplate();

    private static final Pattern IMPORT_LINE =
            Pattern.compile("(?m)^[ \\t]*import\\s+(?:static\\s+)?[\\w.*]+\\s*;[ \\t]*$");

    private static final Pattern PUBLIC_TYPE =
            Pattern.compile("\\bpublic(\\s+(?:final\\s+|abstract\\s+)?(?:class|interface|enum|record)\\b)");

    /** A node type the user already defines, so the harness does not inject a duplicate top-level class. */
    private static final Pattern TYPE_DEF =
            Pattern.compile("\\b(?:class|interface|enum|record)\\s+(ListNode|TreeNode)\\b");

    private static final String LIST_NODE_CLASS = """
            class ListNode {
                int val;
                ListNode next;
                ListNode() {}
                ListNode(int val) { this.val = val; }
                ListNode(int val, ListNode next) { this.val = val; this.next = next; }
            }
            """;

    private static final String TREE_NODE_CLASS = """
            class TreeNode {
                int val;
                TreeNode left;
                TreeNode right;
                TreeNode() {}
                TreeNode(int val) { this.val = val; }
                TreeNode(int val, TreeNode left, TreeNode right) { this.val = val; this.left = left; this.right = right; }
            }
            """;

    /**
     * Static helper methods injected into {@code H} that deserialise/serialise {@code ListNode}. Emitted
     * only when the signature uses {@code ListNode}, kept in lockstep with {@link #nodeClasses} so
     * {@code H} never references a node type whose class was not injected (Java resolves references at
     * compile time, so an unused helper would still be a hard compile error).
     */
    private static final String LIST_NODE_HELPERS = """
        static ListNode toListNode(Object o) {
            if (o == null) return null;
            List<Object> x = (List<Object>) o;
            ListNode dummy = new ListNode(0), cur = dummy;
            for (Object e : x) { cur.next = new ListNode(((Number) e).intValue()); cur = cur.next; }
            return dummy.next;
        }
        static String listNodeJson(ListNode h) {
            StringBuilder b = new StringBuilder("[");
            boolean f = true;
            while (h != null) { if (!f) b.append(','); f = false; b.append(h.val); h = h.next; }
            return b.append(']').toString();
        }
""";

    /**
     * Static helper methods injected into {@code H} that deserialise/serialise {@code TreeNode}. Emitted
     * only when the signature uses {@code TreeNode}; see {@link #LIST_NODE_HELPERS} for the rationale.
     */
    private static final String TREE_NODE_HELPERS = """
        static TreeNode toTreeNode(Object o) {
            if (o == null) return null;
            List<Object> x = (List<Object>) o;
            if (x.isEmpty() || x.get(0) == null) return null;
            TreeNode root = new TreeNode(((Number) x.get(0)).intValue());
            Deque<TreeNode> q = new ArrayDeque<>();
            q.add(root);
            int i = 1;
            while (!q.isEmpty() && i < x.size()) {
                TreeNode n = q.poll();
                if (i < x.size()) { Object v = x.get(i++); if (v != null) { n.left = new TreeNode(((Number) v).intValue()); q.add(n.left); } }
                if (i < x.size()) { Object v = x.get(i++); if (v != null) { n.right = new TreeNode(((Number) v).intValue()); q.add(n.right); } }
            }
            return root;
        }
        static String treeNodeJson(TreeNode r) {
            if (r == null) return "[]";
            List<String> out = new ArrayList<>();
            Deque<TreeNode> q = new ArrayDeque<>();
            q.add(r);
            while (!q.isEmpty()) {
                TreeNode n = q.poll();
                if (n == null) { out.add("null"); continue; }
                out.add(String.valueOf(n.val));
                q.add(n.left);
                q.add(n.right);
            }
            int i = out.size();
            while (i > 0 && out.get(i - 1).equals("null")) i--;
            StringBuilder b = new StringBuilder("[");
            for (int k = 0; k < i; k++) { if (k > 0) b.append(','); b.append(out.get(k)); }
            return b.append(']').toString();
        }
""";

    @Override
    public Language language() {
        return Language.JAVA;
    }

    @Override
    public GeneratedHarness generate(JudgeSignature signature, String userCode) {
        return generateWithOffset(signature, userCode);
    }

    /**
     * @param signature parsed method signature
     * @param userCode  user-submitted {@code Solution} source
     * @return the generated harness along with the line offset of the user's own code
     * @throws IllegalArgumentException when a parameter type is not supported by the harness
     */
    public static GeneratedHarness generateWithOffset(JudgeSignature signature, String userCode) {
        String code = userCode == null ? "" : userCode;
        String imports = hoistImports(code);
        String body = stripPublicTypes(removeImports(code));

        StringBuilder assignments = new StringBuilder();
        StringBuilder args = new StringBuilder();
        List<JudgeSignature.Param> params = signature.params();
        for (int i = 0; i < params.size(); i++) {
            JudgeSignature.Param param = params.get(i);
            assignments.append("            ").append(param.type()).append(' ').append(param.name())
                    .append(" = H.").append(converter(param.type()))
                    .append("(__m.get(\"").append(param.name()).append("\"));\n");
            if (i > 0) {
                args.append(", ");
            }
            args.append(param.name());
        }

        String prefix = TEMPLATE
                .replace("{{USER_IMPORTS}}", imports)
                .replace("{{ASSIGNMENTS}}", assignments.toString().stripTrailing())
                .replace("{{METHOD}}", signature.method())
                .replace("{{ARGS}}", args.toString())
                .replace("{{RETURN_SERIALIZE}}", returnSerializer(signature.returnType()))
                .replace("{{NODE_HELPERS}}", nodeHelpers(signature))
                + nodeClasses(signature, code)
                + "\n";
        return new GeneratedHarness(prefix + body + "\n", HarnessSupport.countNewlines(prefix));
    }

    /** Expression that serialises the method's return value, unwrapping {@code ListNode}/{@code TreeNode}. */
    private static String returnSerializer(String returnType) {
        return switch (returnType.replace(" ", "")) {
            case "ListNode" -> "H.listNodeJson((ListNode) __r)";
            case "TreeNode" -> "H.treeNodeJson((TreeNode) __r)";
            default -> "H.json(__r)";
        };
    }

    /**
     * Top-level {@code ListNode}/{@code TreeNode} definitions to inject after {@code Main}, but only
     * for types the signature actually uses and only when the user's own code does not already define
     * them (a duplicate top-level class would be a compile error).
     */
    private static String nodeClasses(JudgeSignature signature, String userCode) {
        boolean needsList = HarnessSupport.usesType(signature, "ListNode");
        boolean needsTree = HarnessSupport.usesType(signature, "TreeNode");
        if (!needsList && !needsTree) {
            return "";
        }
        Set<String> defined = new HashSet<>();
        Matcher matcher = TYPE_DEF.matcher(userCode == null ? "" : userCode);
        while (matcher.find()) {
            defined.add(matcher.group(1));
        }
        StringBuilder sb = new StringBuilder();
        if (needsList && !defined.contains("ListNode")) {
            sb.append('\n').append(LIST_NODE_CLASS);
        }
        if (needsTree && !defined.contains("TreeNode")) {
            sb.append('\n').append(TREE_NODE_CLASS);
        }
        return sb.toString();
    }

    /**
     * The {@code ListNode}/{@code TreeNode} helper methods to place inside {@code H}, for the types the
     * signature uses. Kept in lockstep with {@link #nodeClasses}: a helper is emitted only when its class
     * is also injected, otherwise {@code H} would fail to compile.
     */
    private static String nodeHelpers(JudgeSignature signature) {
        StringBuilder sb = new StringBuilder();
        if (HarnessSupport.usesType(signature, "ListNode")) {
            sb.append(LIST_NODE_HELPERS);
        }
        if (HarnessSupport.usesType(signature, "TreeNode")) {
            sb.append(TREE_NODE_HELPERS);
        }
        return sb.toString();
    }

    private static String converter(String type) {
        return switch (type.replace(" ", "")) {
            case "int" -> "i";
            case "long" -> "l";
            case "double" -> "d";
            case "boolean" -> "b";
            case "char" -> "ch";
            case "String" -> "str2";
            case "int[]" -> "intArr";
            case "long[]" -> "longArr";
            case "double[]" -> "doubleArr";
            case "boolean[]" -> "boolArr";
            case "char[]" -> "charArr";
            case "String[]" -> "strArr";
            case "int[][]" -> "int2D";
            case "List<Integer>" -> "intList";
            case "List<Long>" -> "longList";
            case "List<String>" -> "strList";
            case "List<List<Integer>>" -> "int2DList";
            case "ListNode" -> "toListNode";
            case "TreeNode" -> "toTreeNode";
            default -> throw new IllegalArgumentException("Unsupported parameter type: " + type);
        };
    }

    private static String hoistImports(String code) {
        Matcher matcher = IMPORT_LINE.matcher(code);
        StringBuilder imports = new StringBuilder();
        while (matcher.find()) {
            if (!imports.isEmpty()) {
                imports.append('\n');
            }
            imports.append(matcher.group().strip());
        }
        return imports.toString();
    }

    private static String removeImports(String code) {
        return IMPORT_LINE.matcher(code).replaceAll("");
    }

    private static String stripPublicTypes(String code) {
        return PUBLIC_TYPE.matcher(code).replaceAll("$1");
    }

    private static String loadTemplate() {
        try (InputStream in = JavaHarness.class.getResourceAsStream("/harness/java/java-main.tpl")) {
            if (in == null) {
                throw new IllegalStateException("Missing harness template /harness/java/java-main.tpl");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load harness template", ex);
        }
    }
}
