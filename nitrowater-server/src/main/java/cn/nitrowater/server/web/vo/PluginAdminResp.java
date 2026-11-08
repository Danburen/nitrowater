package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.infra.entity.Plugin;

import java.time.Instant;
import java.util.List;

/**
 * A plugin row for the admin console (adds identity/ordering/state to {@link PluginDetailResp}).
 *
 * @param id primary key (used by update/delete)
 * @param key stable plugin key
 * @param name display name
 * @param tagline one-line subtitle
 * @param summary short blurb
 * @param icon 24x24 SVG path
 * @param tags tag labels
 * @param docsUrl full documentation site (nullable)
 * @param homepageUrl homepage link (nullable)
 * @param repoUrl source-repository link (nullable)
 * @param sponsorUrl sponsor link (nullable)
 * @param readme intro markdown (nullable)
 * @param sortOrder listing order (ascending)
 * @param enabled whether shown to users
 * @param createdAt creation timestamp
 * @param updatedAt last-update timestamp
 */
public record PluginAdminResp(
        Long id,
        String key,
        String name,
        String tagline,
        String summary,
        String icon,
        List<String> tags,
        String docsUrl,
        String homepageUrl,
        String repoUrl,
        String sponsorUrl,
        String readme,
        Integer sortOrder,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * @param plugin plugin row
     * @return the wire view
     */
    public static PluginAdminResp from(Plugin plugin) {
        return new PluginAdminResp(plugin.getId(), plugin.getPluginKey(), plugin.getName(),
                plugin.getTagline(), plugin.getSummary(), plugin.getIcon(),
                PluginSummaryResp.tags(plugin.getTags()), plugin.getDocsUrl(),
                plugin.getHomepageUrl(), plugin.getRepoUrl(), plugin.getSponsorUrl(),
                plugin.getReadme(), plugin.getSortOrder(), plugin.getEnabled(),
                plugin.getCreatedAt(), plugin.getUpdatedAt());
    }
}
