package cn.nitrowater.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.servlet.autoconfigure.MultipartAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.multipart.MultipartResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard: the BFF must never resolve multipart requests itself.
 *
 * <p>With the resolver enabled, Spring MVC's {@code DispatcherServlet} wraps every
 * {@code multipart/*} request in a {@code StandardMultipartHttpServletRequest} <em>before</em> the
 * handler runs, which parses — and therefore drains — the request body. {@link ApiProxyController}
 * then streamed an already exhausted input stream while still declaring the original
 * {@code Content-Length}, so the downstream service answered
 * {@code MultipartException: Failed to parse multipart servlet request} (that is what broke
 * SKILL.md import). Multipart parsing belongs to the service that owns the upload endpoint.</p>
 */
class BffMultipartDisabledTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MultipartAutoConfiguration.class));

    @Test
    void multipartResolverIsAbsentSoTheProxyStreamsTheRawBody() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MultipartResolver.class);
        });
    }
}
