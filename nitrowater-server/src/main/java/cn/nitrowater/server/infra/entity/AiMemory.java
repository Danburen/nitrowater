package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.MemoryKind;
import cn.nitrowater.server.infra.enums.MemoryStatus;
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
 * A long-term memory entry injected into future prompts.
 *
 * <p>MySQL is the source of truth; {@code qdrantPointId} is only the vector shadow used for
 * recall, and stays {@code null} until the entry has been indexed.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ai_memory")
public class AiMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36)
    private String uuid;

    /** Logical link to account uid (no cross-DB FK). */
    @Column(name = "uid", nullable = false)
    private Long uid;

    @Column(name = "kind", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private MemoryKind kind = MemoryKind.FACT;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "source_conversation_uuid", length = 36)
    private String sourceConversationUuid;

    /** 0-100; entries below the injection threshold stay dormant. */
    @Column(name = "confidence", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private Short confidence = 50;

    @Column(name = "status", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private MemoryStatus status = MemoryStatus.ACTIVE;

    @Column(name = "hits", nullable = false)
    private Integer hits = 0;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "qdrant_point_id", length = 36)
    private String qdrantPointId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
