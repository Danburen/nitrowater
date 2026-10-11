package cn.nitrowater.resource.application;

import cn.nitrowater.resource.infrastructure.PresignedUrlCache;
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
import org.springframework.http.HttpStatus;
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
 * Core resource orchestration: registration, presigned access, upload confirmation and reads.
 *
 * <p>Owns the {@code resource} metadata and drives the pluggable {@link FileStorage} backends.
 * Presigning is delegated to the backend; when a backend cannot presign (local filesystem) the
 * caller falls back to proxying bytes through this service.</p>
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

        Resource resource = new Resource();
        resource.setUuid(uuid);
        resource.setApp(command.app());
        resource.setBackend(backend);
        resource.setBucketKey(objectKey);
        resource.setVisibility(command.visibility());
        resource.setResourceType(type);
        resource.setMimeType(command.contentType());
        resource.setSourceType(SourceType.USER_UPLOADED);
        resource.setOwnerUid(ownerUid);
        resource.setStatus(ResourceStatus.PENDING);
        repository.save(resource);

        Duration expiry = Duration.ofSeconds(properties.presignExpirySeconds());
        String uploadUrl = storageRegistry.get(backend).presignedPutUrl(objectKey, command.contentType(), expiry);
        return new UploadPolicyResult(uuid, uploadUrl, uploadUrl != null ? "PUT" : null, backend,
                properties.presignExpirySeconds());
    }

    /**
     * Validates a finished upload (existence, size, magic number) and activates the resource.
     *
     * @param uuid resource identifier
     * @param ownerUid expected owner
     * @return the activated resource
     */
    @Transactional
    public Resource confirmUpload(String uuid, Long ownerUid) {
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
        return repository.save(resource);
    }

    /**
     * Stores bytes server-side (proxy upload path, used by backends without presigning).
     *
     * @param uuid resource identifier
     * @param ownerUid expected owner
     * @param in content stream
     * @param size content length ({@code -1} if unknown)
     * @param contentType content type
     */
    @Transactional
    public Resource putBytes(String uuid, Long ownerUid, InputStream in, long size, String contentType) {
        Resource resource = require(uuid);
        if (!Objects.equals(resource.getOwnerUid(), ownerUid)) {
            throw new ResourceException(HttpStatus.FORBIDDEN, "not_owner", "Resource does not belong to caller");
        }
        storageRegistry.get(resource.getBackend()).put(resource.getBucketKey(), in, size, contentType);
        return confirmUpload(uuid, ownerUid);
    }

    /**
     * Loads a resource for reading, enforcing visibility.
     *
     * @param uuid resource identifier
     * @param anonymous whether the caller is anonymous
     * @return the readable resource
     */
    @Transactional(readOnly = true)
    public Resource requireReadable(String uuid, boolean anonymous) {
        Resource resource = require(uuid);
        if (resource.getStatus() != ResourceStatus.ACTIVE) {
            throw new ResourceException(HttpStatus.NOT_FOUND, "not_found", "Resource is not available");
        }
        if (anonymous && resource.getVisibility() != ResourceVisibility.PUBLIC) {
            throw new ResourceException(HttpStatus.FORBIDDEN, "private", "Resource is not public");
        }
        return resource;
    }

    /**
     * @param resource an activated resource
     * @return a presigned GET URL (Redis-cached), or {@code null} when the backend cannot presign
     */
    public String presignedGetUrl(Resource resource) {
        Duration expiry = Duration.ofSeconds(properties.presignExpirySeconds());
        return readUrlCache.getOrSign(resource.getUuid(), expiry, properties.effectiveCacheSafetyMarginSeconds(),
                () -> storageRegistry.get(resource.getBackend()).presignedGetUrl(resource.getBucketKey(), expiry));
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

    /** @return a stream over the resource bytes (caller closes) */
    public InputStream openStream(Resource resource) {
        return storageRegistry.get(resource.getBackend()).get(resource.getBucketKey());
    }

    /** @return the storage implementation for a resource */
    public FileStorage storageOf(Resource resource) {
        return storageRegistry.get(resource.getBackend());
    }

    private Resource require(String uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceException(HttpStatus.NOT_FOUND, "not_found", "Resource not found"));
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

    /**
     * Upload intent.
     *
     * @param app target application / namespace
     * @param fileName original file name (drives extension)
     * @param contentType declared content type
     * @param sizeBytes declared size in bytes
     * @param visibility desired visibility
     * @param backend desired backend, or {@code null} to use the configured default
     */
    public record UploadPolicyCommand(String app, String fileName, String contentType, long sizeBytes,
            ResourceVisibility visibility, StorageBackend backend) {
    }

    /**
     * Presign result for an upload.
     *
     * @param uuid resource identifier
     * @param uploadUrl presigned PUT URL, or {@code null} to use the proxy endpoint
     * @param method HTTP method for {@code uploadUrl} ({@code PUT}), or {@code null}
     * @param backend backend that will hold the bytes
     * @param expiresInSeconds URL lifetime
     */
    public record UploadPolicyResult(String uuid, String uploadUrl, String method, StorageBackend backend,
            long expiresInSeconds) {
    }
}
