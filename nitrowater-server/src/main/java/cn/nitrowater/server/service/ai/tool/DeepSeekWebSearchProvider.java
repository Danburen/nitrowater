package cn.nitrowater.server.service.ai.tool;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.service.ai.model.tool.WebSearchResult;
import cn.nitrowater.server.config.ai.WebSearchProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * DeepSeek web-search provider — the default.
 *
 * <p>DeepSeek exposes a standalone search API at {@code POST https://api.deepseek.com/v1/tools/search}
 * (bearer auth). The request is {@code {"query","count","need_content"}}; the response is
 * {@code {"results":[...]}} where each hit carries {@code title}, {@code url}, a short
 * {@code snippet} for display and a longer model-ready {@code summary}, plus quality signals
 * ({@code rerank_score}, {@code authority}) that we ignore for now.</p>
 *
 * <p>We send {@code need_content=false}: the page body would multiply the payload size, and the
 * summary is already the field DeepSeek recommends for models. When a hit has no summary the
 * snippet is used instead.</p>
 *
 * <p>Transient failures (I/O, timeouts, 5xx, 429) are retried once by default so a flaky call does
 * not burn the turn's search budget; client errors (400/401/403) fail immediately because the same
 * request would fail again.</p>
 */
@Component
public class DeepSeekWebSearchProvider implements WebSearchProvider {

    private static final String DEFAULT_ENDPOINT = "https://api.deepseek.com/v1/tools/search";
    /** The API accepts 1..50; the agent only ever needs a handful and pays per result. */
    private static final int MAX_COUNT = 10;

    private final WebSearchProperties properties;
    private final RestClient restClient;

    public DeepSeekWebSearchProvider(WebSearchProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofMillis(properties.effectiveTimeoutMs()));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public boolean enabled() {
        return properties.enabled() && hasText(properties.apiKey());
    }

    @Override
    public List<WebSearchResult> search(String query, int count) {
        if (!enabled()) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("count", Math.min(MAX_COUNT, Math.max(1, count)));
        body.put("need_content", false);

        int attempts = properties.effectiveMaxAttempts();
        for (int attempt = 1; ; attempt++) {
            try {
                return parse(post(body));
            } catch (Exception ex) {
                if (attempt >= attempts || !retryable(ex)) {
                    throw new IllegalStateException(
                            "deepseek web search failed after " + attempt + " attempt(s): " + message(ex), ex);
                }
                pause(properties.effectiveRetryBackoffMs());
            }
        }
    }

    private String post(Map<String, Object> body) {
        return restClient.post()
                .uri(endpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                .body(JsonUtil.toJson(body))
                .retrieve()
                .body(String.class);
    }

    private String endpoint() {
        return hasText(properties.endpoint()) ? properties.endpoint().trim() : DEFAULT_ENDPOINT;
    }

    /**
     * Parses {@code results[]}; an empty array is a normal "no matches" answer, not an error.
     */
    private static List<WebSearchResult> parse(String response) {
        if (StringUtil.isBlank(response)) {
            return List.of();
        }
        Map<String, Object> root = JsonUtil.fromJson(response, new TypeReference<Map<String, Object>>() {
        });
        if (root == null || !(root.get("results") instanceof List<?> items)) {
            return List.of();
        }
        List<WebSearchResult> results = new ArrayList<>(items.size());
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String url = first(map.get("url"), map.get("link"));
            if (url.isBlank()) {
                continue;
            }
            // `summary` is written for the model; `snippet` is the short UI preview.
            String text = first(map.get("summary"), map.get("snippet"), map.get("content"));
            results.add(new WebSearchResult(
                    first(map.get("title"), map.get("site_name"), url),
                    url,
                    text.isBlank() ? url : text));
        }
        return List.copyOf(results);
    }

    /**
     * @return whether the same request is worth repeating (network hiccup, server error, throttling)
     */
    private static boolean retryable(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return retryableOne(error) || retryableOne(root);
    }

    private static boolean retryableOne(Throwable error) {
        return error instanceof IOException
                || error instanceof HttpServerErrorException
                || error instanceof HttpClientErrorException.TooManyRequests;
    }

    private static void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while retrying deepseek web search", ex);
        }
    }

    private static String first(Object... values) {
        for (Object value : values) {
            String text = Objects.toString(value, "").trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    private static boolean hasText(String value) {
        return StringUtil.isNotBlank(value);
    }

    private static String message(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return Objects.requireNonNullElse(current.getMessage(), current.getClass().getSimpleName());
    }
}
