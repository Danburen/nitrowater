package cn.nitrowater.server.service.ai.rag;

import cn.nitrowater.server.infra.entity.AiDocIndex;
import cn.nitrowater.server.infra.repository.AiDocIndexRepository;
import cn.nitrowater.server.service.ai.model.rag.CorpusIndexView;
import cn.nitrowater.server.service.ai.model.rag.DocCollection;
import cn.nitrowater.server.service.ai.model.rag.DocIndexStatus;
import cn.nitrowater.server.service.ai.model.rag.IndexedDocView;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies the four states the index inventory derives from the mount + {@code ai_doc_index}. */
class DocsIndexInventoryServiceTest {

    private static final Instant INDEXED_AT = Instant.parse("2026-11-16T00:00:00Z");
    private static final String SHA_OLD = "a".repeat(64);
    private static final String SHA_NEW = "b".repeat(64);
    private static final String SHA_OTHER = "c".repeat(64);

    private final DocCorpusRegistry registry = mock(DocCorpusRegistry.class);
    private final DocChunkBuilder chunkBuilder = mock(DocChunkBuilder.class);
    private final AiDocIndexRepository repository = mock(AiDocIndexRepository.class);

    private final DocsIndexInventoryService service =
            new DocsIndexInventoryService(registry, chunkBuilder, repository);

    @Test
    void classifiesDocumentsAgainstTheMount() throws IOException {
        when(registry.collections()).thenReturn(List.of(corpus("nitrowater")));
        when(chunkBuilder.scan()).thenReturn(List.of(
                stat("nitrowater", "index.md", SHA_OLD),
                stat("nitrowater", "changed.md", SHA_NEW),
                stat("nitrowater", "new.md", SHA_OTHER)));
        when(repository.findAll()).thenReturn(List.of(
                row("nitrowater", "index.md", SHA_OLD),
                row("nitrowater", "changed.md", SHA_OLD),
                row("nitrowater", "gone.md", SHA_OTHER)));

        assertThat(service.documents("", null, 0, 50).getContent())
                .extracting(doc -> doc.docPath() + ":" + doc.status())
                .containsExactly(
                        "changed.md:STALE",
                        "gone.md:ORPHAN",
                        "index.md:INDEXED",
                        "new.md:MISSING");

        assertThat(service.drifted()).isTrue();
    }

    @Test
    void reportsPerCorpusCountsAndChunks() throws IOException {
        when(registry.collections()).thenReturn(List.of(corpus("nitrowater")));
        when(chunkBuilder.scan()).thenReturn(List.of(stat("nitrowater", "index.md", SHA_OLD)));
        when(repository.findAll()).thenReturn(List.of(row("nitrowater", "index.md", SHA_OLD)));

        List<CorpusIndexView> corpora = service.corpora();

        assertThat(corpora).hasSize(1);
        CorpusIndexView view = corpora.getFirst();
        assertThat(view.corpus()).isEqualTo("nitrowater");
        assertThat(view.description()).isEqualTo("platform docs");
        assertThat(view.sourcePath()).isEqualTo("/srv/docs/nitrowater");
        assertThat(view.documents()).isEqualTo(1);
        assertThat(view.chunks()).isEqualTo(3);
        assertThat(view.indexed()).isEqualTo(1);
        assertThat(view.stale()).isZero();
        assertThat(view.missing()).isZero();
        assertThat(view.orphan()).isZero();
        assertThat(view.indexedAt()).isEqualTo(INDEXED_AT);
        assertThat(service.drifted()).isFalse();
    }

    @Test
    void filtersByCorpusAndStatus() throws IOException {
        when(registry.collections()).thenReturn(List.of(corpus("nitrowater"), corpus("velochatx")));
        when(chunkBuilder.scan()).thenReturn(List.of(
                stat("nitrowater", "index.md", SHA_NEW),
                stat("velochatx", "index.md", SHA_OLD)));
        when(repository.findAll()).thenReturn(List.of(
                row("nitrowater", "index.md", SHA_OLD),
                row("velochatx", "index.md", SHA_OLD)));

        Page<IndexedDocView> stale = service.documents("nitrowater", DocIndexStatus.STALE, 0, 50);

        assertThat(stale.getTotalElements()).isEqualTo(1);
        assertThat(stale.getContent()).singleElement()
                .satisfies(doc -> {
                    assertThat(doc.corpus()).isEqualTo("nitrowater");
                    assertThat(doc.status()).isEqualTo(DocIndexStatus.STALE);
                });
    }

    @Test
    void replacesTheInventoryWithTheLastRun() {
        service.replaceAll(List.of(
                new DocChunkBuilder.DocStat("nitrowater", "index.md", "Title", "zh", SHA_OLD, 3)),
                INDEXED_AT);

        verify(repository).deleteAllInBatch();
        verify(repository).saveAll(any());
    }

    private static DocCollection corpus(String name) {
        return new DocCollection(name, "/srv/docs/" + name, "",
                name.equals("nitrowater") ? "platform docs" : "");
    }

    private static DocChunkBuilder.DocStat stat(String corpus, String path, String sha) {
        return new DocChunkBuilder.DocStat(corpus, path, "Title", "zh", sha, 0);
    }

    private static AiDocIndex row(String corpus, String path, String sha) {
        AiDocIndex row = new AiDocIndex();
        row.setCorpus(corpus);
        row.setDocPath(path);
        row.setDocTitle("Title");
        row.setLang("zh");
        row.setContentSha(sha);
        row.setChunks(3);
        row.setIndexedAt(INDEXED_AT);
        return row;
    }
}
