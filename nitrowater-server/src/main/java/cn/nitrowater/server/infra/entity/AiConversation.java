package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.ConversationStatus;
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
 * A NitroBot conversation owned by one user.
 *
 * <p>{@code uuid} is the external identifier the SPA uses; {@code uid} logically references
 * {@code nitrowater_account.user.uid} (no cross-DB FK). {@code summary} holds a rolling summary of
 * earlier turns so long conversations stay inside the context budget, and {@code compressVersion}
 * is the optimistic lock that keeps concurrent compression runs from overwriting each other.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ai_conversation")
public class AiConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36)
    private String uuid;

    /** Logical link to account uid (no cross-DB FK). */
    @Column(name = "uid", nullable = false)
    private Long uid;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "message_count", nullable = false)
    private Integer messageCount = 0;

    @Column(name = "tokens_estimate", nullable = false)
    private Integer tokensEstimate = 0;

    /** Rolling summary of turns up to {@link #summaryUptoSeq}. */
    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "summary_upto_seq", nullable = false)
    private Integer summaryUptoSeq = 0;

    @Column(name = "compress_version", nullable = false)
    private Integer compressVersion = 0;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "status", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
