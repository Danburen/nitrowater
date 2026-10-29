package cn.nitrowater.bff.web;

import cn.nitrowater.bff.security.SessionEpochFilter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Streams the documentation-assistant SSE endpoints through the BFF.
 *
 * <p>The generic {@code /api/**} proxy returns a {@code Resource}, which Spring MVC copies in
 * fixed-size buffers — acceptable for JSON and jar downloads, but it would batch SSE tokens. This
 * dedicated mapping (more specific than {@code /api/**}) copies the downstream stream chunk by chunk
 * and flushes after every read, so the browser receives tokens as they are produced while the
 * access token still never leaves the BFF.</p>
 *
 * <p>The downstream call is made <b>before</b> the response is committed: a rejected request
 * (400/401/429/503) must keep its status and JSON error body instead of being wrapped in a
 * {@code 200 text/event-stream} with an unparseable body.</p>
 */
@RestController
public class AiStreamProxyController {

    private static final long STREAM_READ_TIMEOUT_MS = 180_000L;

    private final RestClient serverRestClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String registrationId;

    public AiStreamProxyController(
            @Value("${app.rs.base-url}") String rsBaseUrl,
            @Value("${app.api.read-timeout-ms:45000}") long readTimeoutMs,
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${app.oidc.registration-id:nitrowater}") String registrationId) {
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(readTimeoutMs, STREAM_READ_TIMEOUT_MS)));
        this.serverRestClient = RestClient.builder().baseUrl(rsBaseUrl).requestFactory(requestFactory).build();
        this.authorizedClientManager = authorizedClientManager;
        this.registrationId = registrationId;
    }

    /**
     * Proxies both AI SSE endpoints to the business API, preserving SSE framing.
     *
     * <p>The target path is the same as the incoming path ({@code /api/ai/chat/stream} or
     * {@code /api/ai/agent/chat/stream}); both must bypass the generic buffered
     * {@code ApiProxyController}.</p>
     *
     * @param body the chat request JSON
     * @param authentication the BFF session principal
     * @param request the incoming request
     * @return a streaming response with {@code text/event-stream}, or the downstream error
     */
    @PostMapping(path = {"/api/ai/chat/stream", "/api/ai/agent/chat/stream"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@RequestBody String body,
            Authentication authentication, HttpServletRequest request) {
        String accessToken = accessToken(authentication);
        String clientAddress = request.getRemoteAddr();
        String targetPath = (String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (targetPath == null || targetPath.isBlank()) {
            targetPath = request.getRequestURI();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
        headers.setBearerAuth(accessToken);
        headers.set("X-Forwarded-For", clientAddress);

        // Preflight: wait for the downstream status/headers. A 2xx body is an open SSE stream
        // (InputStreamResource keeps it open for the streaming response below).
        ResponseEntity<Resource> upstream = serverRestClient.post()
                .uri(targetPath)
                .headers(target -> target.addAll(headers))
                .body(body)
                .exchange((upstreamRequest, upstreamResponse) -> {
                    InputStream input = upstreamResponse.getBody();
                    Resource resource = input == null ? null : new InputStreamResource(input);
                    HttpHeaders forwarded = new HttpHeaders();
                    if (upstreamResponse.getStatusCode().value() == 401
                            && request.getUserPrincipal() != null) {
                        // The access token is no longer accepted upstream: drop the local session.
                        SessionEpochFilter.invalidate(request);
                        forwarded.set("X-Auth-Reason", "upstream_unauthorized");
                        forwarded.add(HttpHeaders.SET_COOKIE, SessionCookies.expired().toString());
                    }
                    return ResponseEntity.status(upstreamResponse.getStatusCode())
                            .headers(forwarded)
                            .body(resource);
                });

        if (!upstream.getStatusCode().is2xxSuccessful()) {
            String error = read(upstream.getBody());
            byte[] bytes = error.getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.status(upstream.getStatusCode())
                    .headers(upstream.getHeaders())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(output -> output.write(bytes));
        }

        StreamingResponseBody stream = output -> {
            Resource resource = upstream.getBody();
            if (resource == null) {
                return;
            }
            try (InputStream input = resource.getInputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    output.write(buffer, 0, read);
                    // Flush per chunk: this is what turns the proxy into a stream instead of a
                    // buffered copy.
                    output.flush();
                }
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Accel-Buffering", "no")
                .body(stream);
    }

    private static String read(Resource resource) {
        if (resource == null) {
            return "";
        }
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * @param authentication the BFF session principal
     * @return the server-held access token
     * @throws ResponseStatusException 401 when the session has no usable authorized client
     */
    private String accessToken(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login is required");
        }
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                        .principal(authentication)
                        .build());
        if (client == null || client.getAccessToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login is required");
        }
        return client.getAccessToken().getTokenValue();
    }
}
