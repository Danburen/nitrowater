package cn.nitrowater.server.service.ai.model;

/**
 * A citation returned with an answer.
 *
 * @param index 1-based number used in the answer text ({@code [1]})
 * @param kind {@code doc} for a local documentation chunk, {@code web} for a web result
 * @param title document/page title
 * @param section heading of the chunk (empty for web results)
 * @param path repository-relative markdown path (empty for web results)
 * @param url rendered documentation page URL, or the external page URL
 * @param score cosine similarity (0 for web results)
 * @param snippet short plain-text preview of the chunk
 */
public record DocSource(
        int index,
        String kind,
        String title,
        String section,
        String path,
        String url,
        double score,
        String snippet) {

    /** @return whether this citation points at a local documentation page */
    public boolean isDoc() {
        return "doc".equals(kind);
    }
}
