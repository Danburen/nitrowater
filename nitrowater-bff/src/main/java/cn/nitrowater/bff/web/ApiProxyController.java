package cn.nitrowater.bff.web;

import cn.nitrowater.bff.security.SessionEpochFilter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
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
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;

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
                // File-management console + plugin artifacts (resource-owned); must win over the
                // generic /api/admin/** that otherwise routes to the business API.
                || path.equals("/api/admin/resources")
                || path.startsWith("/api/admin/resources/")
                || path.equals("/api/admin/plugin-resources")
                || path.startsWith("/api/admin/plugin-resources/");
    }

    @RequestMapping("/api/**")
    public ResponseEntity<Resource> proxy(HttpServletRequest request, Authentication authentication) throws IOException {
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

        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        long contentLength = request.getContentLengthLong();

        RestClient restClient = isResourcePath(path) ? resourceRestClient : serverRestClient;
        RestClient.RequestBodySpec spec = restClient
                .method(method)
                .uri(target);
        spec.headers(headers -> ProxyHeaders.forEachForwardedHeader(request, headers::add));
        if (client != null && client.getAccessToken() != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
        }
        // Stamp the real client address for downstream per-IP rate limiting. The BFF is the only
        // edge in this topology; if a trusted reverse proxy fronts it, use that proxy's value instead.
        spec.header("X-Forwarded-For", request.getRemoteAddr());
        // Stream the request body instead of buffering it: a 64 MB jar must not be copied into the
        // BFF heap. A known Content-Length lets the JDK client send a fixed-length body (the resource
        // service requires it for uploads); an unknown length falls back to chunked encoding.
        if (contentLength != 0 && (contentLength > 0 || hasRequestBody(method))) {
            spec.body(new StreamingResource(request.getInputStream(), contentLength));
        }

        return spec.exchange((req, res) -> {
            HttpHeaders responseHeaders = new HttpHeaders();
            res.getHeaders().forEach((name, values) -> {
                // CORS is owned by this service's own security chain; a downstream must not
                // overwrite it (and set-cookie is dropped by the shared policy).
                if (ProxyHeaders.isRelayableResponseHeader(name)
                        && !name.toLowerCase(Locale.ROOT).startsWith("access-control-")) {
                    responseHeaders.put(name, values);
                }
            });
            // Returning an InputStreamResource keeps RestClient from closing the downstream
            // response before Spring MVC has streamed it to the browser (see its streaming-result
            // handling). The custom contentLength lets the converter preserve Content-Length.
            InputStream responseStream = res.getBody();
            Resource responseBody = (responseStream != null)
                    ? new StreamingResource(responseStream, res.getHeaders().getContentLength())
                    : null;
            if (res.getStatusCode().value() == 401 && request.getUserPrincipal() != null) {
                // The resource server rejected our access token: the session's credentials are dead,
                // so stop pretending we are still signed in.
                SessionEpochFilter.invalidate(request);
                responseHeaders.set("X-Auth-Reason", "upstream_unauthorized");
                responseHeaders.add(HttpHeaders.SET_COOKIE, SessionCookies.expired().toString());
            }
            return ResponseEntity.status(res.getStatusCode()).headers(responseHeaders).body(responseBody);
        });
    }

    /**
     * @param method HTTP method of the proxied request
     * @return whether the method can carry a request body when its length is not declared
     */
    private static boolean hasRequestBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    /**
     * A one-shot streaming body with a pre-computed length. {@link InputStreamResource} alone reports
     * an unknown length, which would make the JDK client use chunked encoding and would make Spring
     * MVC omit the response length; this subclass keeps the length known without reading the stream
     * twice.
     */
    private static final class StreamingResource extends InputStreamResource {

        private final long contentLength;

        private StreamingResource(InputStream inputStream, long contentLength) {
            super(inputStream);
            this.contentLength = contentLength;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }
    }
}
