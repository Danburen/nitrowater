package cn.nitrowater.server.web.vo;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.infra.entity.Plugin;
import tools.jackson.core.type.TypeReference;

import java.util.List;

/**
 * A plugin card (public listing — no {@code readme}, keeps the list payload small).
 *
 * <p>Fields map to the front-end {@code PluginSummary}; the three external links may be {@code null}
 * and are then hidden by the UI.</p>
 *
 * @param key stable plugin key (route {@code /plugins/<key>})
 * @param name display name
 * @param tagline one-line subtitle
 * @param summary card blurb
 * @param icon 24x24 SVG path
 * @param tags tag labels
 * @param docsUrl full documentation site (may be {@code null})
 * @param homepageUrl homepage link (may be {@code null})
 * @param repoUrl source-repository link (may be {@code null})
 * @param sponsorUrl sponsor link (may be {@code null})
 */
public record PluginSummaryResp(
        String key,
        String name,
        String tagline,
        String summary,
        String icon,
        List<String> tags,
        String docsUrl,
        String homepageUrl,
        String repoUrl,
        String sponsorUrl) {

    private static final TypeReference<List<String>> TAGS_TYPE = new TypeReference<>() {
    };

    /**
     * @param plugin plugin row
     * @return the wire view
     */
    public static PluginSummaryResp from(Plugin plugin) {
        return new PluginSummaryResp(plugin.getPluginKey(), plugin.getName(), plugin.getTagline(),
                plugin.getSummary(), plugin.getIcon(), tags(plugin.getTags()), plugin.getDocsUrl(),
                plugin.getHomepageUrl(), plugin.getRepoUrl(), plugin.getSponsorUrl());
    }

    /**
     * @param json stored tag JSON
     * @return the parsed list, never {@code null}
     */
    static List<String> tags(String json) {
        List<String> parsed = JsonUtil.parseList(json, TAGS_TYPE);
        return parsed == null ? List.of() : parsed;
    }
}
