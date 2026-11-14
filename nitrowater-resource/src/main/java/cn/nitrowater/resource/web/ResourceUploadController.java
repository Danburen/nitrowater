package cn.nitrowater.resource.web;

import cn.nitrowater.core.infrastructure.aspect.RateLimit;
import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.resource.infrastructure.exception.ResourceException;
import cn.nitrowater.resource.service.ResourceService;
import cn.nitrowater.resource.service.dto.UploadPolicyCommand;
import cn.nitrowater.resource.service.dto.resp.ResourceMetaResp;
import cn.nitrowater.resource.service.dto.resp.UploadCallbackReq;
import cn.nitrowater.resource.service.dto.resp.UploadPolicyReq;
import cn.nitrowater.resource.service.dto.resp.UploadPolicyResp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Admin upload endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/resource/upload/policy} — register a pending resource; returns a presigned
 *       PUT URL when direct storage uploads are enabled, otherwise {@code uploadUrl=null} and the
 *       client must use the proxy PUT below;</li>
 *   <li>{@code POST /api/resource/upload/callback} — confirm a direct upload (validate + activate);</li>
 *   <li>{@code PUT  /api/resource/upload/{uuid}} — proxy upload, streamed straight to storage. This
 *       is the single upload entry point for the default LOCAL backend.</li>
 * </ul>
 *
 * <p>Thin by design: it binds the request, translates it into an application command and maps the
 * returned application result into a wire response.</p>
 *
 * <p>This whole surface is restricted to {@code ROLE_ADMIN}: the filter chain enforces it for the
 * {@code /api/resource/upload/**} prefix (the floor), and the class-level {@link PreAuthorize} repeats
 * it at the endpoints. {@code ROLE_SUPER_ADMIN} tokens already carry {@code ROLE_ADMIN}, so one
 * authority covers both.</p>
 *
 * <p>This whole surface can be switched off with {@code app.resource.upload-enabled=false}
 * ({@code RESOURCE_UPLOAD_ENABLED}): the endpoints stay mapped but answer
 * {@code 503 uploads_disabled}. Admin plugin publishing ({@code /api/admin/plugin-resources/**}) and
 * internal publishing ({@code /internal/resource/publish}) are separate surfaces and are unaffected.</p>
 */
@RestController
@RequestMapping("/api/resource/upload")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class ResourceUploadController {

    private final ResourceService resourceService;
    private final boolean uploadEnabled;

    public ResourceUploadController(ResourceService resourceService,
            @Value("${app.resource.upload-enabled:true}") boolean uploadEnabled) {
        this.resourceService = resourceService;
        this.uploadEnabled = uploadEnabled;
    }

    /** @throws ResourceException 503 when uploads are switched off for this deployment */
    private void requireUploadsEnabled() {
        if (!uploadEnabled) {
            throw new ResourceException(HttpStatus.SERVICE_UNAVAILABLE, "uploads_disabled",
                    "Uploads are disabled");
        }
    }

    /** Registers a pending resource and returns a presigned PUT URL (or the proxy fallback). */
    @RateLimit(key = "ip", permits = 20, window = 300)
    @PostMapping("/policy")
    public ApiResponse<UploadPolicyResp> policy(@Valid @RequestBody UploadPolicyReq request,
            Authentication authentication) {
        requireUploadsEnabled();
        UploadPolicyCommand command = new UploadPolicyCommand(
                request.app(),
                request.groupKey(),
                request.fileName(),
                request.contentType(),
                request.sizeBytes(),
                ResourceWebMapper.visibilityOf(request.visibility()),
                ResourceWebMapper.backendOf(request.backend()));
        return ApiResponse.success(UploadPolicyResp.from(
                resourceService.createUploadPolicy(command, ResourceWebMapper.uidOf(authentication))));
    }

    /** Confirms a finished direct upload and returns the activated resource metadata. */
    @RateLimit(key = "ip", permits = 20, window = 300)
    @PostMapping("/callback")
    public ApiResponse<ResourceMetaResp> callback(@Valid @RequestBody UploadCallbackReq request,
            Authentication authentication) {
        requireUploadsEnabled();
        return ApiResponse.success(ResourceMetaResp.from(
                resourceService.confirmUpload(request.uuid(), ResourceWebMapper.uidOf(authentication))));
    }

    /**
     * Streams the raw body straight into storage — the single upload entry point for the LOCAL
     * backend.
     *
     * <p>The body is never buffered in heap: a large artifact must not become a {@code byte[]}.
     * {@code Content-Length} is mandatory so the size cap is enforced before anything is written and
     * a truncated upload is rejected up front.</p>
     *
     * @param uuid pending resource identifier
     * @param request raw request (body stream + declared length / content type)
     * @param jwt caller token
     * @return the activated resource metadata
     * @throws IOException when the request body cannot be read
     */
    @RateLimit(key = "ip", permits = 10, window = 300)
    @PutMapping(path = "/{uuid}", consumes = MediaType.ALL_VALUE)
    public ApiResponse<ResourceMetaResp> proxyUpload(@PathVariable String uuid, HttpServletRequest request,
            Authentication authentication) throws IOException {
        requireUploadsEnabled();
        long size = request.getContentLengthLong();
        if (size <= 0) {
            throw new ResourceException(HttpStatus.LENGTH_REQUIRED, "length_required",
                    "Content-Length is required and must be positive");
        }
        return ApiResponse.success(ResourceMetaResp.from(resourceService.putBytes(uuid,
                ResourceWebMapper.uidOf(authentication), request.getInputStream(), size,
                request.getContentType())));
    }
}
