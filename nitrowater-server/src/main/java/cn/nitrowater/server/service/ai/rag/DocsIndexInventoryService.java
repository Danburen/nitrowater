package cn.nitrowater.server.service.ai.rag;

import cn.nitrowater.server.infra.entity.AiDocIndex;
import cn.nitrowater.server.infra.repository.AiDocIndexRepository;
import cn.nitrowater.server.service.ai.model.rag.CorpusIndexView;
import cn.nitrowater.server.service.ai.model.rag.DocCollection;
import cn.nitrowater.server.service.ai.model.rag.DocIndexStatus;
import cn.nitrowater.server.service.ai.model.rag.IndexedDocView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges the corpus mount with the {@code ai_doc_index} table into the admin index inventory.
 *
 * <p>Two independent sources answer two different questions: the filesystem says what the
 * documentation <em>is</em> right now, the table says what was <em>embedded</em>. Joining them by
 * corpus + path produces the four states in {@link DocIndexStatus}, which is exactly what an
 * operator needs after a content-only deploy ("did my new page actually reach the vector store?").</p>
 *
 * <p>Listing the mount means reading and hashing the markdown every time the report is requested.
 * That is cheap for a documentation tree (kilobytes, not megabytes) and it keeps the report honest:
 * a cached inventory would be a third copy of the truth.</p>
 */
@Service
public class DocsIndexInventoryService {

    private static final Logger log = LoggerFactory.getLogger(DocsIndexInventoryService.class);
    private static final int MAX_PAGE_SIZE = 200;

    private final DocCorpusRegistry registry;
    private final DocChunkBuilder chunkBuilder;
    private final AiDocIndexRepository repository;

    public DocsIndexInventoryService(DocCorpusRegistry registry, DocChunkBuilder chunkBuilder,
            AiDocIndexRepository repository) {
        this.registry = registry;
        this.chunkBuilder = chunkBuilder;
        this.repository = repository;
    }

    /**
     * @return per-corpus roll-up, discovered corpora first and index-only corpora last
     */
    public List<CorpusIndexView> corpora() {
        List<IndexedDocView> documents = merged();
        Map<String, List<IndexedDocView>> byCorpus = new LinkedHashMap<>();
        for (IndexedDocView document : documents) {
            byCorpus.computeIfAbsent(document.corpus(), key -> new ArrayList<>()).add(document);
        }
        List<CorpusIndexView> views = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DocCollection collection : registry.collections()) {
            seen.add(collection.corpus());
            views.add(view(collection.corpus(), collection.description(), collection.location(),
                    byCorpus.getOrDefault(collection.corpus(), List.of())));
        }
        for (Map.Entry<String, List<IndexedDocView>> entry : byCorpus.entrySet()) {
            if (!seen.contains(entry.getKey())) {
                views.add(view(entry.getKey(), "", "", entry.getValue()));
            }
        }
        return List.copyOf(views);
    }

    /**
     * @param corpus optional corpus filter (blank = every corpus)
     * @param status optional status filter; unknown values are ignored
     * @param page zero-based page index
     * @param size page size (clamped to 1..200)
     * @return one page of the merged document inventory
     */
    public Page<IndexedDocView> documents(String corpus, DocIndexStatus status, int page, int size) {
        String corpusFilter = corpus == null ? "" : corpus.trim();
        List<IndexedDocView> filtered = merged().stream()
                .filter(document -> corpusFilter.isEmpty() || corpusFilter.equals(document.corpus()))
                .filter(document -> status == null || status == document.status())
                .toList();
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        int safePage = Math.max(0, page);
        int from = Math.min(filtered.size(), safePage * safeSize);
        int to = Math.min(filtered.size(), from + safeSize);
        return new PageImpl<>(filtered.subList(from, to), PageRequest.of(safePage, safeSize),
                filtered.size());
    }

    /**
     * @return whether any document is stale, missing or orphaned, i.e. a rebuild would change the
     *         index
     */
    public boolean drifted() {
        return merged().stream().anyMatch(document -> document.status() != DocIndexStatus.INDEXED);
    }

    /**
     * @return corpora that currently have at least one indexed document, including corpora whose
     *         directory has already been removed (their points still need deleting)
     */
    public Set<String> indexedCorpora() {
        Set<String> corpora = new LinkedHashSet<>();
        for (AiDocIndex row : repository.findAll()) {
            corpora.add(row.getCorpus());
        }
        return corpora;
    }

    /**
     * Replaces the whole inventory with the documents of a successful index run.
     *
     * @param docs one entry per embedded markdown file
     * @param indexedAt completion time of the run
     */
    @Transactional
    public void replaceAll(List<DocChunkBuilder.DocStat> docs, Instant indexedAt) {
        repository.deleteAllInBatch();
        if (docs == null || docs.isEmpty()) {
            return;
        }
        List<AiDocIndex> rows = new ArrayList<>(docs.size());
        for (DocChunkBuilder.DocStat doc : docs) {
            AiDocIndex row = new AiDocIndex();
            row.setCorpus(doc.corpus());
            row.setDocPath(doc.docPath());
            row.setDocTitle(doc.title());
            row.setLang(doc.lang());
            row.setContentSha(doc.sha256());
            row.setChunks(doc.chunks());
            row.setIndexedAt(indexedAt);
            rows.add(row);
        }
        repository.saveAll(rows);
    }

    /**
     * Joins the mount with the index rows.
     *
     * @return one entry per document, sorted by corpus then path
     */
    private List<IndexedDocView> merged() {
        Map<String, AiDocIndex> indexed = new HashMap<>();
        for (AiDocIndex row : repository.findAll()) {
            indexed.put(key(row.getCorpus(), row.getDocPath()), row);
        }
        List<IndexedDocView> views = new ArrayList<>();
        Set<String> onDisk = new HashSet<>();
        for (DocChunkBuilder.DocStat source : scan()) {
            String key = key(source.corpus(), source.docPath());
            onDisk.add(key);
            AiDocIndex row = indexed.get(key);
            if (row == null) {
                views.add(new IndexedDocView(source.corpus(), source.docPath(), source.title(),
                        source.lang(), DocIndexStatus.MISSING, 0, null, null));
            } else if (!source.sha256().equals(row.getContentSha())) {
                views.add(new IndexedDocView(source.corpus(), source.docPath(), source.title(),
                        source.lang(), DocIndexStatus.STALE, row.getChunks(), row.getContentSha(),
                        row.getIndexedAt()));
            } else {
                views.add(new IndexedDocView(source.corpus(), source.docPath(), source.title(),
                        source.lang(), DocIndexStatus.INDEXED, row.getChunks(), row.getContentSha(),
                        row.getIndexedAt()));
            }
        }
        for (AiDocIndex row : indexed.values()) {
            if (!onDisk.contains(key(row.getCorpus(), row.getDocPath()))) {
                views.add(new IndexedDocView(row.getCorpus(), row.getDocPath(), row.getDocTitle(),
                        row.getLang(), DocIndexStatus.ORPHAN, row.getChunks(), row.getContentSha(),
                        row.getIndexedAt()));
            }
        }
        views.sort(Comparator.comparing(IndexedDocView::corpus)
                .thenComparing(IndexedDocView::docPath));
        return List.copyOf(views);
    }

    private List<DocChunkBuilder.DocStat> scan() {
        try {
            return chunkBuilder.scan();
        } catch (IOException ex) {
            log.warn("Cannot read the documentation mount for the index inventory: {}", ex.getMessage());
            return List.of();
        }
    }

    private static CorpusIndexView view(String corpus, String description, String sourcePath,
            List<IndexedDocView> documents) {
        int chunks = 0;
        int indexed = 0;
        int stale = 0;
        int missing = 0;
        int orphan = 0;
        Instant indexedAt = null;
        for (IndexedDocView document : documents) {
            chunks += document.chunks();
            switch (document.status()) {
                case INDEXED -> indexed++;
                case STALE -> stale++;
                case MISSING -> missing++;
                case ORPHAN -> orphan++;
            }
            if (document.indexedAt() != null
                    && (indexedAt == null || document.indexedAt().isAfter(indexedAt))) {
                indexedAt = document.indexedAt();
            }
        }
        return new CorpusIndexView(corpus, description, sourcePath, documents.size(), chunks,
                indexedAt, indexed, stale, missing, orphan);
    }

    private static String key(String corpus, String docPath) {
        return corpus + "\u0000" + docPath;
    }
}
