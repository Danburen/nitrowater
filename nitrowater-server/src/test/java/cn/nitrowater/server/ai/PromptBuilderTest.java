package cn.nitrowater.server.ai;

import cn.nitrowater.server.service.ai.model.agent.ChatTurn;
import cn.nitrowater.server.service.ai.model.rag.DocCollection;
import cn.nitrowater.server.service.ai.model.skill.Skill;
import cn.nitrowater.server.service.ai.agent.PromptBuilder;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the agent system prompt, the per-turn tool set and the history bounds. */
class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void agentPromptNamesNitroBotAndListsItsTools() {
        String prompt = builder.agentSystem(List.of(), null);

        assertThat(prompt)
                .contains("NitroBot")
                .contains("<tool_policy>")
                .contains("searchDocs")
                .contains("webSearch")
                .contains("loadSkill")
                .contains("runCode")
                .contains("够用即停")
                .contains("通用");
    }

    @Test
    void agentPromptLeavesToolProcedureToTheToolDefinition() {
        String prompt = builder.agentSystem(List.of(), null);

        assertThat(prompt)
                .doesNotContain("saveMemory")
                .doesNotContain("tool.compile_error")
                .doesNotContain("tool.retry_exhausted")
                .doesNotContain("最多 3 次");
    }

    @Test
    void agentPromptStaysWithinItsAttentionBudget() {
        String prompt = builder.agentSystem(List.of(), null, List.of(), PromptBuilder.ALL_TOOLS);

        assertThat(nonWhitespaceLength(prompt)).isLessThan(1600);
    }

    @Test
    void agentPromptDescribesOnlyTheToolsAdvertisedThisTurn() {
        String prompt = builder.agentSystem(List.of(), null, List.of(), List.of("searchDocs"));

        assertThat(prompt)
                .contains("searchDocs")
                .doesNotContain("webSearch")
                .contains("本轮未开启联网搜索");
    }

    @Test
    void agentPromptRendersPolicyOnlyForAdvertisedTools() {
        String prompt = builder.agentSystem(List.of(), null, List.of(), List.of("runCode"));

        assertThat(prompt)
                .contains("执行环境")
                .doesNotContain("产品/项目自身的事实")
                .doesNotContain("可核实的外部事实")
                .doesNotContain("loadSkill");
    }

    @Test
    void agentPromptRoutesArithmeticToCalculateAndExecutionToRunCode() {
        String prompt = builder.agentSystem(List.of(), null);

        assertThat(prompt)
                .contains("calculate")
                .contains("纯算术用 calculate");
    }

    @Test
    void agentPromptTellsTheModelHowToContinueAnUnfinishedAnswer() {
        assertThat(builder.agentSystem(List.of(), null))
                .contains("<continuation>")
                .contains("上一轮最后一条未完成条目")
                .contains("换一个 query");
    }

    @Test
    void agentPromptVerifiesVolatileFactsEvenWhenTheModelFeelsConfident() {
        assertThat(builder.agentSystem(List.of(), null))
                .contains("即使觉得有把握也核实")
                .contains("没有工具结果支撑的具体值不写");
    }

    @Test
    void agentPromptListsTheSearchableCorpora() {
        String prompt = builder.agentSystem(List.of(), null, List.of(
                new DocCollection("nitrowater", "classpath:ai/docs/nitrowater", "",
                        "NitroWater 平台文档"),
                new DocCollection("velochatx", "classpath:ai/docs/velochatx",
                        "/plugin/velochatx/docs/", "VeloChatX 插件文档")));

        assertThat(prompt)
                .contains("知识库文档")
                .contains("nitrowater：NitroWater 平台文档")
                .contains("velochatx：VeloChatX 插件文档");
    }

    /**
     * The prompt is cached by the provider as a prefix, so the per-question block has to come after
     * the stable part: recalled memories change on every turn, and a changing system prompt would
     * invalidate the cache for the whole replayed history behind it.
     */
    @Test
    void perQuestionContextTravelsWithTheUserMessageNotTheSystemPrompt() {
        String context = builder.agentContext("<user_profile>偏好简洁</user_profile>", List.of());

        String system = builder.agentSystem(List.of(), null, List.of(), PromptBuilder.ALL_TOOLS);
        String user = builder.agentUser("你好", List.of(), now(), context);

        assertThat(system)
                .as("the system prompt is the cacheable prefix")
                .doesNotContain("user_context")
                .doesNotContain("偏好简洁");
        assertThat(user)
                .as("personalization is per turn, so it rides with the question")
                .contains("<user_context>")
                .contains("偏好简洁");
        assertThat(user.indexOf("<user_context>"))
                .as("it must still come before the question it personalizes")
                .isNotNegative()
                .isLessThan(user.indexOf("<question>"));
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
    void agentUserKeepsTheEndOfThePreviousAnswerForContinuation() {
        String answer = "开头".repeat(500) + "上一轮的最后一句";

        String prompt = builder.agentUser("继续",
                List.of(new ChatTurn("user", "列一下配置项"), new ChatTurn("assistant", answer)),
                now());

        assertThat(prompt)
                .contains("<conversation_history>")
                .contains("助手（上一轮回答）")
                .contains("上一轮的最后一句")
                .contains("<question>")
                .contains("继续");
    }

    @Test
    void agentUserCapsAPathologicalTurnAndKeepsItsTail() {
        String huge = "头".repeat(10_000) + "结尾在这里";

        String prompt = builder.agentUser("继续",
                List.of(new ChatTurn("assistant", huge)), now());

        assertThat(prompt)
                .contains("…（中间省略")
                .contains("结尾在这里");
    }

    /**
     * The budget and the rendered prompt have to agree: an old message is cut to a fifth of the
     * recent cap, so pricing it at its raw length would make the window look full while most of it
     * is still free — and would compact a conversation the model can still read.
     */
    @Test
    void historyCostFollowsTheCapThePromptActuallyApplies() {
        String longTurn = "字".repeat(5000);

        int recent = PromptBuilder.historyTurnTokens(longTurn, true);
        int older = PromptBuilder.historyTurnTokens(longTurn, false);

        assertThat(recent)
                .as("the newest turns are rendered verbatim: 5000 chars + 4 framing tokens")
                .isEqualTo(5004);
        assertThat(older)
                .as("older turns are cut to 2000 chars (+4 framing tokens)")
                .isEqualTo(2004);
        assertThat(PromptBuilder.historyTurnTokens("  ", true)).isZero();
    }

    @Test
    void agentUserCarriesTheTurnClock() {
        String prompt = builder.agentUser("今天几号", List.of(), now());

        assertThat(prompt)
                .contains("<now>2026-11-11 14:32:05 星期三")
                .contains("Asia/Shanghai")
                .contains("UTC+08:00");
    }

    @Test
    void agentUserKeepsTheQuestionWithinBounds() {
        String prompt = builder.agentUser("x".repeat(5_000), List.of(), now());

        assertThat(prompt)
                .contains("<question>");
        assertThat(prompt.length()).isLessThan(2_200);
    }

    private static int nonWhitespaceLength(String value) {
        return value.replaceAll("\\s", "").length();
    }

    private static ZonedDateTime now() {
        return ZonedDateTime.of(2026, 11, 11, 14, 32, 5, 0, ZoneId.of("Asia/Shanghai"));
    }
}
