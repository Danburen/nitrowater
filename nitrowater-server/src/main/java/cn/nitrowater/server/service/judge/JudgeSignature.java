package cn.nitrowater.server.service.judge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parsed LeetCode-style method signature, stored as {@code judgeConfig.signature}:
 *
 * <pre>{@code
 * { "method": "twoSum", "returnType": "int[]",
 *   "params": [ { "name": "nums", "type": "int[]" }, { "name": "target", "type": "int" } ] }
 * }</pre>
 *
 * <p>A {@code null} result means the problem is <b>not</b> configured for function mode, so the
 * judge falls back to the ACM (stdin/stdout) path.</p>
 */
public record JudgeSignature(String method, String returnType, List<Param> params) {

    /** One method parameter. */
    public record Param(String name, String type) {
    }

    /**
     * @param judgeConfig the problem's parsed {@code judge_config} (may be {@code null})
     * @return the parsed signature, or {@code null} when absent/invalid
     */
    public static JudgeSignature parse(Map<String, Object> judgeConfig) {
        if (judgeConfig == null) {
            return null;
        }
        if (!(judgeConfig.get("signature") instanceof Map<?, ?> raw)) {
            return null;
        }
        if (!(raw.get("method") instanceof String method) || method.isBlank()) {
            return null;
        }
        List<Param> params = new ArrayList<>();
        if (raw.get("params") instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> param && param.get("type") instanceof String type
                        && !type.isBlank()) {
                    Object name = param.get("name");
                    params.add(new Param(name == null ? "arg" + params.size() : String.valueOf(name),
                            type.trim()));
                }
            }
        }
        String returnType = raw.get("returnType") instanceof String value && !value.isBlank()
                ? value.trim()
                : "void";
        return new JudgeSignature(method.trim(), returnType, params);
    }
}
