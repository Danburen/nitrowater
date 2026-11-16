package cn.nitrowater.server.service.ai.rag;

import cn.nitrowater.server.service.ai.model.rag.DocCollection;
import cn.nitrowater.server.service.ai.model.rag.IndexStatus;
import cn.nitrowater.server.config.ai.AiProperties;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Common;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingProperties;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Owns the documentation vector index: collection bootstrap, full re-index and status reporting.
 *
 * <p>Re-indexing is a destructive replace of every discovered corpus: each corpus's points are
 * deleted and the freshly chunked documents (deterministic ids) are embedded and inserted, after
 * which the {@code ai_doc_index} inventory is rewritten from the same run. The corpora are small, so
 * a full rebuild is simpler and safer than incremental diffs.</p>
 *
 * <p>Ingestion never runs on the request thread at startup: when enabled it is checked in the
 * background after the application is ready, and any failure (Qdrant down, embedding key missing)
 * is reported through {@link #status()} instead of failing the boot.</p>
 */
@Service
public class DocsIndexService {

    private static final Logger log = LoggerFactory.getLogger(DocsIndexService.class);

    private final AiProperties properties;
    private final DocCorpusRegistry registry;
    private final DocChunkBuilder chunkBuilder;
    private final DocsIndexInventoryService inventory;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final QdrantClient qdrantClient;
    private final QdrantVectorStoreProperties qdrantProperties;
    private final OpenAiEmbeddingProperties embeddingProperties;

    private final Object lock = new Object();
    private final AtomicBoolean indexing = new AtomicBoolean(false);
    private volatile IndexStatus status = IndexStatus.empty();

    public DocsIndexService(AiProperties properties, DocCorpusRegistry registry,
            DocChunkBuilder chunkBuilder, DocsIndexInventoryService inventory, VectorStore vectorStore,
            EmbeddingModel embeddingModel, QdrantClient qdrantClient,
            QdrantVectorStoreProperties qdrantProperties,
            OpenAiEmbeddingProperties embeddingProperties) {
        this.properties = properties;
        this.registry = registry;
        this.chunkBuilder = chunkBuilder;
        this.inventory = inventory;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.qdrantClient = qdrantClient;
        this.qdrantProperties = qdrantProperties;
        this.embeddingProperties = embeddingProperties;
    }

    /** @return the last known index state (never throws) */
    public IndexStatus status() {
        return status;
    }

    /** @return whether a re-index is currently running */
    public boolean indexing() {
        return indexing.get();
    }

    /**
     * Rebuilds the corpus index.
     *
     * @return the resulting status
     * @throws ResponseStatusException 503 when the assistant is disabled, 409 when a rebuild is
     *         already running
     */
    public IndexStatus reindex() {
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI assistant is disabled");
        }
        if (!indexing.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A re-index is already running");
        }
        synchronized (lock) {
            long startedAt = System.nanoTime();
            try {
                // Re-scan first: a content-only deploy may have added, changed or removed corpora,
                // and the index must follow the mount, not a startup snapshot.
                List<DocCollection> collections = registry.reload();
                DocChunkBuilder.Result result = chunkBuilder.build(collections);
                if (result.chunks().isEmpty()) {
                    long durationMs = elapsedMillis(startedAt);
                    String message = "No markdown sources found under the documentation root "
                            + (registry.root() == null ? "(not configured)" : registry.root());
                    status = new IndexStatus(false, 0, 0, null, durationMs, message);
                    log.warn("AI index rebuild skipped: {}", message);
                    return status;
                }
                ensureCollection();
                deleteCorpora(collections);
                vectorStore.add(result.chunks());
                long durationMs = elapsedMillis(startedAt);
                status = new IndexStatus(true, result.files(), result.chunks().size(), Instant.now(),
                        durationMs, null);
                inventory.replaceAll(result.docs(), status.indexedAt());
                log.info("AI index rebuilt: {} files from {} corpora, {} chunks in {} ms",
                        result.files(), collections.size(), result.chunks().size(), durationMs);
            } catch (Exception ex) {
                long durationMs = elapsedMillis(startedAt);
                String message = rootMessage(ex);
                status = new IndexStatus(false, 0, 0, null, durationMs, message);
                log.warn("AI index rebuild failed: {}", message, ex);
            } finally {
                indexing.set(false);
            }
            return status;
        }
    }

    /**
     * On startup, bootstrap the index when the collection is empty or drifted.
     *
     * <p>"Drifted" means the mount no longer matches what was embedded: a new, edited or deleted
     * markdown file, or a corpus that was added/removed. Without that check a content-only deploy
     * would keep answering from the previous documentation, which is the failure mode this whole
     * out-of-image corpus design has to avoid.</p>
     *
     * <p>The check runs off the boot thread; a missing Qdrant or embedding key only degrades the
     * assistant, it never prevents the business API from serving.</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapOnStartup() {
        if (!properties.enabled() || !properties.ingestOnStartup()) {
            return;
        }
        Thread.ofVirtual().name("ai-index-bootstrap").start(() -> {
            try {
                boolean exists = qdrantClient.collectionExistsAsync(collectionName()).get();
                if (exists && allCorporaIndexed() && !inventory.drifted()) {
                    long points = qdrantClient.countAsync(collectionName()).get();
                    status = new IndexStatus(true, status.files(), (int) Math.min(Integer.MAX_VALUE, points),
                            status.indexedAt(), 0L, null);
                    log.info("AI index already holds all configured corpora ({} chunks); skipping startup ingest",
                            points);
                } else {
                    reindex();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.warn("AI index bootstrap skipped: {}", rootMessage(ex));
            }
        });
    }

    /**
     * @return whether every discovered corpus has at least one point in the shared collection
     * @throws Exception when Qdrant cannot be queried
     */
    private boolean allCorporaIndexed() throws Exception {
        List<DocCollection> collections = registry.collections();
        if (collections.isEmpty()) {
            return false;
        }
        for (DocCollection collection : collections) {
            Common.Filter filter = Common.Filter.newBuilder()
                    .addMust(ConditionFactory.matchKeyword(DocChunkBuilder.META_CORPUS, collection.corpus()))
                    .build();
            long points = qdrantClient.countAsync(collectionName(), filter, true).get();
            if (points == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the collection name configured for the Spring AI Qdrant store, so collection
     *         bootstrap and the vector-store bean always agree
     */
    public String collectionName() {
        return qdrantProperties.getCollectionName();
    }

    /**
     * Creates the collection when missing. Must run before the first add/delete because the Spring
     * AI auto-configuration is configured with {@code initialize-schema=false} (so a missing Qdrant
     * does not break application startup).
     */
    private void ensureCollection() throws Exception {
        if (qdrantClient.collectionExistsAsync(collectionName()).get()) {
            return;
        }
        VectorParams params = VectorParams.newBuilder()
                .setDistance(Distance.Cosine)
                .setSize(embeddingDimensions())
                .build();
        qdrantClient.createCollectionAsync(collectionName(), params).get();
        log.info("Created Qdrant collection '{}'", collectionName());
    }

    /**
     * @return the vector size to create the collection with. Prefers the configured embedding
     *         dimensions (no network call); falls back to probing the model only when unset.
     */
    private int embeddingDimensions() {
        Integer configured = embeddingProperties.getDimensions();
        return configured != null && configured > 0 ? configured : embeddingModel.dimensions();
    }

    /**
     * Removes every point of the corpora that are being re-indexed, plus the corpora that vanished
     * from the mount but still have indexed rows (deleting a corpus directory must also delete its
     * vectors, otherwise the agent keeps citing documents that no longer exist).
     *
     * @param collections corpora currently discovered on disk
     */
    private void deleteCorpora(List<DocCollection> collections) {
        Set<String> corpora = new LinkedHashSet<>();
        for (DocCollection collection : collections) {
            corpora.add(collection.corpus());
        }
        corpora.addAll(inventory.indexedCorpora());
        for (String corpus : corpora) {
            Filter.Expression filter = new FilterExpressionBuilder()
                    .eq(DocChunkBuilder.META_CORPUS, corpus)
                    .build();
            vectorStore.delete(filter);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return StringUtil.isBlank(message) ? current.getClass().getSimpleName() : message;
    }

}
