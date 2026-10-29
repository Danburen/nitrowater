package cn.nitrowater.server.ai;

/**
 * One documentation collection that participates in the RAG index.
 *
 * <p>A collection is a logical corpus (for example {@code velochatx} or {@code nitrowater}) plus
 * the place its markdown sources live. The location may be a filesystem path or a
 * {@code classpath:} prefix; the latter keeps the sources inside the application jar so a packaged
 * deployment no longer depends on a sibling docs checkout.</p>
 *
 * @param corpus logical corpus name stored in every chunk's metadata
 * @param location filesystem path or {@code classpath:} location holding the markdown tree
 * @param publicBaseUrl public URL prefix of the rendered docs site, or an empty string when the
 *        corpus is markdown-only (no published site, citations carry no URL)
 */
public record DocCollection(String corpus, String location, String publicBaseUrl) {

    public DocCollection {
        corpus = requireText(corpus, "corpus");
        location = requireText(location, "location");
        // null = use the conventional /docs/<corpus>/ mount; "" = deliberately no public site.
        publicBaseUrl = publicBaseUrl == null ? "/docs/" + corpus + "/" : publicBaseUrl.trim();
        if (!publicBaseUrl.isEmpty() && !publicBaseUrl.endsWith("/")) {
            publicBaseUrl = publicBaseUrl + "/";
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("app.ai.collections[].%s must not be blank".formatted(field));
        }
        return value.trim();
    }
}
