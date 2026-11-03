package cn.nitrowater.server.service.ai.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web-search configuration ({@code app.ai.web-search.*}).
 *
 * <p>DeepSeek is the only search backend: it exposes a standalone search API
 * ({@code POST /v1/tools/search}) returning {@code title/url/snippet/summary} per hit. The tool is
 * only advertised to the model when {@link #enabled} is true, an API key is present and the user
 * opted in for the turn.</p>
 *
 * <p>Search volume is bounded on three axes: how many hits one call returns ({@link #maxResults}),
 * how many calls succeed per turn across every path ({@link #maxSuccesses}, shared by the explicit
 * tool and the {@code searchDocs} fallback), and how many HTTP attempts one call may spend
 * ({@link #maxAttempts}). Failed attempts neither consume quota nor count as a successful search.</p>
 *
 * @param enabled master switch; disabled means the tool is never advertised
 * @param apiKey DeepSeek API key (defaults to {@code DEEPSEEK_API_KEY})
 * @param endpoint search endpoint override; blank uses the official URL
 * @param autoFallback when {@code searchDocs} finds nothing, run one web search automatically
 * @param maxResults hits requested per call (explicit search and fallback share this value)
 * @param maxSuccesses successful searches allowed per turn (failures do not count)
 * @param maxAttempts HTTP attempts per call, including the first try
 * @param retryBackoffMs pause between attempts when a failure is retryable
 * @param timeoutMs HTTP read timeout
 */
@ConfigurationProperties(prefix = "app.ai.web-search")
public record WebSearchProperties(
        boolean enabled,
        String apiKey,
        String endpoint,
        boolean autoFallback,
        int maxResults,
        int maxSuccesses,
        int maxAttempts,
        int retryBackoffMs,
        int timeoutMs) {

    /** Exposes at most {@link #MAX_RESULTS_CAP} hits, because DeepSeek accepts 1..50 but tokens are linear. */
    private static final int MAX_RESULTS_CAP = 10;
    private static final int DEFAULT_MAX_RESULTS = 3;
    private static final int DEFAULT_MAX_SUCCESSES = 3;
    private static final int DEFAULT_MAX_ATTEMPTS = 2;

    /** @return hits to request per call, clamped to {@code [1, 10]} with a default of 3 */
    public int effectiveMaxResults() {
        int value = maxResults > 0 ? maxResults : DEFAULT_MAX_RESULTS;
        return Math.min(MAX_RESULTS_CAP, value);
    }

    /** @return successful searches allowed per turn; a non-positive value means the default of 3 */
    public int effectiveMaxSuccesses() {
        return maxSuccesses > 0 ? maxSuccesses : DEFAULT_MAX_SUCCESSES;
    }

    /** @return HTTP attempts per call; a non-positive value means the default of 2 (first try + one retry) */
    public int effectiveMaxAttempts() {
        return maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
    }

    /** @return pause between retryable attempts in milliseconds; negative values fall back to 300 ms */
    public long effectiveRetryBackoffMs() {
        return retryBackoffMs >= 0 ? retryBackoffMs : 300L;
    }

    /** @return read timeout in milliseconds; a non-positive value means the default of 20 s */
    public int effectiveTimeoutMs() {
        return timeoutMs > 0 ? timeoutMs : 20_000;
    }
}
