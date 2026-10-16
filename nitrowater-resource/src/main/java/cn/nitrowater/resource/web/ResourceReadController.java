package cn.nitrowater.resource.web;

import cn.nitrowater.resource.application.ResourceService;
import cn.nitrowater.resource.web.dto.ResourceMetaResp;
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
 * Read + lifecycle endpoints.
 *
 * <ul>
 *   <li>{@code GET /api/public/resource/{uuid}} — anonymous read, PUBLIC resources only;</li>
 *   <li>{@code GET /api/public/resource/{uuid}/download} — anonymous forced download (attachment);</li>
 *   <li>{@code GET /api/public/resource/{uuid}/meta} — anonymous metadata, PUBLIC resources only;</li>
 *   <li>{@code GET /api/public/resources?app=&group=} — anonymous listing of a group's PUBLIC resources;</li>
 *   <li>{@code GET /api/resource/{uuid}} — authenticated read (owner, or any PUBLIC resource);</li>
 *   <li>{@code GET /api/resource/{uuid}/download} — authenticated forced download (attachment);</li>
 *   <li>{@code GET /api/resource/{uuid}/meta} — authenticated metadata (owner, or any PUBLIC resource);</li>
 *   <li>{@code GET /api/resources?app=&group=} — authenticated listing (PUBLIC, plus the caller's own);</li>
 *   <li>{@code DELETE /api/resource/{uuid}} — owner deletes a resource (bytes + metadata).</li>
 * </ul>
 *
 * <p>This controller is deliberately thin: it only binds request parameters, delegates to
 * {@link ResourceService} and renders the returned application result. The read policy (presigned
 * redirect vs. proxied stream, media type, download disposition, cache directives) lives in the
 * service; here the outcome is merely translated into a transport response.</p>
 */
@RestController
@RequestMapping("/api")
public class ResourceReadController {

    private final ResourceService resourceService;

    public ResourceReadController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /** Anonymous read of a PUBLIC resource. */
    @GetMapping("/public/resource/{uuid}")
    public ResponseEntity<?> publicRead(@PathVariable String uuid) {
        return ResourceDownloadResponder.respond(resourceService.read(uuid, null));
    }

    /** Anonymous forced download of a PUBLIC resource (streamed as an attachment). */
    @GetMapping("/public/resource/{uuid}/download")
    public ResponseEntity<?> publicDownload(@PathVariable String uuid) {
        return ResourceDownloadResponder.respond(resourceService.download(uuid, null));
    }

    /** Anonymous metadata of a PUBLIC resource. */
    @GetMapping("/public/resource/{uuid}/meta")
    public ResourceMetaResp publicMeta(@PathVariable String uuid) {
        return ResourceWebMapper.toResp(resourceService.meta(uuid, null));
    }

    /** Anonymous listing of a group's PUBLIC resources. */
    @GetMapping("/public/resources")
    public List<ResourceMetaResp> publicList(@RequestParam String app,
            @RequestParam(name = "group", required = false) String group) {
        return resourceService.list(app, group, null).stream().map(ResourceWebMapper::toResp).toList();
    }

    /** Authenticated read of a resource the caller owns, or of any PUBLIC resource. */
    @GetMapping("/resource/{uuid}")
    public ResponseEntity<?> privateRead(@PathVariable String uuid, @AuthenticationPrincipal Jwt jwt) {
        return ResourceDownloadResponder.respond(resourceService.read(uuid, ResourceWebMapper.uidOf(jwt)));
    }

    /** Authenticated forced download of a resource the caller owns, or of any PUBLIC resource. */
    @GetMapping("/resource/{uuid}/download")
    public ResponseEntity<?> privateDownload(@PathVariable String uuid, @AuthenticationPrincipal Jwt jwt) {
        return ResourceDownloadResponder.respond(resourceService.download(uuid, ResourceWebMapper.uidOf(jwt)));
    }

    /** Authenticated metadata for a resource the caller owns, or for any PUBLIC resource. */
    @GetMapping("/resource/{uuid}/meta")
    public ResourceMetaResp privateMeta(@PathVariable String uuid, @AuthenticationPrincipal Jwt jwt) {
        return ResourceWebMapper.toResp(resourceService.meta(uuid, ResourceWebMapper.uidOf(jwt)));
    }

    /** Authenticated listing: PUBLIC resources of the group, plus any the caller owns. */
    @GetMapping("/resources")
    public List<ResourceMetaResp> privateList(@RequestParam String app,
            @RequestParam(name = "group", required = false) String group, @AuthenticationPrincipal Jwt jwt) {
        return resourceService.list(app, group, ResourceWebMapper.uidOf(jwt)).stream()
                .map(ResourceWebMapper::toResp).toList();
    }

    /** Owner deletes a resource (bytes + metadata); idempotent. */
    @DeleteMapping("/resource/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable String uuid, @AuthenticationPrincipal Jwt jwt) {
        resourceService.delete(uuid, ResourceWebMapper.uidOf(jwt));
        return ResponseEntity.noContent().build();
    }
}
