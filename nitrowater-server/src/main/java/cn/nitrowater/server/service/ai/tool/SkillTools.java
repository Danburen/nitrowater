package cn.nitrowater.server.service.ai.tool;

import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import cn.nitrowater.server.service.ai.model.Skill;
import cn.nitrowater.server.service.ai.model.tool.ToolCode;
import cn.nitrowater.server.service.ai.model.tool.ToolResponse;
import cn.nitrowater.server.service.ai.SkillRegistry;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes the skill catalog to the agent.
 *
 * <p>Skills are instructions, not data. The user may preselect one (inlined by the prompt
 * builder); otherwise the model can load the matching workflow on demand. The tool only returns
 * content from {@link SkillRegistry}, never user input.</p>
 */
@Component
public class SkillTools {

    private final SkillRegistry skills;

    public SkillTools(SkillRegistry skills) {
        this.skills = skills;
    }

    /**
     * Loads one skill's instructions.
     *
     * @param name skill name (e.g. {@code config-walkthrough})
     * @param context Spring AI tool context (carries the {@link AgentTrace})
     * @return JSON with the instructions, or an error code plus {@code data.available}
     */
    @Tool(name = "loadSkill", description = """
            加载一个专用工作流（技能）的完整指令。当问题匹配技能描述时调用；一次只加载一个。
            """)
    public String loadSkill(
            @ToolParam(description = "技能名称，例如 config-walkthrough / troubleshooting / command-reference") String name,
            ToolContext context) {
        AgentTrace trace = trace(context);
        Skill skill = skills.get(name);
        if (skill == null) {
            List<String> available = skills.list().stream().map(Skill::name).toList();
            return ToolResponse.of(ToolCode.INVALID_REQUEST,
                            "未知技能，请从 data.available 中选择一个再调用。",
                            Map.of("available", available))
                    .record(trace, "loadSkill", name, 0)
                    .json();
        }
        if (trace != null) {
            trace.addSkill(skill.name());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", skill.name());
        data.put("description", skill.description() == null ? "" : skill.description());
        data.put("instructions", skill.body() == null ? "" : skill.body());
        return ToolResponse.ok(data).record(trace, "loadSkill", name, 1).json();
    }

    private static AgentTrace trace(ToolContext context) {
        if (context == null) {
            return null;
        }
        Object value = context.getContext().get(AgentTrace.CONTEXT_KEY);
        return value instanceof AgentTrace agentTrace ? agentTrace : null;
    }
}
