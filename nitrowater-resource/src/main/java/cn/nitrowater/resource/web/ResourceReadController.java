package cn.nitrowater.resource.web;

import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.core.infrastructure.aspect.RateLimit;
import cn.nitrowater.resource.service.ResourceService;
import cn.nitrowater.resource.service.dto.resp.ResourceMetaResp;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read + lifecycle endpoints.
 *
 * <ul>
 *   <li>{@code GET /api/public/resource/{uuid}} — anonymous read, PUBLIC resources only;</li>
 *   <li>{@code GET /api/public/resource/{uuid}/download} — anonymous forced download (attachment);</li>
 *   <li>{@code GET /api/public/resource/{uuid}/meta} — anonymous metadata, PUBLIC resources only;</li>
 *   <li>{@code GET /api/public/resources?app=&group=} — anonymous listing of a group's PUBLIC resources
 *       (no keyword filter: {@code %q%} is a table scan and must stay on authenticated surfaces);</li>
 *   <li>{@code GET /api/resource/{uuid}} — authenticated read (owner, or any PUBLIC resource);</li>
 *   <li>{@code GET /api/resource/{uuid}/download} — authenticated forced download (attachment);</li>
 *   <li>{@code GET /api/resource/{uuid}/meta} — authenticated metadata (owner, or any PUBLIC resource);</li>
 *   <li>{@code GET /api/resources?app=&group=} — authenticated listing (PUBLIC, plus the caller's own);</li>
 *   <li>{@code DELETE /api/resource/{uuid}} — owner deletes a resource (bytes + metadata).</li>
 * </ul>
 *
 * <p>This controller is deliberately thin: it only binds request parameters, delegates to
 * {@link ResourceService} and renders the returned service result. The read policy (presigned
 * redirect vs. proxied stream, media type, download disposition, cache directives) lives in the
 * service; here the outcome is merely translated into a transport response.</p>
 *
 * <p>Every JSON endpoint returns {@code ApiResponse<T>} explicitly, like the business API. The two
 * read endpoints are the deliberate exception: they answer a byte stream (or a 302 to storage), so
 * they cannot be enveloped — {@code ApiResponseAdvice} would not touch them anyway because they are
 * not rendered by the Jackson converter.</p>
 */
@RestController
@RequestMapping("/api")
public class ResourceReadController {

    private final ResourceService resourceService;

    public ResourceReadController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /** Anonymous read of a PUBLIC resource. */
    @RateLimit(key = "ip", permits = 300, window = 60)
    @GetMapping("/public/resource/{uuid}")
    public ResponseEntity<?> publicRead(@PathVariable String uuid) {
        return ResourceDownloadResponder.respond(resourceService.read(uuid, null));
    }

    /** Anonymous forced download of a PUBLIC resource (streamed as an attachment). */
    @RateLimit(key = "ip", permits = 300, window = 60)
    @GetMapping("/public/resource/{uuid}/download")
    public ResponseEntity<?> publicDownload(@PathVariable String uuid) {
        return ResourceDownloadResponder.respond(resourceService.download(uuid, null));
    }

    /** Anonymous metadata of a PUBLIC resource. */
    @RateLimit(key = "ip", permits = 300, window = 60)
    @GetMapping("/public/resource/{uuid}/meta")
    public ApiResponse<ResourceMetaResp> publicMeta(@PathVariable String uuid) {
        return ApiResponse.success(ResourceMetaResp.from(resourceService.meta(uuid, null)));
    }

    /** Anonymous paged listing of a group's PUBLIC resources. */
    @RateLimit(key = "ip", permits = 60, window = 60)
    @GetMapping("/public/resources")
    public ApiResponse<Page<ResourceMetaResp>> publicList(@RequestParam String app,
            @RequestParam(name = "group", required = false) String group,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(resourceService.list(app, group, null, null, page, size)
                .map(ResourceMetaResp::from));
    }

    /** Authenticated read of a resource the caller owns, or of any PUBLIC resource. */
    @GetMapping("/resource/{uuid}")
    public ResponseEntity<?> privateRead(@PathVariable String uuid, Authentication authentication) {
        return ResourceDownloadResponder.respond(resourceService.read(uuid, ResourceWebMapper.uidOf(authentication)));
    }

    /** Authenticated forced download of a resource the caller owns, or of any PUBLIC resource. */
    @GetMapping("/resource/{uuid}/download")
    public ResponseEntity<?> privateDownload(@PathVariable String uuid, Authentication authentication) {
        return ResourceDownloadResponder.respond(resourceService.download(uuid,
                ResourceWebMapper.uidOf(authentication)));
    }

    /** Authenticated metadata for a resource the caller owns, or for any PUBLIC resource. */
    @GetMapping("/resource/{uuid}/meta")
    public ApiResponse<ResourceMetaResp> privateMeta(@PathVariable String uuid, Authentication authentication) {
        return ApiResponse.success(ResourceMetaResp.from(
                resourceService.meta(uuid, ResourceWebMapper.uidOf(authentication))));
    }

    /** Authenticated paged listing: PUBLIC resources of the group, plus any the caller owns. */
    @GetMapping("/resources")
    public ApiResponse<Page<ResourceMetaResp>> privateList(@RequestParam String app,
            @RequestParam(name = "group", required = false) String group,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return ApiResponse.success(resourceService.list(app, group,
                        ResourceWebMapper.uidOf(authentication), keyword, page, size)
                .map(ResourceMetaResp::from));
    }

    /** Owner deletes a resource (bytes + metadata); idempotent. */
    @DeleteMapping("/resource/{uuid}")
    public ApiResponse<Void> delete(@PathVariable String uuid, Authentication authentication) {
        resourceService.delete(uuid, ResourceWebMapper.uidOf(authentication));
        return ApiResponse.success();
    }
}
