package cn.nitrowater.resource.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link StorageBackend} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class StorageBackendConverter implements AttributeConverter<StorageBackend, Short> {

    @Override
    public Short convertToDatabaseColumn(StorageBackend attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public StorageBackend convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : StorageBackend.fromValue(dbData);
    }
}
