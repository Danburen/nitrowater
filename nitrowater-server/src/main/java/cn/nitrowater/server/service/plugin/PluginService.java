package cn.nitrowater.server.service.plugin;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.infra.entity.Plugin;
import cn.nitrowater.server.infra.repository.PluginRepository;
import cn.nitrowater.server.web.dto.PluginAdminResp;
import cn.nitrowater.server.web.dto.PluginDetailResp;
import cn.nitrowater.server.web.dto.PluginSummaryResp;
import cn.nitrowater.server.web.dto.PluginUpsertReq;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;

import java.time.Instant;
import java.util.List;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Reads and writes plugin-center metadata.
 *
 * <p>Tags are stored as JSON text and surfaced as a {@code List<String>} via {@link JsonUtil};
 * blank optional strings become {@code null} so the front-end can hide the matching link.</p>
 */
@Service
public class PluginService {

    /** JSON shape of {@code plugin.tags}. */
    private static final TypeReference<List<String>> TAGS = new TypeReference<List<String>>() {
    };

    private final PluginRepository plugins;

    public PluginService(PluginRepository plugins) {
        this.plugins = plugins;
    }

    /** @return enabled plugins ordered by sort order (public listing, no readme) */
    @Transactional(readOnly = true)
    public List<PluginSummaryResp> listPublic() {
        return plugins.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(PluginService::toSummary)
                .toList();
    }

    /**
     * @param key plugin key
     * @return the enabled plugin with that key, including its intro markdown
     */
    @Transactional(readOnly = true)
    public PluginDetailResp getPublic(String key) {
        Plugin plugin = plugins.findByPluginKeyAndEnabledTrue(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found"));
        return toDetail(plugin);
    }

    /** @return every plugin ordered by sort order (admin) */
    @Transactional(readOnly = true)
    public List<PluginAdminResp> listAdmin() {
        return plugins.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(PluginService::toAdmin)
                .toList();
    }

    /**
     * @param req create payload; {@code key} is required (400 when blank) and must be unique (409)
     * @return the created plugin
     */
    @Transactional
    public PluginAdminResp create(PluginUpsertReq req) {
        String key = req.key() == null ? "" : req.key().trim();
        if (key.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plugin key is required");
        }
        if (plugins.existsByPluginKey(key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "plugin key already exists: " + key);
        }
        Plugin plugin = new Plugin();
        plugin.setPluginKey(key);
        apply(plugin, req);
        return toAdmin(plugins.save(plugin));
    }

    /**
     * @param id plugin id
     * @param req update payload; {@code key} is ignored (immutable — it doubles as the docs path
     *            and the resource group key)
     * @return the updated plugin
     */
    @Transactional
    public PluginAdminResp update(Long id, PluginUpsertReq req) {
        Plugin plugin = require(id);
        apply(plugin, req);
        plugin.setUpdatedAt(Instant.now());
        return toAdmin(plugins.save(plugin));
    }

    /**
     * @param id plugin id
     */
    @Transactional
    public void delete(Long id) {
        require(id);
        plugins.deleteById(id);
    }

    private Plugin require(Long id) {
        return plugins.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found"));
    }

    private static void apply(Plugin plugin, PluginUpsertReq req) {
        plugin.setName(req.name().trim());
        plugin.setTagline(blankToNull(req.tagline()));
        plugin.setSummary(blankToNull(req.summary()));
        plugin.setIcon(blankToNull(req.icon()));
        plugin.setTags(tagsToJson(req.tags()));
        plugin.setDocsUrl(blankToNull(req.docsUrl()));
        plugin.setHomepageUrl(blankToNull(req.homepageUrl()));
        plugin.setRepoUrl(blankToNull(req.repoUrl()));
        plugin.setSponsorUrl(blankToNull(req.sponsorUrl()));
        plugin.setReadme(blankToNull(req.readme()));
        if (req.sortOrder() != null) {
            plugin.setSortOrder(req.sortOrder());
        }
        if (req.enabled() != null) {
            plugin.setEnabled(req.enabled());
        }
    }

    private static String blankToNull(String value) {
        return (StringUtil.isBlank(value)) ? null : value;
    }

    private static String tagsToJson(List<String> tags) {
        if (tags == null) {
            return null;
        }
        List<String> cleaned = tags.stream()
                .filter(tag -> StringUtil.isNotBlank(tag))
                .map(String::trim)
                .toList();
        return cleaned.isEmpty() ? null : JsonUtil.toJson(cleaned);
    }

    private static List<String> tagsOf(String json) {
        List<String> parsed = JsonUtil.fromJson(json, TAGS);
        return parsed == null ? List.of() : parsed;
    }

    private static PluginSummaryResp toSummary(Plugin p) {
        return new PluginSummaryResp(p.getPluginKey(), p.getName(), p.getTagline(), p.getSummary(),
                p.getIcon(), tagsOf(p.getTags()), p.getDocsUrl(), p.getHomepageUrl(), p.getRepoUrl(),
                p.getSponsorUrl());
    }

    private static PluginDetailResp toDetail(Plugin p) {
        return new PluginDetailResp(p.getPluginKey(), p.getName(), p.getTagline(), p.getSummary(),
                p.getIcon(), tagsOf(p.getTags()), p.getDocsUrl(), p.getHomepageUrl(), p.getRepoUrl(),
                p.getSponsorUrl(), p.getReadme());
    }

    private static PluginAdminResp toAdmin(Plugin p) {
        return new PluginAdminResp(p.getId(), p.getPluginKey(), p.getName(), p.getTagline(), p.getSummary(),
                p.getIcon(), tagsOf(p.getTags()), p.getDocsUrl(), p.getHomepageUrl(), p.getRepoUrl(),
                p.getSponsorUrl(), p.getReadme(), p.getSortOrder(), p.getEnabled(), p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
