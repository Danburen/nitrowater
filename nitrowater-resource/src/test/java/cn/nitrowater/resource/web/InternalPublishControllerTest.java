package cn.nitrowater.resource.web;

import cn.nitrowater.resource.application.ResourceException;
import cn.nitrowater.resource.application.ResourceService;
import cn.nitrowater.resource.config.ResourceProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies the internal publish endpoints' size policy: plugin jars use the (larger) plugin cap and
 * stream straight into storage, everything else keeps the end-user cap.
 */
class InternalPublishControllerTest {

    private static final long USER_CAP = 10L * 1024 * 1024;
    private static final long PLUGIN_CAP = 64L * 1024 * 1024;
    private static final long TWENTY_MB = 20L * 1024 * 1024;

    private final ResourceService service = mock(ResourceService.class);
    private final ResourceProperties properties = mock(ResourceProperties.class);
    private final InternalPublishController controller = new InternalPublishController(service, properties);

    @BeforeEach
    void setUp() {
        when(properties.internalPublishEnabled()).thenReturn(true);
        when(properties.internalToken()).thenReturn("token");
        when(properties.maxSizeBytes()).thenReturn(USER_CAP);
        when(properties.effectivePluginMaxSizeBytes()).thenReturn(PLUGIN_CAP);
    }

    @Test
    void pluginJarIsAllowedAboveTheEndUserCapAndStreamed() throws Exception {
        HttpServletRequest request = requestWith(TWENTY_MB);
        when(service.publishSystem(any(), any(), any(), any(), any(), any(InputStream.class), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("reached the service"));

        assertThatThrownBy(() -> controller.publish("app", "grp", "Plugin-1.2.0.jar", "1.2.0",
                "token", "application/java-archive", request))
                .isInstanceOf(IllegalStateException.class);

        verify(service).publishSystem(eq("app"), eq("grp"), eq("Plugin-1.2.0.jar"), eq("1.2.0"),
                eq("application/java-archive"), any(InputStream.class), eq(TWENTY_MB), eq(PLUGIN_CAP));
    }

    @Test
    void nonArtifactStillUsesTheEndUserCap() throws Exception {
        HttpServletRequest request = requestWith(TWENTY_MB);

        assertThatThrownBy(() -> controller.publish("app", "grp", "notes.txt", null,
                "token", "text/plain", request))
                .isInstanceOfSatisfying(ResourceException.class,
                        ex -> assertThat(ex.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));

        verifyNoInteractions(service);
    }

    private static HttpServletRequest requestWith(long contentLength) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(contentLength);
        when(request.getInputStream()).thenReturn(emptyStream());
        return request;
    }

    /** An empty body; this test only exercises the size gate, never the bytes. */
    private static ServletInputStream emptyStream() {
        return new ServletInputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public boolean isFinished() {
                return true;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                // no async reads in tests
            }
        };
    }
}
