package cn.nitrowater.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;

/**
 * Thymeleaf engine for email bodies.
 *
 * <p>Uses the plain Thymeleaf engine (no Spring dialect): rendering happens outside a web request,
 * so the Spring view layer would only add coupling. Templates live in
 * {@code src/main/resources/templates/email/} with a {@code layout/} and a {@code fragment/}
 * sub-directory.</p>
 */
@Configuration
public class EmailTemplateConfig {

    /**
     * @return engine resolving {@code classpath:templates/email/*.html}
     */
    @Bean
    public TemplateEngine emailTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/email/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // Templates ship inside the jar, so they cannot change at runtime; caching them avoids
        // re-parsing on every verification code.
        resolver.setCacheable(true);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
