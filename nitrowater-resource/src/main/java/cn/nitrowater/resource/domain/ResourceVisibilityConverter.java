package cn.nitrowater.resource.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link ResourceVisibility} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class ResourceVisibilityConverter implements AttributeConverter<ResourceVisibility, Short> {

    @Override
    public Short convertToDatabaseColumn(ResourceVisibility attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ResourceVisibility convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ResourceVisibility.fromValue(dbData);
    }
}
