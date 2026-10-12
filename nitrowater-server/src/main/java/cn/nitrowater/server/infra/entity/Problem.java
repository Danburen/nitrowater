package cn.nitrowater.server.infra.entity;

import cn.nitrowater.server.infra.enums.IoMode;
import cn.nitrowater.server.infra.enums.ProblemDifficulty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A judgeable problem. */
@Getter
@Setter
@Entity
@Table(name = "problem")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Stable human-readable id. */
    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** English title; falls back to {@link #title} when absent. */
    @Column(name = "title_en", length = 200)
    private String titleEn;

    @Column(name = "difficulty", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private ProblemDifficulty difficulty = ProblemDifficulty.EASY;

    /** Markdown statement (Chinese). */
    @Column(name = "description", nullable = false, columnDefinition = "mediumtext")
    private String description;

    /** English markdown statement; falls back to {@link #description} when absent. */
    @Column(name = "description_en", columnDefinition = "mediumtext")
    private String descriptionEn;

    @Column(name = "io_mode", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private IoMode ioMode = IoMode.ACM;

    /** Comparison strategy / LeetCode method signature as JSON text. */
    @Column(name = "judge_config", columnDefinition = "json")
    private String judgeConfig;

    /** Per-language starter templates as JSON text. */
    @Column(name = "templates", columnDefinition = "json")
    private String templates;

    @Column(name = "time_limit_ms", nullable = false)
    private Integer timeLimitMs = 5000;

    @Column(name = "memory_limit_kb", nullable = false)
    private Integer memoryLimitKb = 262144;

    /** Points rewarded on first AC. */
    @Column(name = "reward_points", nullable = false)
    private Integer rewardPoints = 0;

    @Column(name = "enabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean enabled = true;

    /** Author uid (logical). */
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
