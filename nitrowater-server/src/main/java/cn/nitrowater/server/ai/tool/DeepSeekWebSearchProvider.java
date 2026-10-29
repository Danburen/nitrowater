package cn.nitrowater.server.ai.tool;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.ai.properties.WebSearchProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        int timeoutMs = properties.timeoutMs() > 0 ? properties.timeoutMs() : 20_000;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
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

        String response;
        try {
            response = restClient.post()
                    .uri(endpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .body(JsonUtil.toJson(body))
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            throw new IllegalStateException("deepseek web search failed: " + message(ex), ex);
        }
        return parse(response);
    }

    private String endpoint() {
        return hasText(properties.endpoint()) ? properties.endpoint().trim() : DEFAULT_ENDPOINT;
    }

    /**
     * Parses {@code results[]}; an empty array is a normal "no matches" answer, not an error.
     */
    private static List<WebSearchResult> parse(String response) {
        if (response == null || response.isBlank()) {
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
        return value != null && !value.isBlank();
    }

    private static String message(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return Objects.requireNonNullElse(current.getMessage(), current.getClass().getSimpleName());
    }
}
