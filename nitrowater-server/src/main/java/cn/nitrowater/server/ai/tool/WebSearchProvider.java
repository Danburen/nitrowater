package cn.nitrowater.server.ai.tool;

import java.util.List;

/**
 * SPI for the optional web-search tool.
 *
 * <p>{@link DeepSeekWebSearchProvider} is the only implementation (DeepSeek's search API). The SPI
 * stays so another backend can be added without touching the agent.</p>
 */
public interface WebSearchProvider {

    /** @return whether the tool may be advertised to the model */
    boolean enabled();

    /**
     * @param query search query (already trimmed)
     * @param count maximum results
     * @return normalized hits, never {@code null}
     */
    List<WebSearchResult> search(String query, int count);
}
