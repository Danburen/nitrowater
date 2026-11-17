package cn.nitrowater.account.communication.email;

import cn.nitrowater.account.config.EmailTemplateConfig;
import cn.nitrowater.account.config.ResendConfig;
import cn.nitrowater.account.infra.communication.email.EmailSendService;
import cn.nitrowater.account.infra.communication.email.ResendEmailSendService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring contract for the outbound email stack.
 *
 * <p>Email has no SMTP fallback, so the mode switch must be unambiguous: {@code mock} registers no
 * provider (the console sender in core covers it), {@code real} registers exactly one, and a
 * {@code real} run with missing credentials must fail the context instead of starting healthy and
 * failing per request.</p>
 */
class EmailWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EmailTemplateConfig.class, ResendConfig.class,
                    ResendEmailSendService.class)
            .withPropertyValues(
                    "app.communication.email.resend.api-key=re_test_key",
                    "app.communication.email.from=NitroWater <no-reply@nitrowater.cn>");

    @Test
    void realModeRegistersResendSender() {
        runner.withPropertyValues("app.communication.mode=real").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(EmailSendService.class).size());
            assertTrue(context.getBean(EmailSendService.class) instanceof ResendEmailSendService);
        });
    }

    @Test
    void mockModeRegistersNoProvider() {
        runner.withPropertyValues("app.communication.mode=mock").run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(EmailSendService.class).isEmpty());
        });
    }

    @Test
    void realModeWithoutApiKeyFailsStartup() {
        runner.withPropertyValues("app.communication.mode=real",
                "app.communication.email.resend.api-key=").run(context -> {
            assertNotNull(context.getStartupFailure());
            assertTrue(rootMessage(context.getStartupFailure()).contains("RESEND_API_KEY"));
        });
    }

    @Test
    void realModeWithoutSenderFailsStartup() {
        runner.withPropertyValues("app.communication.mode=real",
                "app.communication.email.from=").run(context -> {
            assertNotNull(context.getStartupFailure());
            assertTrue(rootMessage(context.getStartupFailure()).contains("MAIL_FROM"));
        });
    }

    /**
     * @param failure context startup failure
     * @return deepest cause message, where the explicit configuration hint lives
     */
    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }
}
