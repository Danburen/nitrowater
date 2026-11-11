package cn.nitrowater.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the turn guardrails that are read as individual {@code @Value}s rather than through a
 * {@code @ConfigurationProperties} class.
 *
 * <p>A missing key would silently fall back to the annotation default, so a rename in
 * {@code application.yml} would only show up as "the cap stopped working". Binding them here keeps
 * the file and the code in sync.</p>
 */
class AgentPropertiesBindingTest {

    @Test
    void definesTheTurnGuardrailsInApplicationYml() throws Exception {
        StandardEnvironment env = env();

        assertThat(env.getProperty("app.ai.agent.max-rounds", Integer.class))
                .as("model rounds per turn, independent of the framework's call counter")
                .isEqualTo(18);
        assertThat(env.getProperty("app.ai.agent.turn-timeout-ms", Long.class))
                .as("below the 180s SSE timeout")
                .isEqualTo(150_000L);
        assertThat(env.getProperty("app.ai.agent.retry-attempts", Integer.class))
                .as("one retry of a round that failed before its first token")
                .isEqualTo(1);
        assertThat(env.getProperty("app.ai.agent.retry-backoff-ms", Long.class))
                .isEqualTo(400L);
        assertThat(env.getProperty("app.ai.agent.quota-floor-tokens", Long.class))
                .isEqualTo(10_000L);
    }

    private static StandardEnvironment env() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(source -> env.getPropertySources().addLast(source));
        return env;
    }
}
