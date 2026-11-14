package cn.nitrowater.resource.web;

import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.resource.service.ResourceService;
import cn.nitrowater.resource.service.dto.AdminResourceQuery;
import cn.nitrowater.resource.infrastructure.domain.ResourceStatus;
import cn.nitrowater.resource.infrastructure.domain.SourceType;
import cn.nitrowater.resource.service.dto.resp.DictionaryResp;
import cn.nitrowater.resource.service.dto.resp.OptionResp;
import cn.nitrowater.resource.service.dto.resp.ResourceMetaResp;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntFunction;

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
 * <p>The path is guarded to {@code ROLE_ADMIN} / {@code ROLE_SUPER_ADMIN} twice: the filter chain
 * ({@link cn.nitrowater.resource.config.ResourceSecurityConfig}) is the floor, so a new admin
 * controller cannot be exposed by forgetting the annotation, and {@link PreAuthorize} on the class
 * makes the requirement visible where the endpoints are declared. {@code ROLE_SUPER_ADMIN} tokens
 * already carry {@code ROLE_ADMIN}, so one authority covers both. The per-row rules — a
 * {@code ROLE_SUPER_ADMIN} sees and deletes anything, any other admin only the resources they own —
 * are applied in the service, because they depend on the caller's uid and role. This controller only
 * binds request parameters and renders the service results.</p>
 */
@RestController
@RequestMapping("/api/admin/resources")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
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
            Authentication authentication) {
        AdminResourceQuery query = new AdminResourceQuery(
                app, group, ownerUid,
                ResourceWebMapper.statusOf(status), ResourceWebMapper.sourceTypeOf(sourceType), keyword);
        return ApiResponse.success(resourceService.adminList(query, ResourceWebMapper.uidOf(authentication),
                ResourceWebMapper.isSuperAdmin(authentication), page, size).map(ResourceMetaResp::from));
    }

    /**
     * Admin: filter dictionaries for the console — the enum options plus the app namespaces actually
     * present. Shipping them keeps the front-end selects in lockstep with the server instead of
     * hardcoding enum labels that can drift.
     */
    @GetMapping("/dictionaries")
    public ApiResponse<DictionaryResp> dictionaries(Authentication authentication) {
        List<OptionResp> apps = resourceService
                .listVisibleApps(ResourceWebMapper.uidOf(authentication),
                        ResourceWebMapper.isSuperAdmin(authentication)).stream()
                .map(app -> new OptionResp(app, app))
                .toList();
        return ApiResponse.success(new DictionaryResp(apps,
                codedOptions(SourceType.values(), SourceType::getValue),
                codedOptions(ResourceStatus.values(), ResourceStatus::getValue)));
    }

    /**
     * Builds the console options of a coded enum, labelled {@code NAME(value)} — the same convention
     * the database column {@code COMMENT}s use, so a select can never drift from the codes.
     *
     * @param values enum constants
     * @param code reads the persisted tinyint code off a constant
     * @param <E> enum type
     * @return one option per constant, in declaration order
     */
    private static <E extends Enum<E>> List<OptionResp> codedOptions(E[] values, ToIntFunction<E> code) {
        return Arrays.stream(values)
                .map(v -> new OptionResp(v.name(), v.name() + "(" + code.applyAsInt(v) + ")"))
                .toList();
    }

    /** Admin: one resource's metadata. */
    @GetMapping("/{uuid}")
    public ApiResponse<ResourceMetaResp> detail(@PathVariable String uuid, Authentication authentication) {
        return ApiResponse.success(ResourceMetaResp.from(resourceService.adminGet(uuid,
                ResourceWebMapper.uidOf(authentication), ResourceWebMapper.isSuperAdmin(authentication))));
    }

    /** Admin: forced download of a resource's bytes (a stream — deliberately not enveloped). */
    @GetMapping("/{uuid}/download")
    public ResponseEntity<?> download(@PathVariable String uuid, Authentication authentication) {
        return ResourceDownloadResponder.respond(resourceService.adminDownload(uuid,
                ResourceWebMapper.uidOf(authentication), ResourceWebMapper.isSuperAdmin(authentication)));
    }

    /** Admin: delete a resource (bytes + metadata); idempotent. */
    @DeleteMapping("/{uuid}")
    public ApiResponse<Void> delete(@PathVariable String uuid, Authentication authentication) {
        resourceService.adminDelete(uuid, ResourceWebMapper.uidOf(authentication),
                ResourceWebMapper.isSuperAdmin(authentication));
        return ApiResponse.success();
    }
}
