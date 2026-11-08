package cn.nitrowater.server.web.vo;

import cn.nitrowater.server.infra.entity.Plugin;

import java.util.List;

/**
 * A public plugin detail — {@link PluginSummaryResp} plus the single-page {@code readme} markdown
 * rendered in the detail page's "introduction" tab.
 *
 * @param key stable plugin key
 * @param name display name
 * @param tagline one-line subtitle
 * @param summary short blurb
 * @param icon 24x24 SVG path
 * @param tags tag labels
 * @param docsUrl full documentation site (may be {@code null})
 * @param homepageUrl homepage link (may be {@code null})
 * @param repoUrl source-repository link (may be {@code null})
 * @param sponsorUrl sponsor link (may be {@code null})
 * @param readme intro markdown (may be {@code null})
 */
public record PluginDetailResp(
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
        String readme) {

    /**
     * @param plugin plugin row
     * @return the wire view
     */
    public static PluginDetailResp from(Plugin plugin) {
        return new PluginDetailResp(plugin.getPluginKey(), plugin.getName(), plugin.getTagline(),
                plugin.getSummary(), plugin.getIcon(), PluginSummaryResp.tags(plugin.getTags()),
                plugin.getDocsUrl(), plugin.getHomepageUrl(), plugin.getRepoUrl(),
                plugin.getSponsorUrl(), plugin.getReadme());
    }
}
