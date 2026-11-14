package cn.nitrowater.resource.web;

import cn.nitrowater.core.infrastructure.aspect.RateLimit;
import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.resource.service.PluginArtifactPolicyService;
import cn.nitrowater.resource.service.ResourceService;
import cn.nitrowater.resource.service.dto.resp.ResourceMetaResp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedInputStream;
import java.io.IOException;

/**
 * Admin surface for a plugin's downloadable artifacts (jars), mounted under
 * {@code /api/admin/plugin-resources}.
 *
 * <ul>
 *   <li>{@code GET /api/admin/plugin-resources/{key}} — the plugin's active artifacts, newest first;</li>
 *   <li>{@code POST /api/admin/plugin-resources/{key}?name=…&version=…} — publish a jar from the raw
 *       request body (re-publishing the same name overwrites in place and keeps the URL stable);</li>
 *   <li>{@code DELETE /api/admin/plugin-resources/{key}/{uuid}} — delete one artifact.</li>
 * </ul>
 *
 * <p>Unlike the generic file console, plugin artifacts are {@code SYSTEM} resources owned by nobody,
 * so a plain {@code ROLE_ADMIN} would neither see nor delete them there. This controller scopes every
 * operation to the plugin key and lets any admin manage its jars. The upload path is a raw body (not
 * multipart) so the payload can be streamed straight into storage; the strict jar allow-list lives in
 * {@link PluginArtifactPolicyService}.</p>
 *
 * <p>Role gate: {@code /api/admin/**} is already restricted by the filter chain, and the class-level
 * {@link PreAuthorize} states the same requirement at the endpoint itself.</p>
 */
@RestController
@RequestMapping("/api/admin/plugin-resources")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminPluginResourceController {

    /** Bytes peeked to verify the ZIP signature before the stream reaches storage. */
    private static final int MAGIC_HEAD_BYTES = 16;

    private final ResourceService resourceService;
    private final PluginArtifactPolicyService policy;

    public AdminPluginResourceController(ResourceService resourceService, PluginArtifactPolicyService policy) {
        this.resourceService = resourceService;
        this.policy = policy;
    }

    /** Paged list of the plugin's active artifacts (newest first) with public download links. */
    @GetMapping("/{key}")
    public ApiResponse<Page<ResourceMetaResp>> list(@PathVariable String key,
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(resourceService.listPluginArtifacts(key, keyword, page, size)
                .map(ResourceMetaResp::from));
    }

    /**
     * Publishes a plugin jar from the raw request body.
     *
     * @param key plugin key (also the resource group)
     * @param name original file name; must end with {@code .jar}
     * @param version logical version, or {@code null}
     * @param request raw body; {@code Content-Length} is required
     * @return the activated resource metadata
     * @throws IOException when the request body cannot be read
     */
    @RateLimit(key = "ip", permits = 10, window = 300)
    @PostMapping(path = "/{key}", consumes = MediaType.ALL_VALUE)
    public ApiResponse<ResourceMetaResp> upload(
            @PathVariable String key,
            @RequestParam String name,
            @RequestParam(required = false) String version,
            HttpServletRequest request) throws IOException {
        long size = request.getContentLengthLong();
        policy.requireSize(size);
        String safeName = policy.sanitizeName(name);

        BufferedInputStream body = new BufferedInputStream(request.getInputStream(), 64);
        body.mark(MAGIC_HEAD_BYTES);
        byte[] head = body.readNBytes(MAGIC_HEAD_BYTES);
        body.reset();
        policy.requireJarMagic(head);

        return ApiResponse.success(ResourceMetaResp.from(
                resourceService.publishPluginArtifact(key, safeName, version, body, size)));
    }

    /** Deletes one artifact, after checking that it belongs to this plugin. */
    @DeleteMapping("/{key}/{uuid}")
    public ApiResponse<Void> delete(@PathVariable String key, @PathVariable String uuid) {
        resourceService.deletePluginArtifact(key, uuid);
        return ApiResponse.success();
    }
}
