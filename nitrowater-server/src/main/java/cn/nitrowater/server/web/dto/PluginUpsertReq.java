package cn.nitrowater.server.web.dto;

import cn.nitrowater.server.service.plugin.model.PluginDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create/update payload for a plugin (admin only).
 *
 * @param key stable key — required and validated on create; <strong>ignored on update</strong>
 *            (immutable, since it doubles as the docs path and the resource group key)
 * @param name display name (required)
 * @param tagline one-line subtitle (optional)
 * @param summary short blurb (optional)
 * @param icon 24x24 SVG path (optional)
 * @param tags tag labels (optional)
 * @param docsUrl full documentation site (optional)
 * @param homepageUrl homepage link (optional; blank hides it)
 * @param repoUrl source-repository link (optional; blank hides it)
 * @param sponsorUrl sponsor link (optional; blank hides it)
 * @param readme intro markdown (optional)
 * @param sortOrder listing order (default 0)
 * @param enabled whether shown to users (default true)
 */
public record PluginUpsertReq(
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$",
                message = "key must be lowercase letters, digits or hyphens, and start with a letter or digit")
        @Size(max = 64, message = "key must be at most 64 characters")
        String key,
        @NotBlank String name,
        @Size(max = 200) String tagline,
        @Size(max = 1000) String summary,
        @Size(max = 500) String icon,
        List<String> tags,
        @Size(max = 500) String docsUrl,
        @Size(max = 500) String homepageUrl,
        @Size(max = 500) String repoUrl,
        @Size(max = 500) String sponsorUrl,
        String readme,
        Integer sortOrder,
        Boolean enabled) {

    /** @return the service draft built from this validated body */
    public PluginDraft toDraft() {
        return new PluginDraft(key, name, tagline, summary, icon, tags, docsUrl, homepageUrl,
                repoUrl, sponsorUrl, readme, sortOrder, enabled);
    }
}
