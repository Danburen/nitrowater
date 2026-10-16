package cn.nitrowater.resource.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Metadata row for one managed resource.
 *
 * <p>The byte content lives in the storage backend referenced by {@link #backend} + {@link #bucketKey};
 * this row is the source of truth for authorization, ownership, type and lifecycle. Enum columns map
 * to {@code tinyint UNSIGNED} via {@code @Converter(autoApply = true)}.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "resource")
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Opaque external identifier (used in URLs; unguessable). */
    @Column(name = "uuid", nullable = false, length = 36)
    private String uuid;

    /** Application / namespace (also the object-key prefix). */
    @Column(name = "app", nullable = false, length = 32)
    private String app;

    /** Grouping key within the app (e.g. plugin key); pairs with {@link #app} for grouped listing. */
    @Column(name = "group_key", length = 64)
    private String groupKey;

    @Column(name = "backend", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private StorageBackend backend;

    /** Object-storage key or local relative path. */
    @Column(name = "bucket_key", nullable = false, length = 512)
    private String bucketKey;

    @Column(name = "visibility", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private ResourceVisibility visibility = ResourceVisibility.PRIVATE;

    @Column(name = "resource_type", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private ResourceType resourceType = ResourceType.UNKNOWN;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** Original file name as supplied by the uploader (display / download name). */
    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "source_type", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private SourceType sourceType = SourceType.USER_UPLOADED;

    /** Uploader uid (logical link to account; no cross-DB FK). */
    @Column(name = "owner_uid")
    private Long ownerUid;

    @Column(name = "status", nullable = false, columnDefinition = "tinyint UNSIGNED")
    private ResourceStatus status = ResourceStatus.PENDING;

    /** Free-form probe metadata as JSON text. */
    @Column(name = "file_meta", columnDefinition = "json")
    private String fileMeta;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
