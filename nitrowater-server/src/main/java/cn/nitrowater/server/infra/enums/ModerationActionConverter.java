package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link ModerationAction} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class ModerationActionConverter implements AttributeConverter<ModerationAction, Short> {

    @Override
    public Short convertToDatabaseColumn(ModerationAction attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ModerationAction convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ModerationAction.fromValue(dbData);
    }
}
