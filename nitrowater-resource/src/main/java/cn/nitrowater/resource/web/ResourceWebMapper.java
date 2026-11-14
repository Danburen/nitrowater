package cn.nitrowater.resource.web;

import cn.nitrowater.core.infrastructure.security.CurrentUser;
import cn.nitrowater.resource.infrastructure.exception.ResourceException;
import cn.nitrowater.resource.infrastructure.domain.ResourceStatus;
import cn.nitrowater.resource.infrastructure.domain.ResourceVisibility;
import cn.nitrowater.resource.infrastructure.domain.SourceType;
import cn.nitrowater.resource.infrastructure.domain.StorageBackend;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.security.Principal;
import java.util.OptionalLong;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Request-side helpers shared by the resource controllers: the caller's identity from the
 * authenticated principal and the optional filter values from query parameters. Response mapping
 * lives on the response records themselves ({@code XxxResp.from(serviceDto)}), so this class never
 * knows the wire shapes.
 *
 * <p>The uid rule is core's {@link CurrentUser} (one implementation for the whole stack); only the
 * 401 error code stays local to this module.</p>
 */
final class ResourceWebMapper {

    private ResourceWebMapper() {
    }

    /**
     * @param principal authenticated principal ({@code Authentication} or {@code Jwt})
     * @return the caller's numeric uid from the {@code uid} claim, falling back to {@code sub}
     * @throws ResourceException 401 when the token carries no numeric uid
     */
    static Long uidOf(Principal principal) {
        OptionalLong uid = CurrentUser.uidOf(principal);
        if (uid.isEmpty()) {
            throw new ResourceException(HttpStatus.UNAUTHORIZED, "no_uid", "Token carries no uid");
        }
        return uid.getAsLong();
    }

    /** @return {@code PUBLIC} only for the literal "public"; anything else is {@code PRIVATE} */
    static ResourceVisibility visibilityOf(String visibility) {
        return "public".equalsIgnoreCase(visibility) ? ResourceVisibility.PUBLIC : ResourceVisibility.PRIVATE;
    }

    /**
     * @param authentication caller's authorities (the filter chain maps {@code roles} here)
     * @return whether the caller holds {@code ROLE_SUPER_ADMIN} (the file-management super-admin)
     */
    static boolean isSuperAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_SUPER_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses the optional {@code status} filter: a {@link ResourceStatus} name (case-insensitive) or
     * its numeric value; blank means "no filter".
     *
     * @param status raw query value, or {@code null}
     * @return the parsed status, or {@code null} when blank
     * @throws ResourceException 400 when the value is not a known status
     */
    static ResourceStatus statusOf(String status) {
        if (StringUtil.isBlank(status)) {
            return null;
        }
        String value = status.trim();
        for (ResourceStatus candidate : ResourceStatus.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        try {
            return ResourceStatus.fromValue(Integer.parseInt(value));
        } catch (RuntimeException ex) {
            throw new ResourceException(HttpStatus.BAD_REQUEST, "invalid_status", "Unknown status: " + status);
        }
    }

    /**
     * Parses the optional {@code sourceType} filter: a {@link SourceType} name (case-insensitive) or
     * its numeric value; blank means "no filter".
     *
     * @param sourceType raw query value, or {@code null}
     * @return the parsed source type, or {@code null} when blank
     * @throws ResourceException 400 when the value is not a known source type
     */
    static SourceType sourceTypeOf(String sourceType) {
        if (StringUtil.isBlank(sourceType)) {
            return null;
        }
        String value = sourceType.trim();
        for (SourceType candidate : SourceType.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        try {
            return SourceType.fromValue(Integer.parseInt(value));
        } catch (RuntimeException ex) {
            throw new ResourceException(HttpStatus.BAD_REQUEST, "invalid_source_type",
                    "Unknown source type: " + sourceType);
        }
    }

    /**
     * @param backend requested backend ({@code local} / {@code cos})
     * @return the requested backend, or {@code null} to fall back to the configured default
     * @throws ResourceException 400 when the value names no supported backend
     */
    static StorageBackend backendOf(String backend) {
        if (StringUtil.isBlank(backend)) {
            return null;
        }
        return switch (backend.toLowerCase()) {
            case "local" -> StorageBackend.LOCAL;
            case "cos" -> StorageBackend.COS;
            default -> throw new ResourceException(HttpStatus.BAD_REQUEST, "invalid_backend",
                    "Unknown backend: " + backend + " (supported: local, cos)");
        };
    }
}
