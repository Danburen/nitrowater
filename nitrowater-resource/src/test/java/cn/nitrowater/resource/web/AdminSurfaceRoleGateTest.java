package cn.nitrowater.resource.web;

import cn.nitrowater.resource.config.ResourceSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the two halves of the admin role gate.
 *
 * <p>The filter chain is the floor: {@code /api/admin/**} and {@code /api/resource/upload/**} are
 * restricted there, so a newly added endpoint cannot be exposed by forgetting an annotation. The
 * class-level {@link PreAuthorize} is the readable half. Both must stay, and
 * {@link EnableMethodSecurity} must stay on: without it the annotations compile but are silently
 * inert — every admin endpoint would then answer 403 from the chain for non-admins but the
 * annotation itself would protect nothing.</p>
 */
class AdminSurfaceRoleGateTest {

    /** Controllers that administer uploads or the file console: ADMIN (SUPER_ADMIN implies it). */
    private static final List<Class<?>> ADMIN_CONTROLLERS = List.of(
            AdminResourceController.class,
            AdminPluginResourceController.class,
            ResourceUploadController.class);

    @Test
    void everyAdminControllerDeclaresTheAdminRole() {
        for (Class<?> controller : ADMIN_CONTROLLERS) {
            PreAuthorize annotation = controller.getAnnotation(PreAuthorize.class);
            assertThat(annotation)
                    .as("%s must carry @PreAuthorize at the class level", controller.getSimpleName())
                    .isNotNull();
            assertThat(annotation.value())
                    .as("%s role expression", controller.getSimpleName())
                    .isEqualTo("hasAuthority('ROLE_ADMIN')");
        }
    }

    @Test
    void methodSecurityIsEnabledForTheSecurityConfiguration() {
        assertThat(ResourceSecurityConfig.class.isAnnotationPresent(EnableMethodSecurity.class))
                .as("@PreAuthorize is inert without @EnableMethodSecurity")
                .isTrue();
    }
}
