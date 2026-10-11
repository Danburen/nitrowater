package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link UserStatus} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class UserStatusConverter implements AttributeConverter<UserStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(UserStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public UserStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : UserStatus.fromValue(dbData);
    }
}
