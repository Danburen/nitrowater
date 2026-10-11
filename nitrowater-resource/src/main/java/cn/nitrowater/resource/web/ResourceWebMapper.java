package cn.nitrowater.resource.web;

import cn.nitrowater.resource.domain.Resource;
import cn.nitrowater.resource.domain.ResourceVisibility;
import cn.nitrowater.resource.domain.StorageBackend;
import cn.nitrowater.resource.application.ResourceException;
import cn.nitrowater.resource.web.dto.ResourceMetaResp;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/** Small mapping helpers shared by the resource controllers. */
final class ResourceWebMapper {

    private ResourceWebMapper() {
    }

    /** Builds a metadata view, embedding a read URL (or the service read path when not presignable). */
    static ResourceMetaResp toMeta(Resource resource, String url) {
        String effectiveUrl = url != null ? url : serviceReadPath(resource);
        return new ResourceMetaResp(
                resource.getUuid(),
                resource.getApp(),
                resource.getResourceType().name(),
                resource.getMimeType(),
                resource.getSizeBytes(),
                resource.getVisibility().name(),
                resource.getStatus().name(),
                effectiveUrl);
    }

    /**
     * @return the same-origin service read path, used when the backend cannot presign (e.g. LOCAL),
     *         so callers always receive a usable URL
     */
    private static String serviceReadPath(Resource resource) {
        String prefix = resource.getVisibility() == ResourceVisibility.PUBLIC ? "/api/public" : "/api";
        return prefix + "/resource/" + resource.getUuid();
    }

    /** @return the caller's numeric uid from the {@code uid} claim (falling back to {@code sub}) */
    static Long uidOf(Jwt jwt) {
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) {
            uid = jwt.getSubject();
        }
        if (uid == null || uid.isBlank()) {
            throw new ResourceException(HttpStatus.UNAUTHORIZED, "no_uid", "Token carries no uid");
        }
        try {
            return Long.valueOf(uid);
        } catch (NumberFormatException ex) {
            throw new ResourceException(HttpStatus.UNAUTHORIZED, "bad_uid", "Token uid is not numeric");
        }
    }

    /** @return {@code PUBLIC} only for the literal "public"; anything else is {@code PRIVATE} */
    static ResourceVisibility visibilityOf(String visibility) {
        return "public".equalsIgnoreCase(visibility) ? ResourceVisibility.PUBLIC : ResourceVisibility.PRIVATE;
    }

    /** @return the requested backend, or {@code null} to fall back to the configured default */
    static StorageBackend backendOf(String backend) {
        if (backend == null || backend.isBlank()) {
            return null;
        }
        return switch (backend.toLowerCase()) {
            case "local" -> StorageBackend.LOCAL;
            case "cos" -> StorageBackend.COS;
            case "s3", "minio" -> StorageBackend.S3;
            default -> null;
        };
    }
}
