package cn.nitrowater.server.infra.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Maps a problem to a {@link Tag}.
 *
 * <p>Composite PK {@code (problem_id, tag_id)}; the FKs to {@code problem} / {@code tag}
 * are enforced in the DB ({@code ON DELETE CASCADE}).</p>
 */
@Getter
@Setter
@Entity
@Table(name = "problem_tag")
public class ProblemTag {

    @EmbeddedId
    private ProblemTagId id;
}
