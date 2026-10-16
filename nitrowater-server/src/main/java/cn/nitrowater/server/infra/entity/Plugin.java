package cn.nitrowater.server.infra.entity;

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
 * A plugin-center entry — the metadata shown on {@code /plugins}.
 *
 * <p>The jar itself is not stored here: {@link #pluginKey} logically links to the resource service
 * ({@code resource.group_key}) and to the docs directory {@code /plugin/<key>/} served by the BFF.
 * No physical FK (cross-service reference by key, see {@code AGENTS.md}).</p>
 */
@Getter
@Setter
@Entity
@Table(name = "plugin")
public class Plugin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Stable key (immutable). Matches {@code resource.group_key} and the docs path {@code /plugin/<key>/};
     * column is {@code plugin_key} because {@code key} is a MySQL reserved word.
     */
    @Column(name = "plugin_key", nullable = false, length = 64)
    private String pluginKey;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "tagline", length = 200)
    private String tagline;

    @Column(name = "summary", length = 1000)
    private String summary;

    /** 24x24 line-icon SVG path (the {@code d} attribute). */
    @Column(name = "icon", length = 500)
    private String icon;

    /** Tag list as JSON text ({@code ["Velocity","跨服聊天"]}). */
    @Column(name = "tags", columnDefinition = "json")
    private String tags;

    /** Full documentation site (default {@code /plugin/<key>/docs/}, same-origin navigation). */
    @Column(name = "docs_url", length = 500)
    private String docsUrl;

    /** Homepage link; {@code null} hides it on the front-end. */
    @Column(name = "homepage_url", length = 500)
    private String homepageUrl;

    /** Source-repository link; {@code null} hides it on the front-end. */
    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    /** Sponsor/donation link; {@code null} hides it on the front-end. */
    @Column(name = "sponsor_url", length = 500)
    private String sponsorUrl;

    /** Single-page intro markdown (the detail page "介绍" tab). */
    @Column(name = "readme", columnDefinition = "mediumtext")
    private String readme;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "enabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
