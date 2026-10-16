package cn.nitrowater.resource.web;

import cn.nitrowater.resource.application.ResourceException;
import cn.nitrowater.resource.application.ResourceService;
import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.web.dto.ResourceMetaResp;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Internal publish endpoint, used by operator tooling (e.g. {@code bin/sync-res.ps1}) to seed
 * platform-owned artefacts such as plugin {@code .jar} downloads.
 *
 * <p>This is not an end-user surface: it is reached directly on the service port (never through the
 * BFF) and guarded by a shared-secret token ({@code X-Internal-Token}) rather than a JWT. The
 * endpoint is disabled entirely when no token is configured.</p>
 */
@RestController
@RequestMapping("/internal/resource")
public class InternalPublishController {

    private static final String TOKEN_HEADER = "X-Internal-Token";

    private final ResourceService resourceService;
    private final ResourceProperties properties;

    public InternalPublishController(ResourceService resourceService, ResourceProperties properties) {
        this.resourceService = resourceService;
        this.properties = properties;
    }

    /**
     * Publishes (or re-publishes) a system resource from the raw request body. Re-publishing the same
     * {@code app + group + name} overwrites the existing object and keeps its download URL stable.
     *
     * @param app application / namespace (e.g. {@code nitrowater})
     * @param group grouping key within the app (e.g. plugin key)
     * @param name original file name (download name; drives the extension check)
     * @param version logical version recorded in {@code file_meta}, or {@code null}
     * @param token internal shared secret
     * @param contentType declared content type (recorded for reference only; never trusted)
     * @param body raw file bytes
     * @return the activated resource metadata
     */
    @PostMapping(path = "/publish", consumes = MediaType.ALL_VALUE)
    public ResourceMetaResp publish(
            @RequestParam String app,
            @RequestParam(name = "group", required = false) String group,
            @RequestParam String name,
            @RequestParam(required = false) String version,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestBody byte[] body) {
        requireToken(token);
        return ResourceWebMapper.toResp(resourceService.publishSystem(
                app, group, name, version, contentType, new ByteArrayInputStream(body), body.length));
    }

    /** Rejects the request unless the internal publish endpoint is enabled and the token matches. */
    private void requireToken(String token) {
        if (!properties.internalPublishEnabled()) {
            throw new ResourceException(HttpStatus.SERVICE_UNAVAILABLE, "internal_disabled",
                    "Internal publish endpoint is disabled");
        }
        if (token == null || !constantTimeEquals(token, properties.internalToken())) {
            throw new ResourceException(HttpStatus.UNAUTHORIZED, "bad_token", "Invalid internal token");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
