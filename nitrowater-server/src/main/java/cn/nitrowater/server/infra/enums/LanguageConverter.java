package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link Language} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class LanguageConverter implements AttributeConverter<Language, Short> {

    @Override
    public Short convertToDatabaseColumn(Language attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public Language convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : Language.fromValue(dbData);
    }
}
