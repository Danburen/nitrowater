package cn.nitrowater.lib.common.io;

import lombok.Getter;

import java.util.List;

/**
 * Coarse category of a file / resource, shared by the file-extension table and the persisted
 * resource metadata ({@code resource.resource_type}).
 *
 * <p>The numeric code is the persisted {@code tinyint UNSIGNED} value: stable, append-only, and
 * mapped to the column by a JPA {@code AttributeConverter} in the persisting service.</p>
 */
@Getter
public enum ResourceType {
    UNKNOWN(0),
    IMAGE(1),
    VIDEO(2),
    AUDIO(3),
    TEXT(4),
    DOCUMENT(5),
    ARCHIVE(6),
    EXECUTABLE(7),
    OTHER(99);

    private final short value;
    ResourceType(final int value) {
        this.value = (short) value;
    }

    /**
     * @param value persisted {@code tinyint UNSIGNED} code
     * @return the matching resource type
     * @throws IllegalArgumentException when the code is unknown
     */
    public static ResourceType fromValue(int value) {
        for (ResourceType type : ResourceType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ResourceType value: " + value);
    }

    /**
     * @param value persisted code, or {@code null}
     * @return the matching resource type, or {@code null} for a {@code null} code
     */
    public static ResourceType fromCode(Byte value) {
        return value == null ? null : fromValue(value);
    }

    public boolean isAllowed(FileExtension ext) {
        return isAllowed(ext.getExt());
    }

    public boolean isAllowed(String ext) {
        return FileExtension.isAllowed(ext, this);
    }

    public List<String> getAllowExtensionString() {
        return FileExtension.getAllowExtensionString(this);
    }

    public List<FileExtension> getAllowExtensions() {
        return FileExtension.getAllowExtensions(this);
    }

    public List<String> getAllowMimeTypes() {
        return FileExtension.getAllowMimeTypes(this);
    }
}
