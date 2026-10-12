package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link EditorialStatus} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class EditorialStatusConverter implements AttributeConverter<EditorialStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(EditorialStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public EditorialStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : EditorialStatus.fromValue(dbData);
    }
}
