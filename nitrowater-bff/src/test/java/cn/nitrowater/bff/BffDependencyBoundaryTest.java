package cn.nitrowater.bff;

import cn.nitrowater.core.web.ApiResponseAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the shared-library boundary between the BFF and {@code nitrowater-core}.
 *
 * <p>The BFF reuses core's Spring MVC/Security JSON plumbing ({@code cn.nitrowater.core.web}) but has
 * no datasource at all, so core's data stack must stay {@code compileOnly}. If JPA ever leaks back
 * onto this module's runtime classpath, Spring Boot's {@code DataSourceAutoConfiguration} fails the
 * application at startup - long after CI would have caught it here.</p>
 */
class BffDependencyBoundaryTest {

    @Test
    void webPlumbingFromCoreIsOnTheClasspath() {
        assertThat(ClassUtils.isPresent(ApiResponseAdvice.class.getName(), getClass().getClassLoader()))
                .as("core is still the single home of the ApiResponse plumbing")
                .isTrue();
    }

    @Test
    void dataStackStaysOffTheClasspath() {
        ClassLoader loader = getClass().getClassLoader();

        assertThat(ClassUtils.isPresent("jakarta.persistence.EntityManager", loader))
                .as("core's JPA dependency must stay compileOnly")
                .isFalse();
        assertThat(ClassUtils.isPresent("org.springframework.jdbc.core.JdbcTemplate", loader))
                .as("spring-jdbc must not reach the BFF")
                .isFalse();
        assertThat(ClassUtils.isPresent("org.flywaydb.core.Flyway", loader))
                .as("flyway must not reach the BFF")
                .isFalse();
    }
}
