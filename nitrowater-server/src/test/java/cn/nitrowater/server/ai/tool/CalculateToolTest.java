package cn.nitrowater.server.ai.tool;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.service.ai.model.agent.AgentTrace;
import cn.nitrowater.server.service.ai.model.agent.RenderPolicy;
import cn.nitrowater.server.service.ai.model.agent.ToolCall;
import cn.nitrowater.server.service.ai.tool.CalculateTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the agent-facing contract of {@code calculate}: exact decimal results, a fixed grammar and
 * a trace entry that renders as a compact chip instead of a full card.
 *
 * <p>The values here are the ones that broke hand-written model arithmetic during the probe
 * ({@code 0.1 + 0.2}, a large multiplication, a percentage), so a regression in precision fails
 * loudly instead of silently drifting.</p>
 */
class CalculateToolTest {

    private final CalculateTool tool = new CalculateTool();

    @Test
    void computesExactDecimalsInsteadOfBinaryFloats() {
        assertThat(result("0.1 + 0.2")).isEqualTo("0.3");
        assertThat(result("12345.678 * 987.65")).isEqualTo("12193208.8767");
        assertThat(result("48392 * 0.17")).isEqualTo("8226.64");
    }

    @Test
    void honoursPrecedenceParenthesesAndUnaryMinus() {
        assertThat(result("2 + 3 * 4")).isEqualTo("14");
        assertThat(result("(2 + 3) * 4")).isEqualTo("20");
        assertThat(result("-3 * (2 - 5)")).isEqualTo("9");
        assertThat(result("10 / 4")).isEqualTo("2.5");
    }

    @Test
    void supportsTheDocumentedFunctions() {
        assertThat(result("abs(0 - 7)")).isEqualTo("7");
        assertThat(result("min(3, 1.5, 2)")).isEqualTo("1.5");
        assertThat(result("max(3, 1.5, 2)")).isEqualTo("3");
        assertThat(result("round(2.345, 2)")).isEqualTo("2.35");
        assertThat(result("round(2.5)")).isEqualTo("3");
        assertThat(result("sqrt(16)")).isEqualTo("4");
    }

    @Test
    void reportsUndefinedMathAsRetryableMathError() {
        // `success` means the call was handled (same rule as tool.compile_error): the evaluator ran
        // and produced a definite answer, so only the code carries the failure.
        String divided = json("1 / 0");
        assertThat(divided).contains("\"code\":\"tool.math_error\"")
                .contains("\"retryable\":true")
                .contains("除数为 0");

        assertThat(json("sqrt(0 - 4)")).contains("\"code\":\"tool.math_error\"");
    }

    @Test
    void acceptsTheOperatorsPeopleActuallyType() {
        assertThat(result("1234 × 5678")).isEqualTo("7006652");
        assertThat(result("10 ÷ 4")).isEqualTo("2.5");
        assertThat(result("１＋２")).isEqualTo("3");
    }

    @Test
    void rejectsInvalidSyntaxWithoutThrowing() {
        assertThat(json("2 +")).contains("\"code\":\"tool.invalid_request\"");
        assertThat(json("2 ^ 3")).contains("\"code\":\"tool.invalid_request\"");
        assertThat(json("1e10")).contains("\"code\":\"tool.invalid_request\"");
        assertThat(json("nope(1)")).contains("\"code\":\"tool.invalid_request\"");
        assertThat(json("   ")).contains("\"code\":\"tool.invalid_request\"");
        assertThat(json("48392 * 17%")).contains("\"code\":\"tool.invalid_request\"")
                .contains("百分号");
    }

    @Test
    void rejectsExpressionsBeyondTheParserBounds() {
        assertThat(json("(".repeat(40) + "1" + ")".repeat(40)))
                .contains("\"code\":\"tool.invalid_request\"");
        assertThat(json("1" + "0".repeat(500)))
                .contains("\"code\":\"tool.invalid_request\"");
    }

    @Test
    void recordsTheCallAsACompactChip() {
        AgentTrace trace = new AgentTrace("u1");
        trace.registerRender("calculate", RenderPolicy.COMPACT);

        tool.calculate("0.1 + 0.2", context(trace));

        assertThat(trace.calls()).containsExactly(
                new ToolCall("calculate", "0.1 + 0.2", 1, "tool.ok", "", null, RenderPolicy.COMPACT));
    }

    @Test
    void worksWithoutAToolContext() {
        assertThat(tool.calculate("6 * 7", null)).contains("\"result\":\"42\"");
    }

    private String result(String expression) {
        Map<?, ?> payload = payload(json(expression));
        assertThat(payload.get("result")).isNotNull();
        return String.valueOf(payload.get("result"));
    }

    private static Map<?, ?> payload(String json) {
        Map<?, ?> root = JsonUtil.fromJson(json, Map.class);
        assertThat(root).isNotNull();
        return (Map<?, ?>) root.get("data");
    }

    private String json(String expression) {
        return tool.calculate(expression, context(new AgentTrace("u1")));
    }

    private static ToolContext context(AgentTrace trace) {
        return new ToolContext(Map.of(AgentTrace.CONTEXT_KEY, trace));
    }
}
