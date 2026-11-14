package cn.nitrowater.resource.web;

import cn.nitrowater.resource.infrastructure.exception.ResourceException;
import cn.nitrowater.resource.service.ResourceService;
import cn.nitrowater.resource.service.dto.ResourceMeta;
import cn.nitrowater.resource.infrastructure.domain.ResourceStatus;
import cn.nitrowater.lib.common.io.ResourceType;
import cn.nitrowater.resource.infrastructure.domain.ResourceVisibility;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Guards the two properties of the admin upload surface: the deployment-wide kill switch and the
 * streaming contract of the proxy PUT (no {@code byte[]} buffering, mandatory {@code Content-Length}).
 *
 * <p>The service collaborator is {@code null} wherever an assertion must stop at the guard, so a
 * regression that reaches the service fails loudly instead of silently passing.</p>
 */
class ResourceUploadControllerTest {

    private static final Authentication AUTH = new JwtAuthenticationToken(Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("uid", "42")
            .subject("42")
            .build());

    private static final ResourceMeta META = new ResourceMeta(
            "uuid", "nitrowater", null, "note.txt", ResourceType.TEXT, "text/plain", 4L,
            ResourceVisibility.PRIVATE, ResourceStatus.ACTIVE, null,
            Instant.parse("2026-11-14T00:00:00Z"), "/api/resource/uuid/download");

    @Test
    void rejectsEveryUploadEndpointWhenDisabled() {
        ResourceUploadController controller = new ResourceUploadController(null, false);

        assertDisabled(() -> controller.policy(null, null));
        assertDisabled(() -> controller.callback(null, null));
        assertDisabled(() -> controller.proxyUpload("uuid", request(4), AUTH));
    }

    @Test
    void proxyUploadRejectsAMissingContentLength() {
        ResourceUploadController controller = new ResourceUploadController(null, true);

        assertThatThrownBy(() -> controller.proxyUpload("uuid", request(-1), AUTH))
                .isInstanceOfSatisfying(ResourceException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.LENGTH_REQUIRED);
                    assertThat(ex.code()).isEqualTo("length_required");
                });
    }

    @Test
    void proxyUploadStreamsTheRequestBodyWithoutBufferingIt() throws Exception {
        ResourceService service = mock(ResourceService.class);
        when(service.putBytes(eq("uuid"), eq(42L), any(), eq(4L), any())).thenReturn(META);
        ResourceUploadController controller = new ResourceUploadController(service, true);
        HttpServletRequest request = request(4);

        controller.proxyUpload("uuid", request, AUTH);

        // The request stream is handed to the service as-is: a large artifact must never be
        // materialised in the servlet heap as a byte[].
        ArgumentCaptor<InputStream> body = ArgumentCaptor.forClass(InputStream.class);
        verify(service).putBytes(eq("uuid"), eq(42L), body.capture(), eq(4L), any());
        assertThat(body.getValue()).isInstanceOf(ServletInputStream.class);
    }

    @Test
    void proxyUploadDoesNotTouchTheServiceBeforeTheGuard() {
        ResourceService service = mock(ResourceService.class);
        ResourceUploadController controller = new ResourceUploadController(service, false);

        assertDisabled(() -> controller.proxyUpload("uuid", request(4), AUTH));
        verifyNoInteractions(service);
    }

    private static HttpServletRequest request(long contentLength) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(contentLength);
        when(request.getContentType()).thenReturn("application/octet-stream");
        try {
            when(request.getInputStream()).thenAnswer(invocation -> new StubServletInputStream());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return request;
    }

    private static void assertDisabled(Call call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResourceException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.code()).isEqualTo("uploads_disabled");
                });
    }

    /** Controller call that may throw a checked {@link IOException} (proxy upload). */
    @FunctionalInterface
    private interface Call {
        void run() throws Exception;
    }

    /** Minimal empty {@link ServletInputStream}; the payload itself is irrelevant to the contract. */
    private static final class StubServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate = new ByteArrayInputStream(new byte[0]);

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }
    }
}
