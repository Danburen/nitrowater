package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link MemoryKind} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class MemoryKindConverter implements AttributeConverter<MemoryKind, Short> {

    @Override
    public Short convertToDatabaseColumn(MemoryKind attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public MemoryKind convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : MemoryKind.fromValue(dbData);
    }
}
