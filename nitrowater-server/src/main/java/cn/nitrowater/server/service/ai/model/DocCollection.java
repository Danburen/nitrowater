package cn.nitrowater.server.service.ai.model;

import cn.nitrowater.lib.utils.StringUtil;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * One documentation collection that participates in the RAG index.
 *
 * <p>A collection is a logical corpus (for example {@code velochatx} or {@code nitrowater}) plus
 * the place its markdown sources live. The location may be a filesystem path or a
 * {@code classpath:} prefix; the latter keeps the sources inside the application jar so a packaged
 * deployment no longer depends on a sibling docs checkout.</p>
 *
 * <p>{@code description} is what the agent sees when it decides whether to search this corpus, so
 * it should read like "what this documentation covers" rather than repeat the corpus name.</p>
 *
 * @param corpus logical corpus name stored in every chunk's metadata
 * @param location filesystem path or {@code classpath:} location holding the markdown tree
 * @param publicBaseUrl public URL prefix of the rendered docs site, or an empty string when the
 *        corpus is markdown-only (no published site, citations carry no URL)
 * @param description one-line human summary shown to the agent; empty when the deployment does not
 *        provide one
 */
public record DocCollection(String corpus, String location, String publicBaseUrl, String description) {

    /**
     * Backwards-compatible constructor for callers that do not describe their corpus.
     *
     * @param corpus logical corpus name
     * @param location markdown location
     * @param publicBaseUrl public URL prefix, or an empty string
     */
    public DocCollection(String corpus, String location, String publicBaseUrl) {
        this(corpus, location, publicBaseUrl, "");
    }
    @ConstructorBinding
    public DocCollection {
        corpus = StringUtil.requireText(corpus, "app.ai.collections[].corpus");
        location = StringUtil.requireText(location, "app.ai.collections[].location");
        // null = use the conventional /docs/<corpus>/ mount; "" = deliberately no public site.
        publicBaseUrl = publicBaseUrl == null ? "/docs/" + corpus + "/" : publicBaseUrl.trim();
        if (!publicBaseUrl.isEmpty() && !publicBaseUrl.endsWith("/")) {
            publicBaseUrl = publicBaseUrl + "/";
        }
        description = description == null ? "" : description.trim();
    }
}
