package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link ProfileChangeSource} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class ProfileChangeSourceConverter implements AttributeConverter<ProfileChangeSource, Short> {

    @Override
    public Short convertToDatabaseColumn(ProfileChangeSource attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ProfileChangeSource convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ProfileChangeSource.fromValue(dbData);
    }
}
