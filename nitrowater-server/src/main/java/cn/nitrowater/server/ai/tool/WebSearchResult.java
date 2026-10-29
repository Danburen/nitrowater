package cn.nitrowater.server.ai.tool;

/**
 * One web search hit, normalized across providers.
 *
 * @param title page title
 * @param url page URL
 * @param snippet short plain-text summary
 */
public record WebSearchResult(String title, String url, String snippet) {
}
