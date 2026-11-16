package cn.nitrowater.server.service.ai.model.rag;

import cn.nitrowater.lib.utils.StringUtil;

/**
 * One documentation collection that participates in the RAG index.
 *
 * <p>A collection is a logical corpus (for example {@code velochatx} or {@code nitrowater}) plus
 * the directory its markdown sources live in. Collections are discovered at runtime: every
 * sub-directory of {@code app.ai.docs-root} is one corpus, so adding documentation never touches
 * Java or {@code application.yml}.</p>
 *
 * <p>{@code description} is what the agent sees when it decides whether to search this corpus, so
 * it should read like "what this documentation covers" rather than repeat the corpus name.</p>
 *
 * @param corpus logical corpus name stored in every chunk's metadata
 * @param location filesystem directory holding the markdown tree
 * @param publicBaseUrl public URL prefix of the rendered docs site, or an empty string when the
 *        corpus is markdown-only (no published site, citations carry no URL)
 * @param description one-line human summary shown to the agent; empty when the corpus does not
 *        provide a {@code _corpus.yml} with a description
 */
public record DocCollection(String corpus, String location, String publicBaseUrl, String description) {

    /**
     * Backwards-compatible constructor for callers that do not describe their corpus.
     *
     * @param corpus logical corpus name
     * @param location markdown directory
     * @param publicBaseUrl public URL prefix, or an empty string
     */
    public DocCollection(String corpus, String location, String publicBaseUrl) {
        this(corpus, location, publicBaseUrl, "");
    }

    public DocCollection {
        corpus = StringUtil.requireText(corpus, "docs corpora[].corpus");
        location = StringUtil.requireText(location, "docs corpora[].location");
        // null = use the conventional /docs/<corpus>/ mount; "" = deliberately no public site.
        publicBaseUrl = publicBaseUrl == null ? "/docs/" + corpus + "/" : publicBaseUrl.trim();
        if (!publicBaseUrl.isEmpty() && !publicBaseUrl.endsWith("/")) {
            publicBaseUrl = publicBaseUrl + "/";
        }
        description = description == null ? "" : description.trim();
    }
}
