package cn.nitrowater.resource.web;

import cn.nitrowater.core.infrastructure.aspect.RateLimit;
import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.resource.infrastructure.exception.ResourceException;
import cn.nitrowater.resource.service.ResourceService;
import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.service.dto.resp.ResourceMetaResp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Internal publish endpoint, used by operator tooling (e.g. {@code bin/dev/sync-res.ps1}) to seed
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
     * @param request raw file bytes; {@code Content-Length} is mandatory and streamed straight to storage
     * @return the activated resource metadata
     */
    @RateLimit(key = "ip", permits = 30, window = 60)
    @PostMapping(path = "/publish", consumes = MediaType.ALL_VALUE)
    public ApiResponse<ResourceMetaResp> publish(
            @RequestParam String app,
            @RequestParam(name = "group", required = false) String group,
            @RequestParam String name,
            @RequestParam(required = false) String version,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            HttpServletRequest request) throws IOException {
        // Authorise BEFORE touching the body. With @RequestBody the whole payload was buffered by
        // Spring's argument resolver before this method ran, so an anonymous caller could force a
        // large allocation without ever presenting a token.
        if (!properties.internalPublishEnabled()) {
            throw new ResourceException(HttpStatus.SERVICE_UNAVAILABLE, "internal_disabled",
                    "Internal publish endpoint is disabled");
        }
        // Constant-time comparison: a byte-wise early exit would leak the shared secret.
        if(properties.internalToken() != null){
            if (token == null || !MessageDigest.isEqual(
                    token.getBytes(StandardCharsets.UTF_8),
                    properties.internalToken().getBytes(StandardCharsets.UTF_8))
            ) {
                throw new ResourceException(HttpStatus.UNAUTHORIZED, "bad_token", "Invalid internal token");
            }
        }

        // Plugin jars are the whole point of this endpoint, so they get the plugin cap instead of
        // the (much smaller) end-user upload cap; the admin upload path already does the same.
        long maxBytes = name != null && name.toLowerCase(Locale.ROOT).endsWith(".jar")
                ? properties.effectivePluginMaxSizeBytes()
                : properties.maxSizeBytes();
        // A declared length is required: it lets the cap be enforced before the first byte reaches
        // storage and lets the body stream through without ever being buffered in the heap.
        long declared = request.getContentLengthLong();
        if (declared < 0) {
            throw new ResourceException(HttpStatus.LENGTH_REQUIRED, "length_required",
                    "Content-Length is required");
        }
        if (declared > maxBytes) {
            throw new ResourceException(HttpStatus.CONTENT_TOO_LARGE, "too_large",
                    "Payload exceeds the " + maxBytes + "-byte limit");
        }
        return ApiResponse.success(ResourceMetaResp.from(resourceService.publishSystem(
                app, group, name, version, contentType, request.getInputStream(), declared, maxBytes)));
    }
}
