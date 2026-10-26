package cn.nitrowater.server.service.judge.harness;

import cn.nitrowater.server.infra.enums.Language;
import cn.nitrowater.server.service.judge.JudgeSignature;
import cn.nitrowater.server.service.shared.LanguagePrelude;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates a Python harness that drives a LeetCode-style {@code Solution} class over a JSON array of
 * cases read from stdin, printing one JSON result per case.
 *
 * <p>Unlike Java, CPython imposes no single-file constraint: the prelude supplies the common imports,
 * the user's own code follows verbatim (Python allows imports anywhere at module level, so nothing is
 * hoisted), and a small driver is appended to call the method for every case and serialise each
 * return value. Diagnostics stay faithful to the user's line numbers because the only prefix is the
 * prelude, whose line count is reported as the harness offset.</p>
 */
@Component
public final class PythonHarness implements FunctionHarness {

    private static final String PRELUDE = LanguagePrelude.header("python");

    /** A node type the user already defines, so the harness does not inject a duplicate definition. */
    private static final Pattern TYPE_DEF =
            Pattern.compile("(?m)^[ \\t]*class[ \\t]+(ListNode|TreeNode)\\b");

    private static final String LIST_NODE_CLASS = "class ListNode:\n"
            + "    def __init__(self, val=0, next=None):\n"
            + "        self.val = val\n"
            + "        self.next = next\n";

    private static final String TREE_NODE_CLASS = "class TreeNode:\n"
            + "    def __init__(self, val=0, left=None, right=None):\n"
            + "        self.val = val\n"
            + "        self.left = left\n"
            + "        self.right = right\n";

    @Override
    public Language language() {
        return Language.PYTHON;
    }

    @Override
    public GeneratedHarness generate(JudgeSignature signature, String userCode) {
        return generateWithOffset(signature, userCode);
    }

    /**
     * @param signature parsed method signature
     * @param userCode  user-submitted {@code Solution} source
     * @return the generated harness along with the line offset of the user's own code
     */
    public static GeneratedHarness generateWithOffset(JudgeSignature signature, String userCode) {
        String code = userCode == null ? "" : userCode;
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < signature.params().size(); i++) {
            if (i > 0) {
                args.append(", ");
            }
            args.append(argument(signature.params().get(i)));
        }
        String prefix = PRELUDE + nodeClasses(signature, code);
        StringBuilder source = new StringBuilder(prefix);
        source.append(code);
        if (!code.isEmpty() && !code.endsWith("\n")) {
            source.append('\n');
        }
        String serializer = serializerFunction(signature.returnType());
        source.append(driver(serializer + "(_nitro_sol." + signature.method() + "(" + args + "))"));
        return new GeneratedHarness(source.toString(), HarnessSupport.countNewlines(prefix));
    }

    /** Serialiser function name for the return value, chosen by the declared return type. */
    private static String serializerFunction(String returnType) {
        return switch (returnType.replace(" ", "")) {
            case "ListNode" -> "_nitro_ser_list";
            case "TreeNode" -> "_nitro_ser_tree";
            default -> "_nitro_dump";
        };
    }

    /** Binds one case parameter, converting JSON arrays into node objects for complex types. */
    private static String argument(JudgeSignature.Param param) {
        String get = "_nitro_map.get(" + literal(param.name()) + ")";
        return switch (param.type().replace(" ", "")) {
            case "ListNode" -> "_nitro_to_list(" + get + ")";
            case "TreeNode" -> "_nitro_to_tree(" + get + ")";
            default -> get;
        };
    }

    /** Definitions of the node classes the signature uses and the user's own code does not define. */
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
            sb.append("\n\n").append(LIST_NODE_CLASS);
        }
        if (needsTree && !defined.contains("TreeNode")) {
            sb.append("\n\n").append(TREE_NODE_CLASS);
        }
        return sb.toString();
    }

    private static String driver(String call) {
        return """
                import json as _nitro_json


                def _nitro_to_list(_v):
                    if _v is None:
                        return None
                    _d = ListNode(0)
                    _cur = _d
                    for _x in _v:
                        _cur.next = ListNode(_x)
                        _cur = _cur.next
                    return _d.next


                def _nitro_to_tree(_v):
                    if not _v:
                        return None
                    _it = iter(_v)
                    _root = TreeNode(next(_it))
                    _q = collections.deque([_root])
                    while _q:
                        _n = _q.popleft()
                        try:
                            _l = next(_it)
                        except StopIteration:
                            break
                        if _l is not None:
                            _n.left = TreeNode(_l)
                            _q.append(_n.left)
                        try:
                            _r = next(_it)
                        except StopIteration:
                            break
                        if _r is not None:
                            _n.right = TreeNode(_r)
                            _q.append(_n.right)
                    return _root


                def _nitro_ser_list(_v):
                    if _v is None:
                        return "[]"
                    _out = []
                    _n = _v
                    while _n is not None:
                        _out.append(_n.val)
                        _n = _n.next
                    return _nitro_json.dumps(_out, separators=(",", ":"))


                def _nitro_ser_tree(_v):
                    if _v is None:
                        return "[]"
                    _out = []
                    _q = collections.deque([_v])
                    while _q:
                        _n = _q.popleft()
                        if _n is None:
                            _out.append(None)
                            continue
                        _out.append(_n.val)
                        _q.append(_n.left)
                        _q.append(_n.right)
                    while _out and _out[-1] is None:
                        _out.pop()
                    return _nitro_json.dumps(_out, separators=(",", ":"))


                def _nitro_dump(_v):
                    return _nitro_json.dumps(_v, default=str, separators=(",", ":"))


                def _nitro_run():
                    _nitro_raw = sys.stdin.read()
                    _nitro_cases = _nitro_json.loads(_nitro_raw) if _nitro_raw.strip() else []
                    _nitro_sol = Solution()
                    _nitro_out = []
                    for _nitro_case in _nitro_cases:
                        _nitro_map = _nitro_case if isinstance(_nitro_case, dict) else {}
                        _nitro_out.append(%s)
                    sys.stdout.write("\\n".join(_nitro_out) + ("\\n" if _nitro_out else ""))


                _nitro_run()
                """.formatted(call);
    }

    private static String literal(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

}
