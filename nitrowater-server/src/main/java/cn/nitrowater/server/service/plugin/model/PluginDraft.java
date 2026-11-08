package cn.nitrowater.server.service.plugin.model;

import java.util.List;

/**
 * Create/update payload of a plugin, detached from the HTTP DTO.
 *
 * @param key stable key; required on create, ignored on update (immutable)
 * @param name display name
 * @param tagline one-line subtitle
 * @param summary short blurb
 * @param icon 24x24 SVG path
 * @param tags tag labels
 * @param docsUrl full documentation site
 * @param homepageUrl homepage link
 * @param repoUrl source-repository link
 * @param sponsorUrl sponsor link
 * @param readme intro markdown
 * @param sortOrder listing order
 * @param enabled whether shown to users
 */
public record PluginDraft(
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
        Boolean enabled) {
}
