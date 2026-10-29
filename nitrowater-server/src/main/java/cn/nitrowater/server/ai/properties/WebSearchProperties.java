package cn.nitrowater.server.ai.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web-search configuration ({@code app.ai.web-search.*}).
 *
 * <p>DeepSeek is the only search backend: it exposes a standalone search API
 * ({@code POST /v1/tools/search}) returning {@code title/url/snippet/summary} per hit. The tool is
 * only advertised to the model when {@link #enabled} is true, an API key is present and the user
 * opted in for the turn.</p>
 *
 * @param enabled master switch; disabled means the tool is never advertised
 * @param apiKey DeepSeek API key (defaults to {@code DEEPSEEK_API_KEY})
 * @param endpoint search endpoint override; blank uses the official URL
 * @param autoFallback when {@code searchDocs} finds nothing, run one web search automatically
 * @param autoFallbackCount number of results fetched by the automatic fallback
 * @param timeoutMs HTTP read timeout
 */
@ConfigurationProperties(prefix = "app.ai.web-search")
public record WebSearchProperties(
        boolean enabled,
        String apiKey,
        String endpoint,
        boolean autoFallback,
        int autoFallbackCount,
        int timeoutMs) {
}
