package cn.nitrowater.server.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps a problem to a {@link ProblemSet} ("题单") with an explicit order inside the set.
 *
 * <p>Composite PK {@code (problem_id, set_id)}; the FKs are enforced in the DB
 * ({@code ON DELETE CASCADE}).</p>
 */
@Getter
@Setter
@Entity
@Table(name = "problem_set_item")
public class ProblemSetItem {

    @EmbeddedId
    private ProblemSetItemId id;

    /** Order inside the set (ascending). */
    @Column(name = "ord", nullable = false)
    private Integer ord = 0;
}
