package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link Expertise} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class ExpertiseConverter implements AttributeConverter<Expertise, Short> {

    @Override
    public Short convertToDatabaseColumn(Expertise attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public Expertise convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : Expertise.fromValue(dbData);
    }
}
