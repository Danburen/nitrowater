package cn.nitrowater.bff.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

/**
 * Forwards {@code /plugin/**} to the dedicated plugin-docs container (a single nginx serving every
 * plugin's static documentation and downloads).
 *
 * <p>The BFF owns the {@code /plugin/<name>/} namespace as the single edge: it never serves those
 * files itself, it only routes to {@code app.plugin-docs.upstream} (e.g. {@code http://docs:80} in
 * compose, {@code http://localhost:8085} in dev). The docs tree is published out-of-band by each
 * docs repo's own pipeline, so neither a web build nor a BFF restart is involved.</p>
 *
 * <p>The request is forwarded verbatim (full path, query, headers minus hop-by-hop) and the
 * response is streamed straight back with its status and headers preserved — including
 * {@code Range}/{@code 206}/{@code Content-Range} for resumable downloads of the {@code res/*.jar}
 * artifacts. Mapped before the {@code /**} SPA handler, which it therefore never reaches.</p>
 */
@RestController
public class PluginDocsProxyController {

    /** Hop-by-hop / hop-specific headers that must not be copied between the two hops. */
    private static final Set<String> EXCLUDED_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length");

    private final HttpClient httpClient;
    private final String upstream;

    public PluginDocsProxyController(
            @Value("${app.plugin-docs.upstream:http://localhost:8085}") String upstream,
            @Value("${app.plugin-docs.connect-timeout-ms:3000}") long connectTimeoutMs) {
        // Never follow redirects: a directory request without a trailing slash must surface the
        // upstream's (relative) 301 to the browser, not be chased server-side.
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.upstream = upstream.endsWith("/") ? upstream.substring(0, upstream.length() - 1) : upstream;
    }

    @GetMapping("/plugin/**")
    public ResponseEntity<StreamingResponseBody> proxy(HttpServletRequest request) throws IOException {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String query = request.getQueryString();
        String target = (query == null || query.isEmpty()) ? path : path + "?" + query;

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(upstream + target))
                .method(request.getMethod(), HttpRequest.BodyPublishers.noBody());
        copyRequestHeaders(request, builder);

        HttpResponse<InputStream> response =
                send(builder.build());
        return toResponse(response);
    }

    /** @return the upstream response, wrapping the checked I/O failure as unchecked */
    private HttpResponse<InputStream> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Plugin-docs upstream unreachable: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException("Plugin-docs request interrupted: " + request.uri(),
                    new IOException(e));
        }
    }

    /** Maps the upstream response onto a streamed Spring response, preserving status + headers. */
    private static ResponseEntity<StreamingResponseBody> toResponse(HttpResponse<InputStream> response) {
        HttpHeaders headers = new HttpHeaders();
        response.headers().map().forEach((name, values) -> {
            if (!EXCLUDED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                headers.put(name, values);
            }
        });
        InputStream body = response.body();
        StreamingResponseBody stream = out -> {
            try (body) {
                body.transferTo(out);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        return ResponseEntity.status(response.statusCode()).headers(headers).body(stream);
    }

    private static void copyRequestHeaders(HttpServletRequest request, HttpRequest.Builder builder) {
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (EXCLUDED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                builder.header(name, values.nextElement());
            }
        }
    }
}
