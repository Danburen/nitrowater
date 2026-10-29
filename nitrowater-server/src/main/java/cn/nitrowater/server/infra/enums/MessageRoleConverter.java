package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link MessageRole} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class MessageRoleConverter implements AttributeConverter<MessageRole, Short> {

    @Override
    public Short convertToDatabaseColumn(MessageRole attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public MessageRole convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : MessageRole.fromValue(dbData);
    }
}
