package cn.nitrowater.bff.web;

import cn.nitrowater.bff.security.BffTokenService;
import cn.nitrowater.bff.security.MultiAccountAuthorizedClientRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.servlet.HandlerMapping;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that the BFF proxy streams both directions instead of buffering a large jar in heap.
 *
 * <p>The downstream is a real {@link HttpServer}: the response body can only be read after
 * {@code proxy} returns if {@link org.springframework.web.client.RestClient} kept the downstream
 * response open for streaming (which it does for {@code InputStreamResource} results).</p>
 */
class ApiProxyControllerTest {

    @Test
    void streamsRequestBodyAndResponseBody() throws Exception {
        byte[] payload = new byte[256 * 1024];
        new Random(42).nextBytes(payload);

        AtomicReference<String> receivedContentLength = new AtomicReference<>();
        HttpServer downstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        downstream.createContext("/api/echo", exchange -> {
            receivedContentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            byte[] received = exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE);
            exchange.getResponseHeaders().add("X-Downstream", "yes");
            exchange.sendResponseHeaders(200, received.length);
            exchange.getResponseBody().write(received);
            exchange.close();
        });
        downstream.start();

        try {
            String baseUrl = "http://127.0.0.1:" + downstream.getAddress().getPort();
            ApiProxyController controller = new ApiProxyController(
                    baseUrl, baseUrl, baseUrl, 3_000L, 5_000L,
                    tokens(mock(OAuth2AuthorizedClientManager.class)));

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/echo");
            request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "/api/echo");
            request.setContent(payload);
            request.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            request.setRemoteAddr("127.0.0.1");

            ResponseEntity<Resource> response = controller.proxy(request, null, new MockHttpServletResponse());

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getFirst("X-Downstream")).isEqualTo("yes");
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().contentLength()).isEqualTo(payload.length);
            assertThat(response.getBody().getInputStream().readAllBytes()).isEqualTo(payload);
            assertThat(receivedContentLength.get()).isEqualTo(String.valueOf(payload.length));
        } finally {
            downstream.stop(0);
        }
    }

    @Test
    void routesAccountPathToTheAccountService() throws Exception {
        HttpServer account = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        account.createContext("/api/auth/account/me", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        account.start();
        try {
            String baseUrl = "http://127.0.0.1:" + account.getAddress().getPort();
            ApiProxyController controller = new ApiProxyController(
                    baseUrl, baseUrl, baseUrl, 3_000L, 5_000L,
                    tokens(mock(OAuth2AuthorizedClientManager.class)));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/account/me");
            request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "/api/auth/account/me");
            request.setRemoteAddr("127.0.0.1");

            ResponseEntity<Resource> response = controller.proxy(request, null, new MockHttpServletResponse());

            assertThat(response.getStatusCode().value()).isEqualTo(204);
        } finally {
            account.stop(0);
        }
    }

    private static BffTokenService tokens(OAuth2AuthorizedClientManager manager) {
        return new BffTokenService(manager, new MultiAccountAuthorizedClientRepository(), "nitrowater");
    }

    /**
     * A session that cannot produce a token must answer 401 immediately instead of calling upstream
     * without a Bearer header (which returned a downstream 401/500 the SPA could not act on).
     */
    @Test
    void missingTokenAnswersReauthWithoutCallingUpstream() throws Exception {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any())).thenReturn(null);
        ApiProxyController controller = new ApiProxyController(
                "http://127.0.0.1:1", "http://127.0.0.1:1", "http://127.0.0.1:1", 1_000L, 1_000L,
                tokens(manager));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/echo");
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "/api/echo");
        request.setRemoteAddr("127.0.0.1");

        ResponseEntity<Resource> response = controller.proxy(request,
                UsernamePasswordAuthenticationToken.authenticated("9000000001", null, List.of()),
                new MockHttpServletResponse());

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst("X-Auth-Reason")).isEqualTo("no_authorized_client");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody().getContentAsString(StandardCharsets.UTF_8))
                .contains("session_expired")
                .contains("no_authorized_client");
    }
}
