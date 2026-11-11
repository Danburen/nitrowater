package cn.nitrowater.server.ai;

import cn.nitrowater.server.service.ai.model.agent.ChatTurn;
import cn.nitrowater.server.service.ai.model.rag.DocCollection;
import cn.nitrowater.server.service.ai.model.skill.Skill;
import cn.nitrowater.server.service.ai.agent.PromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the agent system prompt, skill inlining and history bounds. */
class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void agentPromptNamesNitroBotAndListsItsTools() {
        assertThat(builder.agentSystem(List.of(), null))
                .contains("NitroBot")
                .contains("searchDocs")
                .contains("loadSkill")
                .contains("webSearch")
                .contains("runCode")
                .contains("saveMemory")
                .contains("够用即停")
                .contains("remaining")
                .contains("通用");
    }

    @Test
    void agentPromptListsTheSearchableCorpora() {
        String prompt = builder.agentSystem(List.of(), null, null, List.of(
                new DocCollection("nitrowater", "classpath:ai/docs/nitrowater", "",
                        "NitroWater 平台文档"),
                new DocCollection("velochatx", "classpath:ai/docs/velochatx",
                        "/plugin/velochatx/docs/", "VeloChatX 插件文档")));

        assertThat(prompt)
                .contains("知识库文档")
                .contains("nitrowater：NitroWater 平台文档")
                .contains("velochatx：VeloChatX 插件文档");
    }

    @Test
    void agentPromptInlinesTheSelectedSkill() {
        Skill selected = new Skill("troubleshooting", "排查流程", "用户报错时", "1. 先看日志",
                "builtin");

        String prompt = builder.agentSystem(List.of(selected), List.of(selected));

        assertThat(prompt).contains("可用技能").contains("troubleshooting").contains("1. 先看日志");
    }

    @Test
    void agentPromptInlinesEverySelectedSkillInPriorityOrder() {
        Skill first = new Skill("troubleshooting", "排查流程", "用户报错时", "1. 先看日志", "builtin");
        Skill second = new Skill("command-reference", "命令参考", "查命令时", "1. 查命令表", "builtin");

        String prompt = builder.agentSystem(List.of(first, second), List.of(first, second));

        assertThat(prompt)
                .contains("用户已选择以下 2 个技能")
                .contains("以列表顺序靠前的技能为准")
                .contains("### troubleshooting")
                .contains("### command-reference");
        assertThat(prompt.indexOf("### troubleshooting"))
                .isLessThan(prompt.indexOf("### command-reference"));
    }

    @Test
    void agentUserKeepsHistoryAndQuestionWithinBounds() {
        String history = "x".repeat(5_000);

        String prompt = builder.agentUser("怎么配置 chat-format？",
                List.of(new ChatTurn("user", history), new ChatTurn("assistant", "上一个回答")));

        assertThat(prompt)
                .contains("<conversation_history>")
                .contains("用户: ")
                .contains("助手: 上一个回答")
                .contains("<question>")
                .contains("怎么配置 chat-format？");
        assertThat(prompt.length()).isLessThan(2_500);
    }
}
