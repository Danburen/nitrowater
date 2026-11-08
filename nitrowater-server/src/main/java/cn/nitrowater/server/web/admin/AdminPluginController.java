package cn.nitrowater.server.web.admin;

import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.server.service.plugin.PluginService;
import cn.nitrowater.server.web.dto.PluginUpsertReq;
import cn.nitrowater.server.web.vo.PluginAdminResp;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * Admin: paged list of every plugin.
     *
     * @param keyword optional key / name / tagline / summary substring
     * @param enabled optional enabled-state filter
     * @param sort {@code default}, {@code updated} or {@code name}
     * @param page zero-based page index
     * @param size page size
     * @return one page of plugin rows
     */
    @GetMapping("/api/plugins")
    public ApiResponse<Page<PluginAdminResp>> listAll(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "default") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(pluginService.listAdmin(keyword, enabled, sort, page, size)
                .map(PluginAdminResp::from));
    }

    /** Admin: create a plugin. */
    @PostMapping("/api/plugins")
    @ResponseStatus(HttpStatus.CREATED)
    public PluginAdminResp create(@Valid @RequestBody PluginUpsertReq request) {
        return PluginAdminResp.from(pluginService.create(request.toDraft()));
    }

    /** Admin: update a plugin (key is immutable). */
    @PutMapping("/api/plugins/{id}")
    public PluginAdminResp update(@PathVariable Long id, @Valid @RequestBody PluginUpsertReq request) {
        return PluginAdminResp.from(pluginService.update(id, request.toDraft()));
    }

    /** Admin: delete a plugin. */
    @DeleteMapping("/api/plugins/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        pluginService.delete(id);
    }
}
