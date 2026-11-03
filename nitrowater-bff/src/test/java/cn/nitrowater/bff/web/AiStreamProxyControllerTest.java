package cn.nitrowater.bff.web;

import cn.nitrowater.bff.security.BffTokenService;
import cn.nitrowater.bff.security.MultiAccountAuthorizedClientRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that the dedicated AI stream route copies SSE through the BFF and still injects the
 * server-held access token.
 */
class AiStreamProxyControllerTest {

    /**
     * The dedicated SSE route must win over {@code ApiProxyController}'s {@code /api/**} mapping,
     * otherwise the response would be buffered by the generic proxy.
     */
    @Test
    void specificStreamPatternOutranksTheApiWildcard() {
        PathPatternParser parser = PathPatternParser.defaultInstance;
        assertThat(parser.parse("/api/ai/agent/chat/stream").compareTo(parser.parse("/api/**")))
                .isNegative();
    }

    @Test
    void streamsSseAndForwardsBearerToken() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();

        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/api/ai/agent/chat/stream", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE);
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                out.write("event:sources\ndata:{\"sources\":[],\"grounded\":false}\n\n"
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.write("event:delta\ndata:{\"text\":\"你好\"}\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.write("event:done\ndata:{}\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            exchange.close();
        });
        upstream.start();

        try {
            String baseUrl = "http://127.0.0.1:" + upstream.getAddress().getPort();
            OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
            OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
            OAuth2AccessToken token = mock(OAuth2AccessToken.class);
            when(token.getTokenValue()).thenReturn("server-token");
            when(client.getAccessToken()).thenReturn(token);
            when(manager.authorize(any())).thenReturn(client);

            AiStreamProxyController controller =
                    new AiStreamProxyController(baseUrl, 3_000, 5_000, tokens(manager));
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ai/agent/chat/stream");
            request.setRemoteAddr("127.0.0.1");

            ResponseEntity<StreamingResponseBody> response = controller.stream("{\"question\":\"hi\"}",
                    principal("9000000001"), request, new MockHttpServletResponse());

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            response.getBody().writeTo(output);

            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
            assertThat(output.toString(StandardCharsets.UTF_8))
                    .contains("event:sources")
                    .contains("event:delta")
                    .contains("你好")
                    .contains("event:done");
            assertThat(authorization.get()).isEqualTo("Bearer server-token");
            assertThat(requestBody.get()).contains("\"question\"").contains("hi");
        } finally {
            upstream.stop(0);
        }
    }

    @Test
    void preservesDownstreamErrorStatusAndBody() throws Exception {
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/api/ai/agent/chat/stream", exchange -> {
            byte[] body = "{\"error\":\"rate_limited\",\"message\":\"too many\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(429, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
            exchange.close();
        });
        upstream.start();

        try {
            String baseUrl = "http://127.0.0.1:" + upstream.getAddress().getPort();
            OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
            OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
            OAuth2AccessToken token = mock(OAuth2AccessToken.class);
            when(token.getTokenValue()).thenReturn("server-token");
            when(client.getAccessToken()).thenReturn(token);
            when(manager.authorize(any())).thenReturn(client);

            AiStreamProxyController controller =
                    new AiStreamProxyController(baseUrl, 3_000, 5_000, tokens(manager));
            MockHttpServletRequest request =
                    new MockHttpServletRequest("POST", "/api/ai/agent/chat/stream");
            request.setRemoteAddr("127.0.0.1");

            ResponseEntity<StreamingResponseBody> response =
                    controller.stream("{}", principal("9000000001"), request, new MockHttpServletResponse());

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            response.getBody().writeTo(output);

            assertThat(response.getStatusCode().value()).isEqualTo(429);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(output.toString(StandardCharsets.UTF_8)).contains("rate_limited");
        } finally {
            upstream.stop(0);
        }
    }

    /**
     * A session that can no longer produce a token must answer the JSON 401 envelope (not an SSE
     * frame, and never a 500): the SPA only re-authenticates on a 401.
     */
    @Test
    void missingAuthorizationAnswersJson401() throws Exception {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any())).thenReturn(null);

        AiStreamProxyController controller =
                new AiStreamProxyController("http://127.0.0.1:1", 3_000, 5_000, tokens(manager));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ai/agent/chat/stream");

        ResponseEntity<StreamingResponseBody> response = controller.stream("{}",
                principal("9000000001"), request, new MockHttpServletResponse());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst("X-Auth-Reason")).isEqualTo("no_authorized_client");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("session_expired")
                .contains("no_authorized_client");
    }

    private static BffTokenService tokens(OAuth2AuthorizedClientManager manager) {
        return new BffTokenService(manager, new MultiAccountAuthorizedClientRepository(), "nitrowater");
    }

    private static Authentication principal(String name) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        return authentication;
    }
}
