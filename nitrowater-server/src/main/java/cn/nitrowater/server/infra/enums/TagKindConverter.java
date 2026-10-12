package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link TagKind} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class TagKindConverter implements AttributeConverter<TagKind, Short> {

    @Override
    public Short convertToDatabaseColumn(TagKind attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public TagKind convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : TagKind.fromValue(dbData);
    }
}
