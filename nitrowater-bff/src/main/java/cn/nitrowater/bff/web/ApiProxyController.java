package cn.nitrowater.bff.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

/**
 * Proxies {@code /api/**} to the downstream services, attaching a server-held Bearer access token.
 *
 * <p>Requests are routed by path prefix: {@code /api/resource/**} (and its {@code /api/public/...}
 * form) go to the resource service; everything else goes to the business API. The access token is
 * obtained through {@link OAuth2AuthorizedClientManager}, so it is refreshed with the refresh token
 * when expired — the SPA never deals with tokens at all.</p>
 */
@RestController
public class ApiProxyController {

    /** Headers that must not be copied verbatim between the two hops. */
    private static final Set<String> EXCLUDED_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host",
            "content-length", "authorization", "cookie",
            // Overwritten with the real client address so downstreams can rate-limit per IP.
            "x-forwarded-for", "x-real-ip");

    private final RestClient serverRestClient;
    private final RestClient resourceRestClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String registrationId;

    public ApiProxyController(
            @Value("${app.rs.base-url}") String rsBaseUrl,
            @Value("${app.resource.base-url:http://localhost:8093}") String resourceBaseUrl,
            @Value("${app.api.read-timeout-ms:45000}") long readTimeoutMs,
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${app.oidc.registration-id:nitrowater}") String registrationId) {
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.serverRestClient = RestClient.builder().baseUrl(rsBaseUrl).requestFactory(requestFactory).build();
        this.resourceRestClient = RestClient.builder().baseUrl(resourceBaseUrl).requestFactory(requestFactory).build();
        this.authorizedClientManager = authorizedClientManager;
        this.registrationId = registrationId;
    }

    /** Routes resource-management paths to the resource service; everything else to the business API. */
    private static boolean isResourcePath(String path) {
        return path.equals("/api/resource")
                || path.startsWith("/api/resource/")
                || path.equals("/api/resources")
                || path.startsWith("/api/resources/")
                || path.startsWith("/api/public/resource")
                // File-management console (resource-owned); must win over the generic /api/admin/**
                // that otherwise routes to the business API.
                || path.equals("/api/admin/resources")
                || path.startsWith("/api/admin/resources/");
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, Authentication authentication) throws IOException {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String query = request.getQueryString();
        String target = query == null || query.isEmpty() ? path : path + "?" + query;

        OAuth2AuthorizedClient client = (authentication != null
                && !(authentication instanceof AnonymousAuthenticationToken))
                ? authorizedClientManager.authorize(
                        OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                                .principal(authentication)
                                .build())
                : null;

        byte[] body = request.getInputStream().readAllBytes();

        RestClient restClient = isResourcePath(path) ? resourceRestClient : serverRestClient;
        RestClient.RequestBodySpec spec = restClient
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(target);
        spec.headers(headers -> copyRequestHeaders(request, headers));
        if (client != null && client.getAccessToken() != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
        }
        // Stamp the real client address for downstream per-IP rate limiting. The BFF is the only
        // edge in this topology; if a trusted reverse proxy fronts it, use that proxy's value instead.
        spec.header("X-Forwarded-For", request.getRemoteAddr());
        if (body.length > 0) {
            spec.body(body);
        }

        return spec.exchange((req, res) -> {
            HttpHeaders responseHeaders = new HttpHeaders();
            res.getHeaders().forEach((name, values) -> {
                String lower = name.toLowerCase(Locale.ROOT);
                if (!EXCLUDED_HEADERS.contains(lower) && !lower.startsWith("access-control-")) {
                    responseHeaders.put(name, values);
                }
            });
            byte[] responseBody = res.getBody().readAllBytes();
            return ResponseEntity.status(res.getStatusCode()).headers(responseHeaders).body(responseBody);
        });
    }

    private static void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (EXCLUDED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
    }
}
