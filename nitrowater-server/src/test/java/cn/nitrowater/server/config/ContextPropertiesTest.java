package cn.nitrowater.server.config;

import cn.nitrowater.server.config.ai.ContextProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the window arithmetic behind {@code app.ai.context}.
 *
 * <p>Compaction has to fold turns <em>before</em> the replay window starts dropping them: if the
 * trigger sat above the budget the model would spend a few turns with neither the original turns nor
 * a summary that covers them. The trigger is therefore derived from the budget, never configured
 * independently, and both are a share of the model window — moving to a 64K model has to move them
 * with it.</p>
 */
class ContextPropertiesTest {

    @Test
    void bindsTheWindowFromApplicationYml() throws Exception {
        ContextProperties context = Binder.get(env())
                .bind("app.ai.context", ContextProperties.class)
                .get();

        assertThat(context.modelWindowTokens()).isEqualTo(1000000);
        assertThat(context.historyBudgetTokens())
                .as("pinned: the budget is a cost lever, it does not grow with the 1M window")
                .isEqualTo(64000);
        assertThat(context.compactTriggerTokens())
                .as("compaction starts below the window, not at it")
                .isEqualTo(51200)
                .isLessThan(context.historyBudgetTokens());
        assertThat(context.historyHardMaxMessages())
                .as("the row cap is a query bound, the token budget is the real limit")
                .isEqualTo(120);
        assertThat(context.compactKeepTurns())
                .as("two turns stay verbatim, which is ~4 messages")
                .isEqualTo(2);
    }

    @Test
    void halvesTheBudgetWhenTheModelWindowIsHalved() {
        ContextProperties context =
                new ContextProperties(64000, 0.5, null, 120, true, 32, 0.8, 2, 2000, 24000);

        assertThat(context.historyBudgetTokens()).isEqualTo(32000);
        assertThat(context.compactTriggerTokens()).isEqualTo(25600);
    }

    @Test
    void neverLetsAnExplicitBudgetOrTriggerExceedTheModelWindow() {
        ContextProperties context =
                new ContextProperties(64000, 0.5, 640000, 120, true, 32, 1.5, 2, 2000, 24000);

        assertThat(context.historyBudgetTokens())
                .as("a typo must not build prompts the provider rejects")
                .isEqualTo(64000);
        assertThat(context.compactTriggerTokens())
                .as("a ratio above 1 is clamped so the trigger can never pass the window")
                .isEqualTo(64000);
    }

    @Test
    void keepsTheLegacyTurnCountAvailable() {
        ContextProperties legacy =
                new ContextProperties(128000, 0.5, 0, 120, true, 32, 0.8, 2, 2000, 24000);

        assertThat(legacy.tokenBudgetEnabled()).isFalse();
        assertThat(legacy.tokenTriggerEnabled()).isFalse();
    }

    @Test
    void disablesOnlyTheTokenTriggerWhenItsRatioIsZero() {
        ContextProperties countTriggered =
                new ContextProperties(128000, 0.5, null, 120, true, 32, 0.0, 2, 2000, 24000);

        assertThat(countTriggered.historyBudgetTokens()).isEqualTo(64000);
        assertThat(countTriggered.tokenTriggerEnabled()).isFalse();
    }

    private static StandardEnvironment env() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(source -> env.getPropertySources().addLast(source));
        return env;
    }
}
