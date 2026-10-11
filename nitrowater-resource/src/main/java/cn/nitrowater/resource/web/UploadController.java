package cn.nitrowater.resource.web;

import cn.nitrowater.resource.domain.Resource;
import cn.nitrowater.resource.application.ResourceService;
import cn.nitrowater.resource.web.dto.UploadCallbackReq;
import cn.nitrowater.resource.web.dto.UploadPolicyReq;
import cn.nitrowater.resource.web.dto.UploadPolicyResp;
import cn.nitrowater.resource.web.dto.ResourceMetaResp;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
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
 */
@RestController
@RequestMapping("/api/resource/upload")
public class UploadController {

    private final ResourceService resourceService;

    public UploadController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * Registers a pending resource and returns a presigned PUT URL (or the proxy fallback).
     * @param request   {@link UploadPolicyReq}
     * @param jwt       {@link Jwt} of the authenticated user
     * @return          {@link UploadPolicyResp} with the presigned URL and other metadata
     */
    @PostMapping("/policy")
    public UploadPolicyResp policy(@Valid @RequestBody UploadPolicyReq request, @AuthenticationPrincipal Jwt jwt) {
        ResourceService.UploadPolicyCommand command = new ResourceService.UploadPolicyCommand(
                request.app(),
                request.fileName(),
                request.contentType(),
                request.sizeBytes(),
                ResourceWebMapper.visibilityOf(request.visibility()),
                ResourceWebMapper.backendOf(request.backend()));
        ResourceService.UploadPolicyResult result =
                resourceService.createUploadPolicy(command, ResourceWebMapper.uidOf(jwt));
        return new UploadPolicyResp(
                result.uuid(), result.uploadUrl(), result.method(),
                result.backend().name(), result.expiresInSeconds());
    }

    /** Confirms a finished direct upload and returns the activated resource metadata. */
    @PostMapping("/callback")
    public ResourceMetaResp callback(@Valid @RequestBody UploadCallbackReq request, @AuthenticationPrincipal Jwt jwt) {
        Resource resource = resourceService.confirmUpload(request.uuid(), ResourceWebMapper.uidOf(jwt));
        return ResourceWebMapper.toMeta(resource, resourceService.presignedGetUrl(resource));
    }

    /** Proxy upload for backends without presigning; body is the raw file. */
    @PutMapping(path = "/{uuid}", consumes = MediaType.ALL_VALUE)
    public ResourceMetaResp proxyUpload(@PathVariable String uuid, @RequestBody byte[] body,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, @AuthenticationPrincipal Jwt jwt) {
        Resource resource = resourceService.putBytes(uuid, ResourceWebMapper.uidOf(jwt),
                new ByteArrayInputStream(body), body.length, contentType);
        return ResourceWebMapper.toMeta(resource, resourceService.presignedGetUrl(resource));
    }
}
