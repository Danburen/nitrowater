package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link ConversationStatus} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class ConversationStatusConverter implements AttributeConverter<ConversationStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(ConversationStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ConversationStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ConversationStatus.fromValue(dbData);
    }
}
