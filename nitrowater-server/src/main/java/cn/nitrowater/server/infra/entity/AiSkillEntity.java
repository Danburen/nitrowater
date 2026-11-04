package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.SkillOrigin;
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
 * An admin-managed agent skill.
 *
 * <p>Skills shipped with the code stay on the classpath; this row set is for anything created,
 * imported or edited from the console. The {@code name} is the merge key with the built-ins: a row
 * with the same name overrides the built-in, and deleting it restores the built-in.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "ai_skill")
public class AiSkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Stable identifier (lowercase kebab-case); referenced by {@code loadSkill} and the prompt. */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "when_to_use", nullable = false, length = 255)
    private String whenToUse;

    /** Markdown instructions handed to the model verbatim. */
    @Column(name = "body", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String body;

    /** How the row was authored (filter/badge in the console). */
    @Column(name = "origin", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private SkillOrigin origin;

    /** Admin uid of the last writer. */
    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
