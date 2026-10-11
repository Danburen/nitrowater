package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link PointRefType} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class PointRefTypeConverter implements AttributeConverter<PointRefType, Short> {

    @Override
    public Short convertToDatabaseColumn(PointRefType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public PointRefType convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : PointRefType.fromValue(dbData);
    }
}
