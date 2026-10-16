package cn.nitrowater.resource.web;

import cn.nitrowater.resource.application.ResourceException;
import cn.nitrowater.resource.application.dto.ResourceMeta;
import cn.nitrowater.resource.domain.ResourceStatus;
import cn.nitrowater.resource.domain.ResourceVisibility;
import cn.nitrowater.resource.domain.SourceType;
import cn.nitrowater.resource.domain.StorageBackend;
import cn.nitrowater.resource.web.dto.OptionResp;
import cn.nitrowater.resource.web.dto.ResourceMetaResp;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntFunction;

/** Small mapping helpers shared by the resource controllers (application DTOs -> web DTOs). */
final class ResourceWebMapper {

    private ResourceWebMapper() {
    }

    /** Maps a service metadata view to the wire response, stringifying the domain enums. */
    static ResourceMetaResp toResp(ResourceMeta meta) {
        return new ResourceMetaResp(
                meta.uuid(),
                meta.app(),
                meta.groupKey(),
                meta.name(),
                meta.resourceType().name(),
                meta.mimeType(),
                meta.sizeBytes(),
                meta.visibility().name(),
                meta.status().name(),
                meta.version(),
                meta.createdAt() == null ? null : meta.createdAt().toString(),
                meta.url());
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

    /** @return whether the token carries {@code ROLE_SUPER_ADMIN} (the file-management super-admin) */
    static boolean isSuperAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ROLE_SUPER_ADMIN");
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
        if (status == null || status.isBlank()) {
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
        if (sourceType == null || sourceType.isBlank()) {
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

    /** @return the {@code sourceType} filter options, derived from {@link SourceType} */
    static List<OptionResp> sourceTypeOptions() {
        return codedOptions(SourceType.values(), SourceType::getValue);
    }

    /** @return the {@code status} filter options, derived from {@link ResourceStatus} */
    static List<OptionResp> statusOptions() {
        return codedOptions(ResourceStatus.values(), ResourceStatus::getValue);
    }

    /**
     * Builds the wire options of a coded enum, labelled {@code NAME(value)} — the same convention the
     * database column {@code COMMENT}s document, so a console select can never drift from the codes.
     *
     * @param values the enum constants
     * @param code reads the persisted tinyint code off a constant
     * @param <E> the enum type
     * @return one option per constant, in declaration order
     */
    private static <E extends Enum<E>> List<OptionResp> codedOptions(E[] values, ToIntFunction<E> code) {
        return Arrays.stream(values)
                .map(v -> new OptionResp(v.name(), v.name() + "(" + code.applyAsInt(v) + ")"))
                .toList();
    }
}
