package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link ReplyStyle} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class ReplyStyleConverter implements AttributeConverter<ReplyStyle, Short> {

    @Override
    public Short convertToDatabaseColumn(ReplyStyle attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ReplyStyle convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ReplyStyle.fromValue(dbData);
    }
}
