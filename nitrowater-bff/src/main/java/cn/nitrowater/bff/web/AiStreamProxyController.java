package cn.nitrowater.bff.web;

import cn.nitrowater.bff.security.BffTokenService;
import cn.nitrowater.bff.security.ReauthRequiredException;
import cn.nitrowater.bff.security.SessionEpochFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import cn.nitrowater.lib.utils.StringUtil;

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
    private final BffTokenService tokens;

    public AiStreamProxyController(
            @Value("${app.rs.base-url}") String rsBaseUrl,
            @Value("${app.api.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${app.api.read-timeout-ms:45000}") long readTimeoutMs,
            BffTokenService tokens) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(readTimeoutMs, STREAM_READ_TIMEOUT_MS)));
        this.serverRestClient = RestClient.builder().baseUrl(rsBaseUrl).requestFactory(requestFactory).build();
        this.tokens = tokens;
    }

    /**
     * Proxies the agent SSE endpoint to the business API, preserving SSE framing.
     *
     * <p>The target path is the same as the incoming path ({@code /api/ai/agent/chat/stream}); it
     * must bypass the generic buffered {@code ApiProxyController}.</p>
     *
     * @param body the chat request JSON
     * @param authentication the BFF session principal
     * @param request the incoming request
     * @param response the outgoing response (used when a refreshed token must be written back)
     * @return a streaming response with {@code text/event-stream}, or the downstream error
     */
    @PostMapping(path = "/api/ai/agent/chat/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@RequestBody String body,
            Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        String accessToken;
        try {
            accessToken = this.tokens.accessToken(authentication, request, response);
        } catch (ReauthRequiredException ex) {
            // Session gone or refresh rejected: a JSON 401 (not an SSE frame) so the SPA can send
            // the user back to sign in — mirrors the downstream-error branch below.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .headers(ReauthResponse.headers(ex.reason()))
                    .body(output -> output.write(ReauthResponse.body(ex.reason())));
        }
        String clientAddress = request.getRemoteAddr();
        String targetPath = (String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (StringUtil.isBlank(targetPath)) {
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
}
