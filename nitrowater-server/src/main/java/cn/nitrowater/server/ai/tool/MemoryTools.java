package cn.nitrowater.server.ai.tool;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.ai.agent.AgentTrace;
import cn.nitrowater.server.ai.memory.AiMemoryService;
import cn.nitrowater.server.infra.enums.MemoryKind;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Exposes explicit long-term memory saving to the agent.
 *
 * <p>The uid and conversation uuid always come from {@link AgentTrace}; they are deliberately not
 * tool parameters, so the model cannot write another user's memory or forge a source.</p>
 */
@Component
public class MemoryTools {

    private final AiMemoryService memories;

    public MemoryTools(AiMemoryService memories) {
        this.memories = memories;
    }

    /**
     * Saves one stable fact, preference, decision or project context.
     *
     * @param kind {@code FACT} / {@code PREFERENCE} / {@code DECISION} / {@code PROJECT}
     * @param content one-sentence memory
     * @param context Spring AI tool context (carries the {@link AgentTrace})
     * @return JSON save outcome
     */
    @Tool(name = "saveMemory", description = """
            保存一条跨会话的长期记忆。只有当用户明确要求“记住 / 保存 / 以后都按这个来”某条稳定事实、偏好、决定或项目背景时才调用；不要根据普通聊天自动保存。
            内容必须是一句话，建议不超过 200 字。绝不保存密码、密钥、token、手机号、身份证等敏感信息。
            记忆类型：FACT=稳定事实，PREFERENCE=偏好，DECISION=已定方案，PROJECT=项目背景。
            """)
    public String saveMemory(
            @ToolParam(description = "记忆类型：FACT / PREFERENCE / DECISION / PROJECT") String kind,
            @ToolParam(description = "要记住的一句话，建议不超过 200 字") String content,
            ToolContext context) {
        AgentTrace trace = trace(context);
        String uidValue = trace == null ? null : trace.userId();
        if (uidValue == null || uidValue.isBlank()) {
            return rejected(trace, content, "需要登录后才能保存记忆。");
        }
        long uid;
        try {
            uid = Long.parseLong(uidValue);
        } catch (NumberFormatException ex) {
            return rejected(trace, content, "无法识别当前用户，未保存。");
        }

        MemoryKind memoryKind = parseKind(kind);
        if (memoryKind == null) {
            return rejected(trace, content,
                    "未知的记忆类型，请使用 FACT / PREFERENCE / DECISION / PROJECT。");
        }

        AiMemoryService.SaveResult result = memories.save(uid, trace.conversationUuid(), memoryKind, content);
        if (result.memory() == null) {
            return rejected(trace, content, Objects.requireNonNullElse(result.reason(), "未保存。"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", result.status());
        body.put("uuid", result.memory().uuid());
        body.put("kind", result.memory().kind());
        body.put("content", result.memory().content());
        body.put("message", "已记住：" + result.memory().content());
        if (trace != null) {
            trace.recordTool("saveMemory", content, 1, result.status(),
                    "已记住：" + result.memory().content());
        }
        return JsonUtil.toJson(body);
    }

    private static String rejected(AgentTrace trace, String content, String reason) {
        if (trace != null) {
            trace.recordTool("saveMemory", content, 0, "rejected", reason);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "rejected");
        body.put("message", reason);
        return JsonUtil.toJson(body);
    }

    private static MemoryKind parseKind(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return MemoryKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static AgentTrace trace(ToolContext context) {
        if (context == null) {
            return null;
        }
        Object value = context.getContext().get(AgentTrace.CONTEXT_KEY);
        return value instanceof AgentTrace agentTrace ? agentTrace : null;
    }
}
