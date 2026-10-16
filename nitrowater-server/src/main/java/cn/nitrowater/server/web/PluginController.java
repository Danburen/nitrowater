package cn.nitrowater.server.web;

import cn.nitrowater.server.service.plugin.PluginService;
import cn.nitrowater.server.web.admin.AdminPluginController;
import cn.nitrowater.server.web.dto.PluginDetailResp;
import cn.nitrowater.server.web.dto.PluginSummaryResp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public plugin-center endpoints (anonymous read).
 *
 * <ul>
 *   <li>{@code GET /api/public/plugins} — list enabled plugins (cards).</li>
 *   <li>{@code GET /api/public/plugins/{key}} — one enabled plugin, including its intro markdown.</li>
 * </ul>
 *
 * <p>Admin writes live in {@link AdminPluginController}.</p>
 */
@RestController
public class PluginController {

    private final PluginService pluginService;

    public PluginController(PluginService pluginService) {
        this.pluginService = pluginService;
    }

    /** Anonymous: list enabled plugins. */
    @GetMapping("/api/public/plugins")
    public List<PluginSummaryResp> listPublic() {
        return pluginService.listPublic();
    }

    /** Anonymous: one enabled plugin with its intro markdown. */
    @GetMapping("/api/public/plugins/{key}")
    public PluginDetailResp getPublic(@PathVariable String key) {
        return pluginService.getPublic(key);
    }
}
