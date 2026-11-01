package cn.nitrowater.resource.web;

import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.resource.application.ResourceService;
import cn.nitrowater.resource.application.dto.ResourceMeta;
import cn.nitrowater.resource.application.dto.AdminResourceQuery;
import cn.nitrowater.resource.web.dto.DictionaryResp;
import cn.nitrowater.resource.web.dto.OptionResp;
import cn.nitrowater.resource.web.dto.ResourceMetaResp;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * File-management console endpoints (admin surface, mounted under {@code /api/admin/resources}).
 *
 * <ul>
 *   <li>{@code GET /api/admin/resources} — filtered, paged listing;</li>
 *   <li>{@code GET /api/admin/resources/dictionaries} — filter dictionaries (enum options + present apps);</li>
 *   <li>{@code GET /api/admin/resources/{uuid}} — one resource's metadata;</li>
 *   <li>{@code GET /api/admin/resources/{uuid}/download} — forced download of the bytes;</li>
 *   <li>{@code DELETE /api/admin/resources/{uuid}} — delete (bytes + row).</li>
 * </ul>
 *
 * <p>The path is guarded to {@code ROLE_ADMIN} / {@code ROLE_SUPER_ADMIN} at the filter chain
 * ({@link cn.nitrowater.resource.config.ResourceSecurityConfig}); the per-row rules — a
 * {@code ROLE_SUPER_ADMIN} sees and deletes anything, any other admin only the resources they own —
 * are applied in the service, because they depend on the caller's uid and role. This controller only
 * binds request parameters and renders the service results.</p>
 */
@RestController
@RequestMapping("/api/admin/resources")
public class AdminResourceController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ResourceService resourceService;

    public AdminResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /** Admin: filtered, paged listing (super-admin: all; other admin: own only). */
    @GetMapping
    public ApiResponse<Page<ResourceMetaResp>> list(
            @RequestParam(required = false) String app,
            @RequestParam(name = "group", required = false) String group,
            @RequestParam(required = false) Long ownerUid,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @AuthenticationPrincipal Jwt jwt) {
        AdminResourceQuery query = new AdminResourceQuery(
                app, group, ownerUid,
                ResourceWebMapper.statusOf(status), ResourceWebMapper.sourceTypeOf(sourceType), keyword);
        Page<ResourceMeta> result = resourceService.adminList(query, ResourceWebMapper.uidOf(jwt),
                ResourceWebMapper.isSuperAdmin(jwt), page, size);
        return ApiResponse.success(result.map(ResourceWebMapper::toResp));
    }

    /**
     * Admin: filter dictionaries for the console — the enum options plus the app namespaces actually
     * present. Shipping them keeps the front-end selects in lockstep with the server instead of
     * hardcoding enum labels that can drift.
     */
    @GetMapping("/dictionaries")
    public DictionaryResp dictionaries(@AuthenticationPrincipal Jwt jwt) {
        List<OptionResp> apps = resourceService
                .listVisibleApps(ResourceWebMapper.uidOf(jwt), ResourceWebMapper.isSuperAdmin(jwt)).stream()
                .map(app -> new OptionResp(app, app))
                .toList();
        return new DictionaryResp(apps, ResourceWebMapper.sourceTypeOptions(), ResourceWebMapper.statusOptions());
    }

    /** Admin: one resource's metadata. */
    @GetMapping("/{uuid}")
    public ResourceMetaResp detail(@PathVariable String uuid, @AuthenticationPrincipal Jwt jwt) {
        return ResourceWebMapper.toResp(resourceService.adminGet(uuid, ResourceWebMapper.uidOf(jwt),
                ResourceWebMapper.isSuperAdmin(jwt)));
    }

    /** Admin: forced download of a resource's bytes. */
    @GetMapping("/{uuid}/download")
    public ResponseEntity<?> download(@PathVariable String uuid, @AuthenticationPrincipal Jwt jwt) {
        return ResourceDownloadResponder.respond(resourceService.adminDownload(uuid,
                ResourceWebMapper.uidOf(jwt), ResourceWebMapper.isSuperAdmin(jwt)));
    }

    /** Admin: delete a resource (bytes + metadata); idempotent. */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable String uuid, @AuthenticationPrincipal Jwt jwt) {
        resourceService.adminDelete(uuid, ResourceWebMapper.uidOf(jwt), ResourceWebMapper.isSuperAdmin(jwt));
        return ResponseEntity.noContent().build();
    }
}
