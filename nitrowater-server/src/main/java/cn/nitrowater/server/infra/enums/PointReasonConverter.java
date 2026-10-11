package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link PointReason} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class PointReasonConverter implements AttributeConverter<PointReason, Short> {

    @Override
    public Short convertToDatabaseColumn(PointReason attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public PointReason convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : PointReason.fromValue(dbData);
    }
}
