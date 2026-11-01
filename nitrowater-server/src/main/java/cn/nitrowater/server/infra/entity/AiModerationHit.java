package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.ModerationAction;
import cn.nitrowater.server.infra.enums.ModerationDirection;
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
 * One content-moderation hit, kept for administrator review.
 *
 * <p>The matched terms are stored, the surrounding text is not: the point of the gate is to keep
 * flagged content out of the database, so a review row must never become a copy of it. Output and
 * memory hits are recorded for dictionary tuning only and never count against the account.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ai_moderation_hit")
public class AiModerationHit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "bigint UNSIGNED")
    private Long id;

    @Column(name = "uid", nullable = false, columnDefinition = "bigint UNSIGNED")
    private Long uid;

    @Column(name = "conversation_uuid", length = 36)
    private String conversationUuid;

    @Column(name = "direction", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private ModerationDirection direction;

    @Column(name = "action", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private ModerationAction action;

    /** Distinct matched terms, comma separated and truncated to the column width. */
    @Column(name = "words", nullable = false, length = 255)
    private String words;

    /** Number of distinct matched terms. */
    @Column(name = "hit_count", nullable = false, columnDefinition = "int UNSIGNED")
    private Integer hitCount = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
