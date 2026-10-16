package cn.nitrowater.server.web.dto;

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
}
