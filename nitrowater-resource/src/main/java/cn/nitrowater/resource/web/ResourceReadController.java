package cn.nitrowater.resource.web;

import cn.nitrowater.resource.domain.Resource;
import cn.nitrowater.resource.service.ResourceService;
import cn.nitrowater.resource.web.dto.ResourceMetaResp;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URI;

/**
 * Read endpoints.
 *
 * <ul>
 *   <li>{@code GET /api/public/resource/{uuid}} — anonymous read, PUBLIC resources only;</li>
 *   <li>{@code GET /api/resource/{uuid}} — authenticated read;</li>
 *   <li>{@code GET /api/resource/{uuid}/meta} — authenticated metadata.</li>
 * </ul>
 *
 * <p>When the backend supports presigning the response is a 302 to a presigned GET URL; otherwise
 * the bytes are streamed through this service.</p>
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
        return read(uuid, true);
    }

    /** Authenticated read of any active resource. */
    @GetMapping("/resource/{uuid}")
    public ResponseEntity<?> privateRead(@PathVariable String uuid) {
        return read(uuid, false);
    }

    /** Authenticated metadata for a resource. */
    @GetMapping("/resource/{uuid}/meta")
    public ResourceMetaResp meta(@PathVariable String uuid, @AuthenticationPrincipal Jwt jwt) {
        Resource resource = resourceService.requireReadable(uuid, false);
        return ResourceWebMapper.toMeta(resource, resourceService.presignedGetUrl(resource));
    }

    private ResponseEntity<?> read(String uuid, boolean anonymous) {
        Resource resource = resourceService.requireReadable(uuid, anonymous);
        String url = resourceService.presignedGetUrl(resource);
        if (url != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
        }
        InputStream stream = resourceService.openStream(resource);
        MediaType mediaType = resource.getMimeType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(resource.getMimeType());
        return ResponseEntity.ok().contentType(mediaType).body(new InputStreamResource(stream));
    }
}
