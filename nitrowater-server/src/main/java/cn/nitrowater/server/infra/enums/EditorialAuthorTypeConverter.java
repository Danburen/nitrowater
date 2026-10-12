package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link EditorialAuthorType} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class EditorialAuthorTypeConverter implements AttributeConverter<EditorialAuthorType, Short> {

    @Override
    public Short convertToDatabaseColumn(EditorialAuthorType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public EditorialAuthorType convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : EditorialAuthorType.fromValue(dbData);
    }
}
