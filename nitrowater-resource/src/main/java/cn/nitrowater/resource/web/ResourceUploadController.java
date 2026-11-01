package cn.nitrowater.resource.web;

import cn.nitrowater.resource.application.ResourceException;
import cn.nitrowater.resource.application.ResourceService;
import cn.nitrowater.resource.application.dto.UploadPolicyCommand;
import cn.nitrowater.resource.application.dto.UploadPolicyResult;
import cn.nitrowater.resource.web.dto.ResourceMetaResp;
import cn.nitrowater.resource.web.dto.UploadCallbackReq;
import cn.nitrowater.resource.web.dto.UploadPolicyReq;
import cn.nitrowater.resource.web.dto.UploadPolicyResp;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;

/**
 * Authenticated upload endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/resource/upload/policy} — register a pending resource, return a presigned PUT URL;</li>
 *   <li>{@code POST /api/resource/upload/callback} — confirm a direct upload (validate + activate);</li>
 *   <li>{@code PUT  /api/resource/upload/{uuid}} — proxy upload for backends without presigning.</li>
 * </ul>
 *
 * <p>Thin by design: it binds the request, translates it into an application command and maps the
 * returned application result into a wire response.</p>
 *
 * <p>This whole surface can be switched off with {@code app.resource.user-upload-enabled=false}
 * ({@code RESOURCE_USER_UPLOAD}): the endpoints stay mapped but answer {@code 503 uploads_disabled}.
 * Admin plugin publishing ({@code /api/admin/plugin-resources/**}) and internal publishing
 * ({@code /internal/resource/publish}) are separate surfaces and are unaffected - that is what lets
 * us defer "user uploads + moderation flow" without losing plugin jar delivery.</p>
 */
@RestController
@RequestMapping("/api/resource/upload")
public class ResourceUploadController {

    private final ResourceService resourceService;
    private final boolean userUploadEnabled;

    public ResourceUploadController(ResourceService resourceService,
            @Value("${app.resource.user-upload-enabled:true}") boolean userUploadEnabled) {
        this.resourceService = resourceService;
        this.userUploadEnabled = userUploadEnabled;
    }

    /** @throws ResourceException 503 when user uploads are switched off for this deployment */
    private void requireUploadsEnabled() {
        if (!userUploadEnabled) {
            throw new ResourceException(HttpStatus.SERVICE_UNAVAILABLE, "uploads_disabled",
                    "User uploads are disabled");
        }
    }

    /** Registers a pending resource and returns a presigned PUT URL (or the proxy fallback). */
    @PostMapping("/policy")
    public UploadPolicyResp policy(@Valid @RequestBody UploadPolicyReq request, @AuthenticationPrincipal Jwt jwt) {
        requireUploadsEnabled();
        UploadPolicyCommand command = new UploadPolicyCommand(
                request.app(),
                request.groupKey(),
                request.fileName(),
                request.contentType(),
                request.sizeBytes(),
                ResourceWebMapper.visibilityOf(request.visibility()),
                ResourceWebMapper.backendOf(request.backend()));
        UploadPolicyResult result = resourceService.createUploadPolicy(command, ResourceWebMapper.uidOf(jwt));
        return new UploadPolicyResp(
                result.uuid(), result.uploadUrl(), result.method(),
                result.backend().name(), result.expiresInSeconds());
    }

    /** Confirms a finished direct upload and returns the activated resource metadata. */
    @PostMapping("/callback")
    public ResourceMetaResp callback(@Valid @RequestBody UploadCallbackReq request, @AuthenticationPrincipal Jwt jwt) {
        requireUploadsEnabled();
        return ResourceWebMapper.toResp(resourceService.confirmUpload(request.uuid(), ResourceWebMapper.uidOf(jwt)));
    }

    /** Proxy upload for backends without presigning; body is the raw file. */
    @PutMapping(path = "/{uuid}", consumes = MediaType.ALL_VALUE)
    public ResourceMetaResp proxyUpload(@PathVariable String uuid, @RequestBody byte[] body,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, @AuthenticationPrincipal Jwt jwt) {
        requireUploadsEnabled();
        return ResourceWebMapper.toResp(resourceService.putBytes(uuid, ResourceWebMapper.uidOf(jwt),
                new ByteArrayInputStream(body), body.length, contentType));
    }
}
