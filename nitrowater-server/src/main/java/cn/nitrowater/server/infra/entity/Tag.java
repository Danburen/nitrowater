package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.TagKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A problem tag (topic / company / category), bilingual via direct {@code _en} columns. */
@Getter
@Setter
@Entity
@Table(name = "tag")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Stable key, e.g. {@code array} / {@code dynamic-programming}. */
    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** English name; falls back to {@link #name} when absent. */
    @Column(name = "name_en", length = 64)
    private String nameEn;

    @Column(name = "kind", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private TagKind kind = TagKind.TOPIC;

    @Column(name = "color", length = 16)
    private String color;

    @Column(name = "enabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
