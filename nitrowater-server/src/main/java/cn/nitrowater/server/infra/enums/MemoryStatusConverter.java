package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link MemoryStatus} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class MemoryStatusConverter implements AttributeConverter<MemoryStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(MemoryStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public MemoryStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : MemoryStatus.fromValue(dbData);
    }
}
