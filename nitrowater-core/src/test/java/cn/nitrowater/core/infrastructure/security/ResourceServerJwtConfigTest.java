package cn.nitrowater.core.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the "activate by {@code @Import} only" contract: {@link ResourceServerJwtConfig} is
 * deliberately not a {@code @Configuration} (the account module component-scans core and must not
 * pick up a second {@code JwtDecoder}), yet an importing service still gets both beans.
 */
class ResourceServerJwtConfigTest {

    @Configuration
    @Import(ResourceServerJwtConfig.class)
    static class Importer {
    }

    @Test
    void importingTheConfigRegistersTheSharedBeans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri", "http://localhost:8090",
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri", "http://localhost:8090/oauth2/jwks",
                    "app.security.jwt.audiences", "nitrowater-test")));
            context.register(Importer.class);
            context.refresh();

            assertThat(context.getBean(JwtDecoder.class)).isNotNull();
            assertThat(context.getBean(JwtAuthenticationConverter.class)).isNotNull();
        }
    }
}
