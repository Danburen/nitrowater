package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.ProfileChangeSource;
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
 * Audit row for a single profile field change.
 *
 * <p>Inferred changes carry a confidence (0-100) so the user can review or revert them; user-made
 * changes are recorded for symmetry.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ai_profile_change")
public class AiProfileChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Logical link to account uid (no cross-DB FK). */
    @Column(name = "uid", nullable = false)
    private Long uid;

    @Column(name = "field", nullable = false, length = 64)
    private String field;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(name = "source", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private ProfileChangeSource source = ProfileChangeSource.USER;

    @Column(name = "confidence", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private Short confidence = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
