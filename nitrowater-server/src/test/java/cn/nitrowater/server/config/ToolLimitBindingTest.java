package cn.nitrowater.server.config;

import cn.nitrowater.server.config.ai.WebSearchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the tool-budget wiring in {@code application.yml}.
 *
 * <p>{@code RunCodeTool} allows three executions and answers {@code retry_exhausted} on the fourth
 * call. That only works when Spring AI lets a fourth {@code runCode} call through, so the per-tool
 * limit must be 4. With the default limit (3) the framework would reject the call first and the
 * normalised status would never reach the model.</p>
 *
 * <p>The same file also has to keep the framework's {@code webSearch} cap above
 * {@code app.ai.web-search.max-successes}: the framework counts duplicates and failed attempts,
 * the tool counts only successes, so a cap equal to the success budget would silently refuse the
 * last legitimate search.</p>
 */
class ToolLimitBindingTest {

    @Test
    void allowsOneRunCodeCallBeyondItsExecutionBudget() throws Exception {
        ToolCallingProperties.Limits limits = limits();

        assertThat(limits.getMaxCallsPerTool())
                .as("runCode needs a 4th call to report retry_exhausted")
                .containsEntry("runCode", 4);
        assertThat(limits.getMaxTotalToolCalls())
                .as("the total budget must not cut a runCode-only turn short")
                .isGreaterThanOrEqualTo(4);
    }

    @Test
    void keepsTheFrameworkWebSearchCapAboveTheTurnSuccessBudget() throws Exception {
        ToolCallingProperties.Limits limits = limits();
        WebSearchProperties search = Binder.get(env())
                .bind("app.ai.web-search", WebSearchProperties.class)
                .get();

        assertThat(limits.getMaxCallsPerTool())
                .as("the framework counts duplicate/failed calls, so its cap must exceed the "
                        + "success budget and leave room for other tools in the turn")
                .containsKey("webSearch");
        assertThat(limits.getMaxCallsPerTool().get("webSearch"))
                .as("a success budget of %d needs more framework slots than that",
                        search.effectiveMaxSuccesses())
                .isGreaterThan(search.effectiveMaxSuccesses());
        assertThat(limits.getMaxTotalToolCalls())
                .as("one max-search turn must still leave room for the other tools")
                .isGreaterThan(limits.getMaxCallsPerTool().get("webSearch"));
    }

    @Test
    void givesCalculateMoreThanTheDefaultToolBudget() throws Exception {
        ToolCallingProperties.Limits limits = limits();

        assertThat(limits.getMaxCallsPerTool())
                .as("multi-step arithmetic often needs more than the default three calls")
                .containsEntry("calculate", 6);
        assertThat(limits.getMaxCallsPerTool().get("calculate"))
                .isGreaterThan(limits.getMaxCallsPerToolDefault());
    }

    private static ToolCallingProperties.Limits limits() throws Exception {
        return Binder.get(env())
                .bind("spring.ai.tools.limits", ToolCallingProperties.Limits.class)
                .get();
    }

    private static StandardEnvironment env() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(source -> env.getPropertySources().addLast(source));
        return env;
    }
}
