package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.MessageRole;
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
 * One persisted NitroBot message.
 *
 * <p>{@code seq} is monotonic within its conversation (starting at 1) and is what the rolling
 * summary references; {@code content} always keeps the original text even after the turn is pruned
 * from the prompt context ({@code isPruned}). Citations and tool traces are stored as JSON
 * snapshots so old answers render exactly as they did.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ai_message")
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36)
    private String uuid;

    /** References {@code ai_conversation.id} (same database, FK exists in the schema). */
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "role", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private MessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "mediumtext")
    private String content;

    @Column(name = "tokens", nullable = false)
    private Integer tokens = 0;

    @Column(name = "sources_json", columnDefinition = "text")
    private String sourcesJson;

    @Column(name = "tools_json", columnDefinition = "text")
    private String toolsJson;

    @Column(name = "model", length = 64)
    private String model;

    /** True when long tool output was folded into a placeholder in the prompt context. */
    @Column(name = "is_pruned", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean pruned = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
