package cn.nitrowater.server.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One markdown document embedded in the RAG vector index.
 *
 * <p>The table is the durable half of the index inventory: the vectors live in Qdrant, this row
 * remembers what was embedded and from which content. Comparing {@code content_sha} with the file
 * on disk yields {@code STALE}, and the absence of a row yields {@code MISSING}; both are what tells
 * the startup bootstrap that a deploy actually changed the documentation.</p>
 *
 * <p>Only the indexer writes here; the admin report reads it. Losing the table is harmless — the
 * next re-index rebuilds it from the mount.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ai_doc_index")
public class AiDocIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "bigint UNSIGNED")
    private Long id;

    /** Logical corpus name: the directory under the docs root. */
    @Column(name = "corpus", nullable = false, length = 64)
    private String corpus;

    /** Corpus-relative markdown path, e.g. {@code architecture/overview.md}. */
    @Column(name = "doc_path", nullable = false, length = 512)
    private String docPath;

    @Column(name = "doc_title", nullable = false, length = 255)
    private String docTitle;

    /** {@code zh} or {@code en}. */
    @Column(name = "lang", nullable = false, length = 8)
    private String lang;

    /** SHA-256 of the markdown file at the last successful index run. */
    @Column(name = "content_sha", nullable = false, length = 64)
    private String contentSha;

    @Column(name = "chunks", nullable = false, columnDefinition = "int UNSIGNED")
    private int chunks;

    @Column(name = "indexed_at", nullable = false)
    private Instant indexedAt;
}
