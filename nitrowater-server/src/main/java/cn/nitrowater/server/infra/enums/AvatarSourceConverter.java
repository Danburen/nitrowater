package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link AvatarSource} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class AvatarSourceConverter implements AttributeConverter<AvatarSource, Short> {

    @Override
    public Short convertToDatabaseColumn(AvatarSource attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public AvatarSource convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : AvatarSource.fromValue(dbData);
    }
}
