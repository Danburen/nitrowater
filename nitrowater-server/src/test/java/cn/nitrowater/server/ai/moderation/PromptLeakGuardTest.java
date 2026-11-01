package cn.nitrowater.server.ai.moderation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the canary rule used to detect an answer that echoes the system prompt. */
class PromptLeakGuardTest {

    @Test
    void detectsTheCanaryAnywhereInTheAnswer() {
        assertThat(PromptLeakGuard.leaked("规则如下：……（内部标识，勿输出：" + PromptLeakGuard.CANARY + "）"))
                .isTrue();
    }

    @Test
    void ignoresEverythingElse() {
        assertThat(PromptLeakGuard.leaked("我是 NitroBot，可以帮你查文档、跑代码。")).isFalse();
        assertThat(PromptLeakGuard.leaked(null)).isFalse();
    }
}
