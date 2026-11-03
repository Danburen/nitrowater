package cn.nitrowater.server.service.ai;

import cn.nitrowater.server.service.ai.model.DocCollection;
import cn.nitrowater.server.service.ai.properties.AiProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One retrieval path for the plain chat endpoint and the agent's {@code searchDocs} tool.
 *
 * <p>The corpus filter keeps the search confined to the configured documentation collections even
 * when a Qdrant collection is shared by several corpora. Failures propagate; callers decide whether
 * to map them to a 503 (chat) or to a tool error result (agent).</p>
 */
@Component
public class DocRetriever {

    private static final int MIN_TOP_K = 1;
    private static final int MAX_TOP_K = 20;

    private final VectorStore vectorStore;
    private final AiProperties properties;

    public DocRetriever(VectorStore vectorStore, AiProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /**
     * Searches every configured documentation collection.
     *
     * @param query user/tool query
     * @param topK requested result count (clamped to 1..20)
     * @param similarityThreshold minimum cosine similarity
     * @return matching chunks in relevance order
     */
    public List<Document> search(String query, int topK, double similarityThreshold) {
        Set<String> corpora = new LinkedHashSet<>();
        for (DocCollection collection : properties.collections()) {
            corpora.add(collection.corpus());
        }
        return search(query, topK, similarityThreshold, List.copyOf(corpora));
    }

    /**
     * @param query user/tool query
     * @param topK requested result count (clamped to 1..20)
     * @param similarityThreshold minimum cosine similarity
     * @param corpora logical corpora to search; an empty list returns no results
     * @return matching chunks in relevance order
     */
    public List<Document> search(String query, int topK, double similarityThreshold, List<String> corpora) {
        if (corpora == null || corpora.isEmpty()) {
            return List.of();
        }
        Filter.Expression filter = new FilterExpressionBuilder()
                .in(DocChunkBuilder.META_CORPUS, corpora.toArray())
                .build();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(Math.min(MAX_TOP_K, Math.max(MIN_TOP_K, topK)))
                .similarityThreshold(similarityThreshold)
                .filterExpression(filter)
                .build();
        List<Document> found = vectorStore.similaritySearch(request);
        return found == null ? List.of() : found;
    }
}
