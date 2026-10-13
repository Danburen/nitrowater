package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.ProblemSetVisibility;
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
 * A curated problem set ("题单"), e.g. 热门题目 / 面试经典 / 数据结构.
 *
 * <p>A problem may belong to many sets via {@link ProblemSetItem}. Bilingual via direct
 * {@code _en} columns.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "problem_set")
public class ProblemSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Stable key (curated sets only), e.g. {@code hot-100}; {@code null} for user-owned sets. */
    @Column(name = "slug", length = 64)
    private String slug;

    /** Owner uid for user-owned sets; {@code null} for curated sets (cross-DB logical key, no FK). */
    @Column(name = "owner_uid")
    private Long ownerUid;

    /** Visibility; only meaningful for user-owned sets. */
    @Column(name = "visibility", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private ProblemSetVisibility visibility = ProblemSetVisibility.PRIVATE;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** English name; falls back to {@link #name} when absent. */
    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Column(name = "description", length = 500)
    private String description;

    /** English description; falls back to {@link #description} when absent. */
    @Column(name = "description_en", length = 500)
    private String descriptionEn;

    @Column(name = "color", length = 16)
    private String color;

    /** Display order (ascending). */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "enabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
