package cn.nitrowater.server.ai;

import cn.nitrowater.server.service.ai.AiAdminSettingsService.Settings;
import cn.nitrowater.server.infra.enums.ModerationMode;
import cn.nitrowater.server.service.ai.model.AiTokenUsage;
import cn.nitrowater.server.service.ai.model.AiTokenUsageMeter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.Usage;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure accounting rules: provider usage wins, character estimates are marked as estimates. */
class AiTokenUsageMeterTest {

    @Test
    void reportsProviderUsageWhenStreamEmitsIt() {
        AiTokenUsageMeter meter = new AiTokenUsageMeter();

        meter.record(usage(120, 30));

        AiTokenUsage snapshot = meter.snapshot(500, "ignored fallback output");

        assertThat(snapshot.inputTokens()).isEqualTo(120);
        assertThat(snapshot.outputTokens()).isEqualTo(30);
        assertThat(snapshot.estimated()).isFalse();
        assertThat(snapshot.totalTokens()).isEqualTo(150);
    }

    @Test
    void ignoresEmptyUsageChunksAndAccumulatesRealOnes() {
        AiTokenUsageMeter meter = new AiTokenUsageMeter();

        meter.record(usage(0, 0));
        meter.record(null);

        assertThat(meter.hasReportedUsage()).isFalse();

        meter.record(usage(10, 5));
        meter.record(usage(10, 5));

        assertThat(meter.snapshot(0, "").totalTokens()).isEqualTo(30);
    }

    @Test
    void fallsBackToCharacterEstimateWhenProviderOmitsUsage() {
        AiTokenUsageMeter meter = new AiTokenUsageMeter();

        AiTokenUsage snapshot = meter.snapshot(400, "12345678");

        assertThat(snapshot.inputTokens()).isEqualTo(400);
        assertThat(snapshot.outputTokens()).isEqualTo(2);
        assertThat(snapshot.estimated()).isTrue();
    }

    @Test
    void estimatesTokensFromTextConservatively() {
        assertThat(AiTokenUsageMeter.estimateTokens(null)).isZero();
        assertThat(AiTokenUsageMeter.estimateTokens("  ")).isZero();
        assertThat(AiTokenUsageMeter.estimateTokens("abcd")).isEqualTo(1);
        assertThat(AiTokenUsageMeter.estimateTokens("abcde")).isEqualTo(2);
    }

    private static Usage usage(int promptTokens, int completionTokens) {
        return new Usage() {
            @Override
            public Integer getPromptTokens() {
                return promptTokens;
            }

            @Override
            public Integer getCompletionTokens() {
                return completionTokens;
            }

            @Override
            public Object getNativeUsage() {
                return null;
            }
        };
    }

    @Test
    void adminSettingsGateOnlyTheToolsTheyOwn() {
        Settings disabled = new Settings(1000, true, true, true, false, false, false, false,
                ModerationMode.LOG);

        assertThat(disabled.toolEnabled("searchDocs")).isFalse();
        assertThat(disabled.toolEnabled("loadSkill")).isFalse();
        assertThat(disabled.toolEnabled("runCode")).isFalse();
        assertThat(disabled.toolEnabled("webSearch")).isFalse();
        assertThat(disabled.toolEnabled("futureCoreTool")).isTrue();
        assertThat(disabled.toolEnabled(" ")).isFalse();

        Settings enabled = new Settings(1000, true, true, true, true, true, true, true,
                ModerationMode.LOG);
        assertThat(enabled.toolEnabled("searchDocs")).isTrue();
        assertThat(enabled.toolEnabled("webSearch")).isTrue();
    }
}
