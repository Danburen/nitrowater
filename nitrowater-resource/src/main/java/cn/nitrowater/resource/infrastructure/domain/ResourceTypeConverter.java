package cn.nitrowater.resource.infrastructure.domain;

import cn.nitrowater.lib.common.io.ResourceType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists the shared {@link ResourceType} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class ResourceTypeConverter implements AttributeConverter<ResourceType, Short> {

    @Override
    public Short convertToDatabaseColumn(ResourceType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ResourceType convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ResourceType.fromValue(dbData);
    }
}
