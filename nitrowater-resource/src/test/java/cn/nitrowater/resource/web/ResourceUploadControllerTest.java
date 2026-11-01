package cn.nitrowater.resource.web;

import cn.nitrowater.resource.application.ResourceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the deployment-wide switch that keeps the end-user upload surface mapped but disabled.
 *
 * <p>The service collaborator is {@code null} on purpose: every assertion below either stops at the
 * guard (503) or blows up past it, so the guard is the only thing under test.</p>
 */
class ResourceUploadControllerTest {

    @Test
    void rejectsEveryUploadEndpointWhenDisabled() {
        ResourceUploadController controller = new ResourceUploadController(null, false);

        assertDisabled(() -> controller.policy(null, null));
        assertDisabled(() -> controller.callback(null, null));
        assertDisabled(() -> controller.proxyUpload("uuid", new byte[0], "image/png", null));
    }

    @Test
    void letsTheCallThroughWhenEnabled() {
        ResourceUploadController controller = new ResourceUploadController(null, true);

        assertThatThrownBy(() -> controller.proxyUpload("uuid", new byte[0], "image/png", null))
                .isNotInstanceOf(ResourceException.class);
    }

    private static void assertDisabled(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResourceException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.code()).isEqualTo("uploads_disabled");
                });
    }
}
