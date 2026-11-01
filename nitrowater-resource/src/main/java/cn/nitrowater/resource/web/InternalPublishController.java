package cn.nitrowater.resource.web;

import cn.nitrowater.resource.application.ResourceException;
import cn.nitrowater.resource.application.ResourceService;
import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.web.dto.ResourceMetaResp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
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
     * @param request raw file bytes; streamed straight to storage when Content-Length is present
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
            HttpServletRequest request) throws IOException {
        // Authorise BEFORE touching the body. With @RequestBody the whole payload was buffered by
        // Spring's argument resolver before this method ran, so an anonymous caller could force a
        // large allocation without ever presenting a token.
        requireToken(token);

        // Plugin jars are the whole point of this endpoint, so they get the plugin cap instead of
        // the (much smaller) end-user upload cap; the admin upload path already does the same.
        long maxBytes = isPluginArtifact(name)
                ? properties.effectivePluginMaxSizeBytes()
                : properties.maxSizeBytes();
        long declared = request.getContentLengthLong();
        if (declared > maxBytes) {
            throw tooLarge(maxBytes);
        }
        if (declared >= 0) {
            // Stream straight into storage: buffering a 64 MiB jar as byte[] was a needless heap
            // spike, and publishSystem already accepts a sized stream.
            return ResourceWebMapper.toResp(resourceService.publishSystem(
                    app, group, name, version, contentType, request.getInputStream(), declared, maxBytes));
        }
        byte[] body = readBody(request, maxBytes);
        return ResourceWebMapper.toResp(resourceService.publishSystem(
                app, group, name, version, contentType, new ByteArrayInputStream(body), body.length, maxBytes));
    }

    /** @return whether the published name is a plugin artifact (.jar) */
    private static boolean isPluginArtifact(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    /**
     * Reads the payload for a chunked request (no {@code Content-Length}), refusing anything above
     * the effective limit for this publication.
     *
     * @param request the publish request whose body carries the file
     * @param maxBytes maximum accepted size for this publication
     * @return the body bytes, never larger than {@code maxBytes}
     */
    private byte[] readBody(HttpServletRequest request, long maxBytes) throws IOException {
        if (request.getContentLengthLong() > maxBytes) {
            throw tooLarge(maxBytes);
        }
        // Cap the read too: a chunked upload carries no Content-Length.
        long cap = Math.min(maxBytes + 1, Integer.MAX_VALUE - 8);
        byte[] body = request.getInputStream().readNBytes((int) cap);
        if (body.length > maxBytes) {
            throw tooLarge(maxBytes);
        }
        return body;
    }

    private static ResourceException tooLarge(long maxBytes) {
        return new ResourceException(HttpStatus.PAYLOAD_TOO_LARGE, "payload_too_large",
                "Payload exceeds the " + maxBytes + "-byte limit");
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
