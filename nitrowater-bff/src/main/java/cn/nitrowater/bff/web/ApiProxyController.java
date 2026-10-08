package cn.nitrowater.bff.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

/**
 * Proxies {@code /api/**} to the resource server, attaching a server-held Bearer access token.
 *
 * <p>The access token is obtained through {@link OAuth2AuthorizedClientManager}, so it is
 * refreshed with the refresh token when expired — the SPA never deals with tokens at all.</p>
 */
@RestController
public class ApiProxyController {

    /** Headers that must not be copied verbatim between the two hops. */
    private static final Set<String> EXCLUDED_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host",
            "content-length", "authorization", "cookie");

    private final RestClient restClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String registrationId;

    public ApiProxyController(
            @Value("${app.rs.base-url}") String rsBaseUrl,
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${app.oidc.registration-id:nitrowater}") String registrationId) {
        this.restClient = RestClient.builder().baseUrl(rsBaseUrl).build();
        this.authorizedClientManager = authorizedClientManager;
        this.registrationId = registrationId;
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, Authentication authentication) throws IOException {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String query = request.getQueryString();
        String target = query == null || query.isEmpty() ? path : path + "?" + query;

        OAuth2AuthorizedClient client = authorizedClientManager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                        .principal(authentication)
                        .build());

        byte[] body = request.getInputStream().readAllBytes();

        RestClient.RequestBodySpec spec = restClient
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(target);
        spec.headers(headers -> copyRequestHeaders(request, headers));
        if (client != null && client.getAccessToken() != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
        }
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
