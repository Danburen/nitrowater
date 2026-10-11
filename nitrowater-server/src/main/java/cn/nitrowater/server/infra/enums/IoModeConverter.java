package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link IoMode} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class IoModeConverter implements AttributeConverter<IoMode, Short> {

    @Override
    public Short convertToDatabaseColumn(IoMode attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public IoMode convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : IoMode.fromValue(dbData);
    }
}
