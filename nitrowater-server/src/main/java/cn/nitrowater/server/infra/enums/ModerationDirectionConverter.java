package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link ModerationDirection} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class ModerationDirectionConverter implements AttributeConverter<ModerationDirection, Short> {

    @Override
    public Short convertToDatabaseColumn(ModerationDirection attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ModerationDirection convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ModerationDirection.fromValue(dbData);
    }
}
