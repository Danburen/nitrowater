package cn.nitrowater.resource.application;

import cn.nitrowater.resource.application.dto.AdminResourceQuery;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import jakarta.persistence.criteria.Predicate;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** Upper bound on an admin listing page size, so a caller cannot force an unbounded scan. */
    private static final int MAX_ADMIN_PAGE_SIZE = 100;

    /** Key under which the logical version is stored inside the {@code file_meta} JSON document. */
    private static final String FILE_META_VERSION_KEY = "version";

    /** Jackson 3 mapper used for the small {@code file_meta} JSON documents. */
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final ResourceRepository repository;
    private final FileStorageRegistry storageRegistry;
    private final ResourceProperties properties;
    private final FileTypeValidator validator;
    private final PluginArtifactPolicy pluginArtifactPolicy;
    private final PresignedUrlCache readUrlCache;

    public ResourceService(ResourceRepository repository, FileStorageRegistry storageRegistry,
            ResourceProperties properties, FileTypeValidator validator,
            PluginArtifactPolicy pluginArtifactPolicy, PresignedUrlCache readUrlCache) {
        this.repository = repository;
        this.storageRegistry = storageRegistry;
        this.properties = properties;
        this.validator = validator;
        this.pluginArtifactPolicy = pluginArtifactPolicy;
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
        String objectKey = buildObjectKey(command.app(), command.groupKey(), ownerUid, uuid, extension);

        // The client-declared content type is ignored: resolve the media type from the (already
        // validated) extension so a caller cannot get the service to echo an attacker-chosen
        // Content-Type (which would turn a stored file into stored XSS).
        String mimeType = validator.mimeTypeByExtension(command.fileName())
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        Resource resource = new Resource();
        resource.setUuid(uuid);
        resource.setApp(command.app());
        resource.setGroupKey(blankToNull(command.groupKey()));
        resource.setOriginalName(command.fileName());
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
     * Publishes (or re-publishes) a system-owned resource from an internal caller — used to seed the
     * platform's own artefacts (e.g. plugin {@code .jar} downloads) without an end-user upload flow.
     *
     * <p>Re-publishing the same {@code app + group + originalName} overwrites the existing object and
     * reuses its {@code uuid}, so the public download URL stays stable across versions. The resource
     * is always {@code PUBLIC} + {@code SYSTEM} (immune to end-user deletion).</p>
     *
     * @param app application / namespace
     * @param groupKey grouping key within the app (e.g. plugin key)
     * @param originalName original file name (drives the extension check and the download name)
     * @param version logical version recorded in {@code file_meta}, or {@code null}
     * @param contentType declared content type (recorded for reference only; never trusted for the
     *                    stored / served MIME)
     * @param in content stream (caller closes)
     * @param size content length ({@code -1} if unknown)
     * @return the activated resource metadata
     */
    @Transactional
    public ResourceMeta publishSystem(String app, String groupKey, String originalName, String version,
            String contentType, InputStream in, long size) {
        return publishSystem(app, groupKey, originalName, version, contentType, in, size,
                properties.maxSizeBytes());
    }

    /**
     * Publishes a system resource with an explicit size cap (used by the admin plugin upload, whose
     * jars are larger than the end-user upload limit).
     *
     * @param maxBytes maximum accepted stored size for this publication
     * @see #publishSystem(String, String, String, String, String, InputStream, long)
     */
    @Transactional
    public ResourceMeta publishSystem(String app, String groupKey, String originalName, String version,
            String contentType, InputStream in, long size, long maxBytes) {
        String group = blankToNull(groupKey);
        if (size > maxBytes) {
            throw new ResourceException(HttpStatus.CONTENT_TOO_LARGE, "too_large",
                    "File exceeds the maximum size of " + maxBytes + " bytes");
        }
        ResourceType type = validator.typeByExtension(originalName)
                .orElseThrow(() -> new ResourceException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_type",
                        "Unsupported file type: " + originalName));
        String mimeType = validator.mimeTypeByExtension(originalName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        StorageBackend backend = parseDefaultBackend();

        // Idempotent re-publish: reuse the existing row (and its uuid + object key) when the same
        // app + group + name was already published, so the download URL does not change.
        Resource resource = repository
                .findFirstByAppAndGroupKeyAndOriginalNameAndSourceTypeOrderByCreatedAtDesc(
                        app, group, originalName, SourceType.SYSTEM)
                .orElseGet(Resource::new);
        if (resource.getUuid() == null) {
            String uuid = UUID.randomUUID().toString();
            String extension = validator.extensionOf(originalName).orElse("");
            resource.setUuid(uuid);
            resource.setBucketKey(buildObjectKey(app, group, null, uuid, extension));
        }

        resource.setApp(app);
        resource.setGroupKey(group);
        resource.setOriginalName(originalName);
        resource.setBackend(backend);
        resource.setVisibility(ResourceVisibility.PUBLIC);
        resource.setResourceType(type);
        resource.setMimeType(mimeType);
        resource.setSourceType(SourceType.SYSTEM);
        resource.setOwnerUid(null);
        resource.setStatus(ResourceStatus.PENDING);
        resource.setFileMeta(fileMetaJson(version));
        resource.setUpdatedAt(Instant.now());
        repository.save(resource);

        FileStorage storage = storageRegistry.get(backend);
        storage.put(resource.getBucketKey(), in, size, mimeType);
        long storedSize = storage.size(resource.getBucketKey());
        if (storedSize > maxBytes) {
            storage.remove(resource.getBucketKey());
            throw new ResourceException(HttpStatus.CONTENT_TOO_LARGE, "too_large", "File exceeds maximum size");
        }
        validator.detectByMagic(readHead(storage, resource.getBucketKey()))
                .ifPresent(resource::setResourceType);

        resource.setSizeBytes(storedSize);
        resource.setStatus(ResourceStatus.ACTIVE);
        resource.setUpdatedAt(Instant.now());
        readUrlCache.evict(resource.getUuid());
        return toMeta(repository.save(resource));
    }

    /**
     * Publishes (or re-publishes) a plugin artifact uploaded from the admin UI.
     *
     * <p>Applies the strict {@link PluginArtifactPolicy} (jar-only name allow-list, ZIP magic and the
     * dedicated size cap) and delegates to {@link #publishSystem} so the download URL stays stable
     * across versions.</p>
     *
     * @param pluginKey plugin key (also the resource group)
     * @param originalName client-supplied file name
     * @param version logical version, or {@code null}
     * @param in content stream (caller closes)
     * @param size declared content length
     * @return the activated resource metadata
     */
    @Transactional
    public ResourceMeta publishPluginArtifact(String pluginKey, String originalName, String version,
            InputStream in, long size) {
        String key = pluginArtifactPolicy.sanitizeKey(pluginKey);
        String name = pluginArtifactPolicy.sanitizeName(originalName);
        pluginArtifactPolicy.requireSize(size);
        return publishSystem(PluginArtifactPolicy.PLUGIN_APP, key, name, version,
                MediaType.APPLICATION_OCTET_STREAM_VALUE, in, size, pluginArtifactPolicy.maxBytes());
    }

    /**
     * Lists a plugin's active artifacts, newest first, as public download links.
     *
     * @param pluginKey plugin key (also the resource group)
     * @return the active artifacts, newest first
     */
    @Transactional(readOnly = true)
    public List<ResourceMeta> listPluginArtifacts(String pluginKey) {
        String key = pluginArtifactPolicy.sanitizeKey(pluginKey);
        return repository.findByAppAndGroupKeyAndStatusOrderByCreatedAtDesc(
                        PluginArtifactPolicy.PLUGIN_APP, key, ResourceStatus.ACTIVE)
                .stream()
                .map(ResourceService::toListItem)
                .toList();
    }

    /**
     * Deletes one plugin artifact after verifying that it belongs to the given plugin.
     *
     * <p>Unlike {@link #adminDelete}, a plain {@code ROLE_ADMIN} may remove system-published plugin
     * jars; the group check prevents deleting an arbitrary resource through this path.</p>
     *
     * @param pluginKey plugin key (also the resource group)
     * @param uuid resource identifier
     */
    @Transactional
    public void deletePluginArtifact(String pluginKey, String uuid) {
        String key = pluginArtifactPolicy.sanitizeKey(pluginKey);
        Resource resource = require(uuid);
        if (!PluginArtifactPolicy.PLUGIN_APP.equals(resource.getApp())
                || !Objects.equals(resource.getGroupKey(), key)) {
            throw new ResourceException(HttpStatus.NOT_FOUND, "not_found",
                    "Resource does not belong to this plugin");
        }
        deleteWithPolicy(uuid, null, true);
    }

    /**
     * Lists the visible resources of one app + group (e.g. a plugin's published downloads), newest
     * first. No presigned URL is generated per item: each item's {@code url} points at the streaming
     * download endpoint instead.
     *
     * @param app application / namespace
     * @param groupKey grouping key within the app (e.g. plugin key)
     * @param callerUid authenticated caller's uid, or {@code null} for an anonymous caller
     * @return the visible, activated resources, newest first
     */
    @Transactional(readOnly = true)
    public List<ResourceMeta> list(String app, String groupKey, Long callerUid) {
        return repository.findByAppAndGroupKeyAndStatusOrderByCreatedAtDesc(app, groupKey, ResourceStatus.ACTIVE)
                .stream()
                .filter(resource -> isVisibleTo(resource, callerUid))
                .map(ResourceService::toListItem)
                .toList();
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
        return ResourceReadResult.stream(stream, mediaType, !inlineSafe, null, contentLength, cacheControl);
    }

    /**
     * Forces a streaming download: always serves the bytes through this service (never a presigned
     * redirect) with {@code Content-Disposition: attachment} and the original file name, so the
     * browser downloads rather than renders and the download name is preserved.
     *
     * @param uuid resource identifier
     * @param callerUid authenticated caller's uid, or {@code null} for an anonymous caller
     * @return the read outcome describing the forced-download stream
     */
    @Transactional(readOnly = true)
    public ResourceReadResult download(String uuid, Long callerUid) {
        return renderDownload(requireReadable(uuid, callerUid));
    }

    /** Builds the forced-download outcome for an already-authorized resource. */
    private ResourceReadResult renderDownload(Resource resource) {
        CacheControl cacheControl = cacheControl(resource);
        InputStream stream = openStream(resource);
        long contentLength = resource.getSizeBytes() == null ? -1 : resource.getSizeBytes();
        return ResourceReadResult.stream(stream, MediaType.APPLICATION_OCTET_STREAM, true,
                downloadFileName(resource), contentLength, cacheControl);
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
     * <p>System-owned resources ({@code source_type = SYSTEM}) are immutable and cannot be deleted
     * through this end-user path; they are managed exclusively by {@link #publishSystem}.</p>
     *
     * @param uuid resource identifier
     * @param ownerUid expected owner
     * @throws ResourceException when the resource is missing, is a system resource, or does not belong
     *         to the caller
     */
    @Transactional
    public void delete(String uuid, Long ownerUid) {
        deleteWithPolicy(uuid, ownerUid, false);
    }

    /**
     * Admin delete from the file-management console, applying the console's permission model:
     *
     * <pre>
     *   ROLE_SUPER_ADMIN      -&gt; allowed (system resources and other users' files included)
     *   otherwise, SYSTEM     -&gt; 403 (system resources are immutable to non-super-admins)
     *   otherwise, owner == caller -&gt; allowed
     *   otherwise             -&gt; 403
     * </pre>
     *
     * @param uuid resource identifier
     * @param callerUid admin's uid
     * @param superAdmin whether the caller holds {@code ROLE_SUPER_ADMIN}
     */
    @Transactional
    public void adminDelete(String uuid, Long callerUid, boolean superAdmin) {
        deleteWithPolicy(uuid, callerUid, superAdmin);
    }

    /**
     * Shared delete policy. The end-user path ({@link #delete}) always passes {@code superAdmin =
     * false}, so its behavior is unchanged (owner-only, system resources guarded).
     */
    private void deleteWithPolicy(String uuid, Long callerUid, boolean superAdmin) {
        Resource resource = require(uuid);
        if (!superAdmin) {
            if (resource.getSourceType() == SourceType.SYSTEM) {
                throw new ResourceException(HttpStatus.FORBIDDEN, "system_resource",
                        "System resources cannot be deleted");
            }
            if (!Objects.equals(resource.getOwnerUid(), callerUid)) {
                throw new ResourceException(HttpStatus.FORBIDDEN, "not_owner", "Resource does not belong to caller");
            }
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

    /**
     * Admin listing for the file-management console, filtered and paged at the database.
     *
     * <p>Visibility: a {@code ROLE_SUPER_ADMIN} sees every resource (system resources and all
     * owners); any other admin sees only the resources they own.</p>
     *
     * @param query optional filters
     * @param callerUid the admin's uid
     * @param superAdmin whether the caller holds {@code ROLE_SUPER_ADMIN}
     * @param page zero-based page index
     * @param size page size
     * @return one page of matching resources, newest first
     */
    @Transactional(readOnly = true)
    public Page<ResourceMeta> adminList(AdminResourceQuery query, Long callerUid, boolean superAdmin,
            int page, int size) {
        int safeSize = Math.min(MAX_ADMIN_PAGE_SIZE, Math.max(1, size));
        Pageable pageable = PageRequest.of(Math.max(0, page), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAll(adminSpecification(query, callerUid, superAdmin), pageable)
                .map(ResourceService::toAdminListItem);
    }

    /**
     * Lists the app namespaces visible to the caller, for the console's app filter.
     *
     * <p>Mirrors {@link #adminList} visibility so the filter can only offer namespaces the caller can
     * actually see: a {@code ROLE_SUPER_ADMIN} gets every namespace in the table, any other admin
     * only those of the resources they own.</p>
     *
     * @param callerUid the admin's uid
     * @param superAdmin whether the caller holds {@code ROLE_SUPER_ADMIN}
     * @return distinct app namespaces, ascending
     */
    @Transactional(readOnly = true)
    public List<String> listVisibleApps(Long callerUid, boolean superAdmin) {
        return superAdmin ? repository.findDistinctApps() : repository.findDistinctAppsByOwnerUid(callerUid);
    }

    /**
     * Admin metadata for one resource, enforcing the console visibility (super-admin: any; other
     * admin: own only). Unlike the read path this does not require {@code ACTIVE}, so an admin can
     * inspect pending/deleted rows too.
     *
     * @param uuid resource identifier
     * @param callerUid the admin's uid
     * @param superAdmin whether the caller holds {@code ROLE_SUPER_ADMIN}
     * @return the resource metadata (with a presigned read URL when the backend can presign)
     */
    @Transactional(readOnly = true)
    public ResourceMeta adminGet(String uuid, Long callerUid, boolean superAdmin) {
        return toMeta(requireAdminVisible(uuid, callerUid, superAdmin));
    }

    /**
     * Admin forced download of a resource's bytes, enforcing the console visibility (super-admin:
     * any; other admin: own only). The resource must be {@code ACTIVE} (bytes present).
     *
     * @param uuid resource identifier
     * @param callerUid the admin's uid
     * @param superAdmin whether the caller holds {@code ROLE_SUPER_ADMIN}
     * @return the forced-download read outcome
     */
    @Transactional(readOnly = true)
    public ResourceReadResult adminDownload(String uuid, Long callerUid, boolean superAdmin) {
        Resource resource = requireAdminVisible(uuid, callerUid, superAdmin);
        if (resource.getStatus() != ResourceStatus.ACTIVE) {
            throw new ResourceException(HttpStatus.NOT_FOUND, "not_found", "Resource is not available");
        }
        return renderDownload(resource);
    }

    /**
     * Loads a resource for the admin console, enforcing visibility: a super-admin may see any
     * resource; any other admin only their own.
     */
    private Resource requireAdminVisible(String uuid, Long callerUid, boolean superAdmin) {
        Resource resource = require(uuid);
        if (!superAdmin && !Objects.equals(resource.getOwnerUid(), callerUid)) {
            throw new ResourceException(HttpStatus.FORBIDDEN, "forbidden", "Resource is not accessible");
        }
        return resource;
    }

    /** Builds the admin list-item view: no presigning, {@code url} points at the admin download path. */
    private static ResourceMeta toAdminListItem(Resource resource) {
        return new ResourceMeta(resource.getUuid(), resource.getApp(), resource.getGroupKey(),
                resource.getOriginalName(), resource.getResourceType(), resource.getMimeType(),
                resource.getSizeBytes(), resource.getVisibility(), resource.getStatus(),
                versionOf(resource.getFileMeta()), resource.getCreatedAt(), adminDownloadPath(resource));
    }

    /** @return the same-origin admin download path for a resource */
    private static String adminDownloadPath(Resource resource) {
        return "/api/admin/resources/" + resource.getUuid() + "/download";
    }

    /** Builds the JPA filter for the admin listing, always constraining by the caller's visibility. */
    private static Specification<Resource> adminSpecification(AdminResourceQuery query, Long callerUid,
            boolean superAdmin) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!superAdmin) {
                predicates.add(cb.equal(root.get("ownerUid"), callerUid));
            }
            if (hasText(query.app())) {
                predicates.add(cb.equal(root.get("app"), query.app()));
            }
            if (hasText(query.groupKey())) {
                predicates.add(cb.equal(root.get("groupKey"), query.groupKey()));
            }
            if (query.ownerUid() != null) {
                predicates.add(cb.equal(root.get("ownerUid"), query.ownerUid()));
            }
            if (query.status() != null) {
                predicates.add(cb.equal(root.get("status"), query.status()));
            } else {
                // Soft-deleted rows are never part of a listing: their bytes are gone, the row only
                // exists so a later purge/audit can still resolve the uuid.
                predicates.add(cb.notEqual(root.get("status"), ResourceStatus.DELETED));
            }
            if (query.sourceType() != null) {
                predicates.add(cb.equal(root.get("sourceType"), query.sourceType()));
            }
            if (hasText(query.keyword())) {
                String like = "%" + query.keyword().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("originalName")), like));
            }            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Builds the metadata view, embedding a read URL (or the service read path when not presignable). */
    private ResourceMeta toMeta(Resource resource) {
        String url = presignedGetUrl(resource);
        return new ResourceMeta(resource.getUuid(), resource.getApp(), resource.getGroupKey(),
                resource.getOriginalName(), resource.getResourceType(), resource.getMimeType(),
                resource.getSizeBytes(), resource.getVisibility(), resource.getStatus(),
                versionOf(resource.getFileMeta()), resource.getCreatedAt(),
                url != null ? url : serviceReadPath(resource));
    }

    /** Builds a list item: no presigning, {@code url} points at the streaming download endpoint. */
    private static ResourceMeta toListItem(Resource resource) {
        return new ResourceMeta(resource.getUuid(), resource.getApp(), resource.getGroupKey(),
                resource.getOriginalName(), resource.getResourceType(), resource.getMimeType(),
                resource.getSizeBytes(), resource.getVisibility(), resource.getStatus(),
                versionOf(resource.getFileMeta()), resource.getCreatedAt(), serviceDownloadPath(resource));
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
        if (!isVisibleTo(resource, callerUid)) {
            throw new ResourceException(HttpStatus.FORBIDDEN, "forbidden", "Resource is not accessible");
        }
        return resource;
    }

    /** @return whether a resource is readable by the caller (PUBLIC, or PRIVATE owned by the caller) */
    private static boolean isVisibleTo(Resource resource, Long callerUid) {
        return resource.getVisibility() == ResourceVisibility.PUBLIC
                || (callerUid != null && Objects.equals(resource.getOwnerUid(), callerUid));
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
        return servicePrefix(resource) + "/resource/" + resource.getUuid();
    }

    /** @return the same-origin forced-download path for a resource */
    private static String serviceDownloadPath(Resource resource) {
        return servicePrefix(resource) + "/resource/" + resource.getUuid() + "/download";
    }

    private static String servicePrefix(Resource resource) {
        return resource.getVisibility() == ResourceVisibility.PUBLIC ? "/api/public" : "/api";
    }

    /** Browsers may cache the result for as long as the signed URL / service URL stays valid. */
    private CacheControl cacheControl(Resource resource) {
        long ttl = Math.max(1, properties.presignExpirySeconds() - properties.effectiveCacheSafetyMarginSeconds());
        CacheControl control = CacheControl.maxAge(Duration.ofSeconds(ttl));
        return resource.getVisibility() == ResourceVisibility.PUBLIC
                ? control.cachePublic()
                : control.cachePrivate();
    }

    /** @return the download file name: the original name when known, else the uuid */
    private static String downloadFileName(Resource resource) {
        String name = resource.getOriginalName();
        return (name != null && !name.isBlank()) ? name : resource.getUuid();
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

    /**
     * Builds the object key as {@code app/group/owner/yyyy/MM/dd/uuid.ext}, where {@code group} falls
     * back to {@code _} and a system resource (no owner) uses the {@code system} owner segment.
     */
    private static String buildObjectKey(String app, String groupKey, Long ownerUid, String uuid, String extension) {
        String group = (groupKey == null || groupKey.isBlank()) ? "_" : groupKey;
        String owner = ownerUid == null ? "system" : ownerUid.toString();
        String noDash = uuid.replace("-", "");
        String suffix = extension.isEmpty() ? "" : "." + extension;
        return app + "/" + group + "/" + owner + "/" + LocalDate.now().format(DATE_PATH) + "/" + noDash + suffix;
    }

    /** @return {@code {"version":"..."}} as JSON, or {@code null} when no version is recorded */
    private static String fileMetaJson(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        return JSON.writeValueAsString(Map.of(FILE_META_VERSION_KEY, version));
    }

    /** @return the {@code version} field of a {@code file_meta} JSON document, or {@code null} */
    private static String versionOf(String fileMeta) {
        if (fileMeta == null || fileMeta.isBlank()) {
            return null;
        }
        try {
            Object parsed = JSON.readValue(fileMeta, Map.class);
            if (parsed instanceof Map<?, ?> map) {
                Object version = map.get(FILE_META_VERSION_KEY);
                return version == null ? null : version.toString();
            }
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static byte[] readHead(FileStorage storage, String objectKey) {
        try (InputStream in = storage.get(objectKey)) {
            return in.readNBytes(MAGIC_HEAD_BYTES);
        } catch (IOException ex) {
            throw new ResourceException(HttpStatus.INTERNAL_SERVER_ERROR, "read_failed", "Failed to read object head");
        }
    }
}
