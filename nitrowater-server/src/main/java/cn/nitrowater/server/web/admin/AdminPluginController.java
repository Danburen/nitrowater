package cn.nitrowater.server.web.admin;

import cn.nitrowater.server.service.plugin.PluginService;
import cn.nitrowater.server.web.dto.PluginAdminResp;
import cn.nitrowater.server.web.dto.PluginUpsertReq;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin plugin-center endpoints (CRUD).
 *
 * <p>The whole controller requires {@code ROLE_ADMIN}. {@code pluginKey} is immutable after creation,
 * so the update endpoint ignores the key in the payload.</p>
 */
@RestController
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminPluginController {

    private final PluginService pluginService;

    public AdminPluginController(PluginService pluginService) {
        this.pluginService = pluginService;
    }

    /** Admin: list all plugins. */
    @GetMapping("/api/plugins")
    public List<PluginAdminResp> listAll() {
        return pluginService.listAdmin();
    }

    /** Admin: create a plugin. */
    @PostMapping("/api/plugins")
    @ResponseStatus(HttpStatus.CREATED)
    public PluginAdminResp create(@Valid @RequestBody PluginUpsertReq request) {
        return pluginService.create(request);
    }

    /** Admin: update a plugin (key is immutable). */
    @PutMapping("/api/plugins/{id}")
    public PluginAdminResp update(@PathVariable Long id, @Valid @RequestBody PluginUpsertReq request) {
        return pluginService.update(id, request);
    }

    /** Admin: delete a plugin. */
    @DeleteMapping("/api/plugins/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        pluginService.delete(id);
    }
}
