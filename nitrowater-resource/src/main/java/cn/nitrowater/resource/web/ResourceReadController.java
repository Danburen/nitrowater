package cn.nitrowater.resource.web;

import cn.nitrowater.resource.application.ResourceService;
import cn.nitrowater.resource.application.dto.ResourceReadResult;
import cn.nitrowater.resource.web.dto.ResourceMetaResp;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Read + lifecycle endpoints.
 *
 * <ul>
 *   <li>{@code GET /api/public/resource/{uuid}} — anonymous read, PUBLIC resources only;</li>
 *   <li>{@code GET /api/public/resource/{uuid}/meta} — anonymous metadata, PUBLIC resources only;</li>
 *   <li>{@code GET /api/resource/{uuid}} — authenticated read (owner, or any PUBLIC resource);</li>
 *   <li>{@code GET /api/resource/{uuid}/meta} — authenticated metadata (owner, or any PUBLIC resource);</li>
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
        return respond(resourceService.read(uuid, null));
    }

    /** Anonymous metadata of a PUBLIC resource. */
    @GetMapping("/public/resource/{uuid}/meta")
    public ResourceMetaResp publicMeta(@PathVariable String uuid) {
        return ResourceWebMapper.toResp(resourceService.meta(uuid, null));
    }

    /** Authenticated read of a resource the caller owns, or of any PUBLIC resource. */
    @GetMapping("/resource/{uuid}")
    public ResponseEntity<?> privateRead(@PathVariable String uuid, @AuthenticationPrincipal Jwt jwt) {
        return respond(resourceService.read(uuid, ResourceWebMapper.uidOf(jwt)));
    }

    /** Authenticated metadata for a resource the caller owns, or for any PUBLIC resource. */
    @GetMapping("/resource/{uuid}/meta")
    public ResourceMetaResp privateMeta(@PathVariable String uuid, @AuthenticationPrincipal Jwt jwt) {
        return ResourceWebMapper.toResp(resourceService.meta(uuid, ResourceWebMapper.uidOf(jwt)));
    }

    /** Owner deletes a resource (bytes + metadata); idempotent. */
    @DeleteMapping("/resource/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable String uuid, @AuthenticationPrincipal Jwt jwt) {
        resourceService.delete(uuid, ResourceWebMapper.uidOf(jwt));
        return ResponseEntity.noContent().build();
    }

    /** Renders a service read outcome as an HTTP response (the only mapping this layer owns). */
    private static ResponseEntity<?> respond(ResourceReadResult result) {
        CacheControl cacheControl = result.cacheControl();
        if (result.redirectUrl() != null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(result.redirectUrl()))
                    .cacheControl(cacheControl)
                    .build();
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(result.contentType())
                .contentLength(result.contentLength())
                .cacheControl(cacheControl);
        if (result.attachment()) {
            // Force a download: a stored file (e.g. an SVG declaring HTML) must never render in the
            // browser, which would turn the shared resource origin into a stored-XSS sink.
            builder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment");
        }
        return builder.body(new InputStreamResource(result.stream()));
    }
}
