package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.EditorialAuthorType;
import cn.nitrowater.server.infra.enums.EditorialStatus;
import cn.nitrowater.server.infra.enums.Language;
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
 * A problem editorial (1:N per {@link Problem}).
 *
 * <p>Chinese-only {@code content}. All {@link EditorialStatus#PUBLISHED} editorials are publicly
 * visible; {@code DRAFT} / {@code HIDDEN} are admin-only. Official editorials may be pinned.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "problem_editorial")
public class ProblemEditorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    /** Author uid (logical; official editorials carry the admin uid). */
    @Column(name = "author_uid")
    private Long authorUid;

    @Column(name = "author_type", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private EditorialAuthorType authorType = EditorialAuthorType.OFFICIAL;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Markdown body (Chinese). */
    @Column(name = "content", nullable = false, columnDefinition = "mediumtext")
    private String content;

    /** Language of the code snippet, mirroring {@code submission.language}. */
    @Column(name = "language", columnDefinition = "tinyint UNSIGNED")
    private Language language;

    @Column(name = "status", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private EditorialStatus status = EditorialStatus.DRAFT;

    /** Pinned (official). */
    @Column(name = "pinned", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean pinned = false;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount = 0;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
