package cn.nitrowater.resource.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link ResourceStatus} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class ResourceStatusConverter implements AttributeConverter<ResourceStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(ResourceStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ResourceStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ResourceStatus.fromValue(dbData);
    }
}
