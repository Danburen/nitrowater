package cn.nitrowater.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the {@code runCode} budget wiring in {@code application.yml}.
 *
 * <p>{@code RunCodeTool} allows three executions and answers {@code retry_exhausted} on the fourth
 * call. That only works when Spring AI lets a fourth {@code runCode} call through, so the per-tool
 * limit must be 4. With the default limit (3) the framework would reject the call first and the
 * normalised status would never reach the model.</p>
 */
class ToolLimitBindingTest {

    @Test
    void allowsOneRunCodeCallBeyondItsExecutionBudget() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(source -> env.getPropertySources().addLast(source));

        ToolCallingProperties.Limits limits = Binder.get(env)
                .bind("spring.ai.tools.limits", ToolCallingProperties.Limits.class)
                .get();

        assertThat(limits.getMaxCallsPerTool())
                .as("runCode needs a 4th call to report retry_exhausted")
                .containsEntry("runCode", 4);
        assertThat(limits.getMaxTotalToolCalls())
                .as("the total budget must not cut a runCode-only turn short")
                .isGreaterThanOrEqualTo(4);
    }
}
