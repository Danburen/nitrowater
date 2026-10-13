package cn.nitrowater.resource.application;

import cn.nitrowater.resource.application.dto.ResourceMeta;
import cn.nitrowater.resource.application.dto.ResourceReadResult;
import cn.nitrowater.resource.application.dto.UploadPolicyCommand;
import cn.nitrowater.resource.application.dto.UploadPolicyResult;
import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.domain.Resource;
import cn.nitrowater.resource.domain.ResourceRepository;
import cn.nitrowater.resource.domain.ResourceStatus;
import cn.nitrowater.resource.domain.ResourceType;
import cn.nitrowater.resource.domain.ResourceVisibility;
import cn.nitrowater.resource.domain.SourceType;
import cn.nitrowater.resource.domain.StorageBackend;
import cn.nitrowater.resource.infrastructure.FileStorage;
import cn.nitrowater.resource.infrastructure.FileStorageRegistry;
import cn.nitrowater.resource.infrastructure.PresignedUrlCache;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * Core resource orchestration: registration, upload confirmation, reads and deletion.
 *
 * <p>Owns the {@code resource} metadata and drives the pluggable {@link FileStorage} backends. Every
 * use case returns a complete application-layer result (a service DTO), including the read outcome
 * and its response policy, so the web layer only translates results into transport responses.
 * Presigning is delegated to the backend; when a backend cannot presign (local filesystem) reads
 * fall back to streaming the bytes through this service.</p>
 */
@Service
public class ResourceService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final int MAGIC_HEAD_BYTES = 16;

    private final ResourceRepository repository;
    private final FileStorageRegistry storageRegistry;
    private final ResourceProperties properties;
    private final FileTypeValidator validator;
    private final PresignedUrlCache readUrlCache;

    public ResourceService(ResourceRepository repository, FileStorageRegistry storageRegistry,
            ResourceProperties properties, FileTypeValidator validator, PresignedUrlCache readUrlCache) {
        this.repository = repository;
        this.storageRegistry = storageRegistry;
        this.properties = properties;
        this.validator = validator;
        this.readUrlCache = readUrlCache;
    }

    /**
     * Registers a pending resource and returns a presigned PUT URL (or {@code null} for backends
     * that require proxying).
     *
     * @param command upload intent
     * @param ownerUid uploader uid
     * @return presign result
     */
    @Transactional
    public UploadPolicyResult createUploadPolicy(UploadPolicyCommand command, Long ownerUid) {
        if (command.sizeBytes() > properties.maxSizeBytes()) {
            throw new ResourceException(HttpStatus.CONTENT_TOO_LARGE, "too_large",
                    "File exceeds the maximum size of " + properties.maxSizeBytes() + " bytes");
        }
        ResourceType type = validator.typeByExtension(command.fileName())
                .orElseThrow(() -> new ResourceException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_type",
                        "Unsupported file type: " + command.fileName()));

        StorageBackend backend = command.backend() != null ? command.backend() : parseDefaultBackend();
        String uuid = UUID.randomUUID().toString();
        String extension = validator.extensionOf(command.fileName()).orElse("");
        String objectKey = buildObjectKey(command.app(), ownerUid, uuid, extension);

        // The client-declared content type is ignored: resolve the media type from the (already
        // validated) extension so a caller cannot get the service to echo an attacker-chosen
        // Content-Type (which would turn a stored file into stored XSS).
        String mimeType = validator.mimeTypeByExtension(command.fileName())
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        Resource resource = new Resource();
        resource.setUuid(uuid);
        resource.setApp(command.app());
        resource.setBackend(backend);
        resource.setBucketKey(objectKey);
        resource.setVisibility(command.visibility());
        resource.setResourceType(type);
        resource.setMimeType(mimeType);
        resource.setSourceType(SourceType.USER_UPLOADED);
        resource.setOwnerUid(ownerUid);
        resource.setStatus(ResourceStatus.PENDING);
        repository.save(resource);

        Duration expiry = Duration.ofSeconds(properties.presignExpirySeconds());
        String uploadUrl = storageRegistry.get(backend).presignedPutUrl(objectKey, mimeType, expiry);
        return new UploadPolicyResult(uuid, uploadUrl, uploadUrl != null ? "PUT" : null, backend,
                properties.presignExpirySeconds());
    }

    /**
     * Validates a finished upload (existence, size, magic number), activates the resource and returns
     * its metadata.
     *
     * @param uuid resource identifier
     * @param ownerUid expected owner
     * @return the activated resource metadata
     */
    @Transactional
    public ResourceMeta confirmUpload(String uuid, Long ownerUid) {
        Resource resource = require(uuid);
        if (!Objects.equals(resource.getOwnerUid(), ownerUid)) {
            throw new ResourceException(HttpStatus.FORBIDDEN, "not_owner", "Resource does not belong to caller");
        }
        FileStorage storage = storageRegistry.get(resource.getBackend());
        if (!storage.exists(resource.getBucketKey())) {
            throw new ResourceException(HttpStatus.BAD_REQUEST, "object_missing", "Uploaded object not found");
        }
        long size = storage.size(resource.getBucketKey());
        if (size > properties.maxSizeBytes()) {
            storage.remove(resource.getBucketKey());
            throw new ResourceException(HttpStatus.CONTENT_TOO_LARGE, "too_large", "File exceeds maximum size");
        }
        validator.detectByMagic(readHead(storage, resource.getBucketKey()))
                .ifPresent(resource::setResourceType);

        resource.setSizeBytes(size);
        resource.setStatus(ResourceStatus.ACTIVE);
        resource.setUpdatedAt(Instant.now());
        return toMeta(repository.save(resource));
    }

    /**
     * Stores bytes server-side (proxy upload path, used by backends without presigning) and returns
     * the activated resource metadata.
     *
     * @param uuid resource identifier
     * @param ownerUid expected owner
     * @param in content stream
     * @param size content length ({@code -1} if unknown)
     * @param contentType content type
     * @return the activated resource metadata
     */
    @Transactional
    public ResourceMeta putBytes(String uuid, Long ownerUid, InputStream in, long size, String contentType) {
        Resource resource = require(uuid);
        if (!Objects.equals(resource.getOwnerUid(), ownerUid)) {
            throw new ResourceException(HttpStatus.FORBIDDEN, "not_owner", "Resource does not belong to caller");
        }
        storageRegistry.get(resource.getBackend()).put(resource.getBucketKey(), in, size, contentType);
        return confirmUpload(uuid, ownerUid);
    }

    /**
     * Resolves how to serve a read, applying access control and full response policy (presigned
     * redirect vs. proxied stream, media type, download disposition, cache directives).
     *
     * <p>A caller may read a resource when it is {@code PUBLIC}, or when the caller owns it; otherwise
     * {@code 403}. Media types that must not render inline (e.g. SVG/HTML/XML) are always streamed as
     * {@code application/octet-stream} with {@code Content-Disposition: attachment}.</p>
     *
     * @param uuid resource identifier
     * @param callerUid authenticated caller's uid, or {@code null} for an anonymous caller
     * @return the read outcome describing exactly how to render the response
     */
    @Transactional(readOnly = true)
    public ResourceReadResult read(String uuid, Long callerUid) {
        Resource resource = requireReadable(uuid, callerUid);
        CacheControl cacheControl = cacheControl(resource);
        String mime = resource.getMimeType();
        boolean inlineSafe = FileTypeValidator.isInlineSafeMimeType(mime);
        // Only hand out a presigned redirect for media types that are safe to render inline; for
        // everything else we stream through this service so we fully control the response headers.
        if (inlineSafe) {
            String url = presignedGetUrl(resource);
            if (url != null) {
                return ResourceReadResult.redirect(url, cacheControl);
            }
        }
        InputStream stream = openStream(resource);
        MediaType mediaType = inlineSafe ? safeMediaType(mime) : MediaType.APPLICATION_OCTET_STREAM;
        long contentLength = resource.getSizeBytes() == null ? -1 : resource.getSizeBytes();
        return ResourceReadResult.stream(stream, mediaType, !inlineSafe, contentLength, cacheControl);
    }

    /**
     * Returns a resource's metadata, enforcing read access (see {@link #read(String, Long)}).
     *
     * @param uuid resource identifier
     * @param callerUid authenticated caller's uid, or {@code null} for an anonymous caller
     * @return the resource metadata
     */
    @Transactional(readOnly = true)
    public ResourceMeta meta(String uuid, Long callerUid) {
        return toMeta(requireReadable(uuid, callerUid));
    }

    /**
     * Deletes a resource's bytes and marks the metadata row {@code DELETED} (idempotent).
     *
     * @param uuid resource identifier
     * @param ownerUid expected owner
     * @throws ResourceException when the resource is missing or does not belong to the caller
     */
    @Transactional
    public void delete(String uuid, Long ownerUid) {
        Resource resource = require(uuid);
        if (!Objects.equals(resource.getOwnerUid(), ownerUid)) {
            throw new ResourceException(HttpStatus.FORBIDDEN, "not_owner", "Resource does not belong to caller");
        }
        if (resource.getStatus() == ResourceStatus.DELETED) {
            return;
        }
        storageRegistry.get(resource.getBackend()).remove(resource.getBucketKey());
        resource.setStatus(ResourceStatus.DELETED);
        resource.setUpdatedAt(Instant.now());
        repository.save(resource);
        readUrlCache.evict(resource.getUuid());
    }

    /** Builds the metadata view, embedding a read URL (or the service read path when not presignable). */
    private ResourceMeta toMeta(Resource resource) {
        String url = presignedGetUrl(resource);
        return new ResourceMeta(resource.getUuid(), resource.getApp(), resource.getResourceType(),
                resource.getMimeType(), resource.getSizeBytes(), resource.getVisibility(),
                resource.getStatus(), url != null ? url : serviceReadPath(resource));
    }

    /**
     * Loads a resource for reading, enforcing access: PUBLIC resources are readable by anyone,
     * a PRIVATE resource only by its owner. A caller that is not the owner gets {@code 403}.
     */
    private Resource requireReadable(String uuid, Long callerUid) {
        Resource resource = require(uuid);
        if (resource.getStatus() != ResourceStatus.ACTIVE) {
            throw new ResourceException(HttpStatus.NOT_FOUND, "not_found", "Resource is not available");
        }
        boolean isPublic = resource.getVisibility() == ResourceVisibility.PUBLIC;
        boolean isOwner = callerUid != null && Objects.equals(resource.getOwnerUid(), callerUid);
        if (!isPublic && !isOwner) {
            throw new ResourceException(HttpStatus.FORBIDDEN, "forbidden", "Resource is not accessible");
        }
        return resource;
    }

    /** @return a presigned GET URL (Redis-cached), or {@code null} when the backend cannot presign */
    private String presignedGetUrl(Resource resource) {
        Duration expiry = Duration.ofSeconds(properties.presignExpirySeconds());
        return readUrlCache.getOrSign(resource.getUuid(), expiry, properties.effectiveCacheSafetyMarginSeconds(),
                () -> storageRegistry.get(resource.getBackend()).presignedGetUrl(resource.getBucketKey(), expiry));
    }

    /** @return a stream over the resource bytes (caller closes) */
    private InputStream openStream(Resource resource) {
        return storageRegistry.get(resource.getBackend()).get(resource.getBucketKey());
    }

    private Resource require(String uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceException(HttpStatus.NOT_FOUND, "not_found", "Resource not found"));
    }

    /**
     * @return the same-origin service read path, used when the backend cannot presign (e.g. LOCAL),
     *         so callers always receive a usable URL
     */
    private static String serviceReadPath(Resource resource) {
        String prefix = resource.getVisibility() == ResourceVisibility.PUBLIC ? "/api/public" : "/api";
        return prefix + "/resource/" + resource.getUuid();
    }

    /** Browsers may cache the result for as long as the signed URL / service URL stays valid. */
    private CacheControl cacheControl(Resource resource) {
        long ttl = Math.max(1, properties.presignExpirySeconds() - properties.effectiveCacheSafetyMarginSeconds());
        CacheControl control = CacheControl.maxAge(Duration.ofSeconds(ttl));
        return resource.getVisibility() == ResourceVisibility.PUBLIC
                ? control.cachePublic()
                : control.cachePrivate();
    }

    /** Parses a media type, degrading to {@code application/octet-stream} on malformed input. */
    private static MediaType safeMediaType(String mime) {
        try {
            return MediaType.parseMediaType(mime);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private StorageBackend parseDefaultBackend() {
        String configured = properties.defaultBackend() == null ? "s3" : properties.defaultBackend().toLowerCase();
        return switch (configured) {
            case "local" -> StorageBackend.LOCAL;
            case "cos" -> StorageBackend.COS;
            default -> StorageBackend.S3;
        };
    }

    private static String buildObjectKey(String app, Long ownerUid, String uuid, String extension) {
        String owner = ownerUid == null ? "0" : ownerUid.toString();
        String noDash = uuid.replace("-", "");
        String suffix = extension.isEmpty() ? "" : "." + extension;
        return app + "/" + owner + "/" + LocalDate.now().format(DATE_PATH) + "/" + noDash + suffix;
    }

    private static byte[] readHead(FileStorage storage, String objectKey) {
        try (InputStream in = storage.get(objectKey)) {
            return in.readNBytes(MAGIC_HEAD_BYTES);
        } catch (IOException ex) {
            throw new ResourceException(HttpStatus.INTERNAL_SERVER_ERROR, "read_failed", "Failed to read object head");
        }
    }
}
