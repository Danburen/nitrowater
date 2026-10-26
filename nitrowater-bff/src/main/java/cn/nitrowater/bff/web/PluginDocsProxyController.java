package cn.nitrowater.bff.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Forwards {@code /plugin/**} to the dedicated plugin-docs container (a single nginx serving every
 * plugin's static documentation). Downloadable jars are NOT part of this namespace: they are
 * served by the unified resource service ({@code nitrowater-resource}) under
 * {@code /api/public/resource/**}.
 *
 * <p>The BFF owns the {@code /plugin/<name>/} namespace as the single edge: it never serves those
 * files itself, it only routes to {@code app.plugin-docs.upstream} (e.g. {@code http://docs:80} in
 * compose, {@code http://localhost:8085} in dev). The docs tree is published out-of-band by each
 * docs repo's own pipeline, so neither a web build nor a BFF restart is involved.</p>
 *
 * <p>The request is forwarded verbatim (full path, query, headers minus hop-by-hop) and the
 * response is streamed straight back with its status and headers preserved — including
 * {@code Range}/{@code 206}/{@code Content-Range} passthrough for resumable file downloads.
 * Mapped before the {@code /**} SPA handler, which it therefore never reaches.</p>
 */
@RestController
public class PluginDocsProxyController {

    /** Friendly maintenance page returned when the docs container cannot be reached (503). */
    private static final byte[] UNAVAILABLE_HTML = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>文档站暂时不可用</title>
              <style>
                :root { color-scheme: light dark; }
                body { margin: 0; min-height: 100vh; display: grid; place-items: center;
                       font: 15px/1.7 system-ui, -apple-system, "Segoe UI", "Noto Sans SC", sans-serif;
                       background: #f7f8fa; color: #1f2430; }
                .card { max-width: 460px; padding: 32px 36px; border: 1px solid #e3e6ec;
                        border-radius: 14px; background: #fff; }
                h1 { margin: 0 0 10px; font-size: 18px; }
                p { margin: 0 0 8px; color: #5b6472; }
                code { font-family: ui-monospace, Consolas, monospace; font-size: 13px; color: #1f2430; }
                a { color: #2563eb; }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>文档站暂时不可用</h1>
                <p>插件文档服务当前无法访问，请稍后重试。</p>
                <p><small>本地预览：启动 <code>deploy/docker</code> 的 <code>docs</code> 容器（nginx，挂载 <code>deploy/plugin-docs</code>）。</small></p>
                <p><a href="/plugins">← 返回插件中心</a></p>
              </div>
            </body>
            </html>
            """.getBytes(StandardCharsets.UTF_8);

    private static final Logger log = LoggerFactory.getLogger(PluginDocsProxyController.class);

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
        ProxyHeaders.forEachForwardedHeader(request, builder::header);

        HttpResponse<InputStream> response;
        try {
            response = send(builder.build());
        } catch (UncheckedIOException e) {
            // Docs container down/unreachable: degrade to a friendly page rather than an opaque 500.
            log.warn("Plugin-docs upstream unreachable, serving 503: {}", e.toString());
            return unavailable();
        }
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
            if (ProxyHeaders.isRelayableResponseHeader(name)) {
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

    /**
     * Self-contained 503 page for the {@code /plugin/**} namespace when the docs container cannot be
     * reached, so a down docs deployment surfaces as "temporarily unavailable" (with a short
     * {@code Retry-After}) instead of a whitelabel 500.
     *
     * @return a 503 response streaming {@link #UNAVAILABLE_HTML}
     */
    private static ResponseEntity<StreamingResponseBody> unavailable() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "html", StandardCharsets.UTF_8));
        headers.setCacheControl("no-store");
        headers.set(HttpHeaders.RETRY_AFTER, "30");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .headers(headers)
                .body(out -> out.write(UNAVAILABLE_HTML));
    }
}
