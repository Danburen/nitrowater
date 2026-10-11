package cn.nitrowater.resource.web;

import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.domain.Resource;
import cn.nitrowater.resource.domain.ResourceVisibility;
import cn.nitrowater.resource.service.ResourceService;
import cn.nitrowater.resource.web.dto.ResourceMetaResp;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * Read + lifecycle endpoints.
 *
 * <ul>
 *   <li>{@code GET /api/public/resource/{uuid}} — anonymous read, PUBLIC resources only;</li>
 *   <li>{@code GET /api/public/resource/{uuid}/meta} — anonymous metadata, PUBLIC resources only;</li>
 *   <li>{@code GET /api/resource/{uuid}} — authenticated read;</li>
 *   <li>{@code GET /api/resource/{uuid}/meta} — authenticated metadata;</li>
 *   <li>{@code DELETE /api/resource/{uuid}} — owner deletes a resource (bytes + metadata).</li>
 * </ul>
 *
 * <p>When the backend supports presigning the response is a 302 to a presigned GET URL (Redis-cached
 * by {@code PresignedUrlCache}); otherwise the bytes are streamed through this service. Responses
 * carry a {@code Cache-Control} so browsers may reuse the redirect/bytes within the signature window.</p>
 */
@RestController
@RequestMapping("/api")
public class ResourceReadController {

    private final ResourceService resourceService;
    private final ResourceProperties properties;

    public ResourceReadController(ResourceService resourceService, ResourceProperties properties) {
        this.resourceService = resourceService;
        this.properties = properties;
    }

    /** Anonymous read of a PUBLIC resource. */
    @GetMapping("/public/resource/{uuid}")
    public ResponseEntity<?> publicRead(@PathVariable String uuid) {
        return read(uuid, true);
    }

    /** Anonymous metadata of a PUBLIC resource. */
    @GetMapping("/public/resource/{uuid}/meta")
    public ResourceMetaResp publicMeta(@PathVariable String uuid) {
        return meta(uuid, true);
    }

    /** Authenticated read of any active resource. */
    @GetMapping("/resource/{uuid}")
    public ResponseEntity<?> privateRead(@PathVariable String uuid) {
        return read(uuid, false);
    }

    /** Authenticated metadata for a resource. */
    @GetMapping("/resource/{uuid}/meta")
    public ResourceMetaResp privateMeta(@PathVariable String uuid) {
        return meta(uuid, false);
    }

    /** Owner deletes a resource (bytes + metadata); idempotent. */
    @DeleteMapping("/resource/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable String uuid, @AuthenticationPrincipal Jwt jwt) {
        resourceService.delete(uuid, ResourceWebMapper.uidOf(jwt));
        return ResponseEntity.noContent().build();
    }

    private ResourceMetaResp meta(String uuid, boolean anonymous) {
        Resource resource = resourceService.requireReadable(uuid, anonymous);
        return ResourceWebMapper.toMeta(resource, resourceService.presignedGetUrl(resource));
    }

    private ResponseEntity<?> read(String uuid, boolean anonymous) {
        Resource resource = resourceService.requireReadable(uuid, anonymous);
        CacheControl cacheControl = cacheControl(resource);
        String url = resourceService.presignedGetUrl(resource);
        if (url != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).cacheControl(cacheControl).build();
        }
        InputStream stream = resourceService.openStream(resource);
        MediaType mediaType = resource.getMimeType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(resource.getMimeType());
        long contentLength = resource.getSizeBytes() == null ? -1 : resource.getSizeBytes();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(contentLength)
                .cacheControl(cacheControl)
                .body(new InputStreamResource(stream));
    }

    /** Browsers may cache the result for as long as the signed URL / service URL stays valid. */
    private CacheControl cacheControl(Resource resource) {
        long ttl = Math.max(1, properties.presignExpirySeconds() - properties.effectiveCacheSafetyMarginSeconds());
        CacheControl control = CacheControl.maxAge(Duration.ofSeconds(ttl));
        return resource.getVisibility() == ResourceVisibility.PUBLIC
                ? control.cachePublic()
                : control.cachePrivate();
    }
}
