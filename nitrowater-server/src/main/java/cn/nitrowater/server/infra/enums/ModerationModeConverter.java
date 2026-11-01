package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link ModerationMode} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class ModerationModeConverter implements AttributeConverter<ModerationMode, Short> {

    @Override
    public Short convertToDatabaseColumn(ModerationMode attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ModerationMode convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ModerationMode.fromValue(dbData);
    }
}
