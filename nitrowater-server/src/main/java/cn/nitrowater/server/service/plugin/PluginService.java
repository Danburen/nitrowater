package cn.nitrowater.server.service.plugin;

import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.lib.utils.StringUtil;
import cn.nitrowater.server.infra.entity.Plugin;
import cn.nitrowater.server.infra.repository.PluginRepository;
import cn.nitrowater.server.infra.specification.PluginSpec;
import cn.nitrowater.server.service.plugin.model.PluginDraft;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Reads and writes plugin-center metadata.
 *
 * <p>Tags are stored as JSON text; the web layer renders them as a list via {@link JsonUtil}. Blank
 * optional strings become {@code null} so the front-end can hide the matching link.</p>
 *
 * <p>The service returns {@link Plugin} entities (and pages of them): the controller maps them to
 * response VOs, which keeps the JSON shape of the plugin endpoints owned by the web layer.</p>
 */
@Service
public class PluginService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PluginRepository plugins;

    public PluginService(PluginRepository plugins) {
        this.plugins = plugins;
    }

    /**
     * @param keyword optional case-insensitive match on key / name / tagline / summary
     * @param page zero-based page index
     * @param size page size
     * @return enabled plugins ordered by sort order (public listing, no readme)
     */
    @Transactional(readOnly = true)
    public Page<Plugin> listPublic(String keyword, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by(Sort.Direction.ASC, "sortOrder").and(Sort.by("id")));
        return plugins.findAll(PluginSpec.of(true, keyword), pageable);
    }

    /**
     * @param key plugin key
     * @return the enabled plugin with that key, including its intro markdown
     */
    @Transactional(readOnly = true)
    public Plugin getPublic(String key) {
        return plugins.findByPluginKeyAndEnabledTrue(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plugin not found"));
    }

    /**
     * @param keyword optional case-insensitive match on key / name / tagline / summary
     * @param enabled optional enabled-state filter
     * @param sort {@code default} (sort order), {@code updated} (newest change first) or {@code name}
     * @param page zero-based page index
     * @param size page size
     * @return one page of plugin rows (admin)
     */
    @Transactional(readOnly = true)
    public Page<Plugin> listAdmin(String keyword, Boolean enabled, String sort, int page, int size) {
        return plugins.findAll(PluginSpec.of(enabled, keyword), pageable(page, size, adminSort(sort)));
    }

    private static Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)), sort);
    }

    private static Sort adminSort(String sort) {
        return switch (sort == null ? "" : sort) {
            case "updated" -> Sort.by(Sort.Direction.DESC, "updatedAt");
            case "name" -> Sort.by(Sort.Direction.ASC, "name").and(Sort.by("id"));
            default -> Sort.by(Sort.Direction.ASC, "sortOrder").and(Sort.by("id"));
        };
    }

    /**
     * @param draft create payload; {@code key} is required (400 when blank) and must be unique (409)
     * @return the created plugin
     */
    @Transactional
    public Plugin create(PluginDraft draft) {
        String key = draft.key() == null ? "" : draft.key().trim();
        if (key.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plugin key is required");
        }
        if (plugins.existsByPluginKey(key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "plugin key already exists: " + key);
        }
        Plugin plugin = new Plugin();
        plugin.setPluginKey(key);
        apply(plugin, draft);
        return plugins.save(plugin);
    }

    /**
     * @param id plugin id
     * @param draft update payload; {@code key} is ignored (immutable — it doubles as the docs path
     *              and the resource group key)
     * @return the updated plugin
     */
    @Transactional
    public Plugin update(Long id, PluginDraft draft) {
        Plugin plugin = require(id);
        apply(plugin, draft);
        plugin.setUpdatedAt(Instant.now());
        return plugins.save(plugin);
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

    private static void apply(Plugin plugin, PluginDraft draft) {
        plugin.setName(draft.name().trim());
        plugin.setTagline(blankToNull(draft.tagline()));
        plugin.setSummary(blankToNull(draft.summary()));
        plugin.setIcon(blankToNull(draft.icon()));
        plugin.setTags(tagsToJson(draft.tags()));
        plugin.setDocsUrl(blankToNull(draft.docsUrl()));
        plugin.setHomepageUrl(blankToNull(draft.homepageUrl()));
        plugin.setRepoUrl(blankToNull(draft.repoUrl()));
        plugin.setSponsorUrl(blankToNull(draft.sponsorUrl()));
        plugin.setReadme(blankToNull(draft.readme()));
        if (draft.sortOrder() != null) {
            plugin.setSortOrder(draft.sortOrder());
        }
        if (draft.enabled() != null) {
            plugin.setEnabled(draft.enabled());
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
}
