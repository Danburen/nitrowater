package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link InstanceCredentialStatus} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class InstanceCredentialStatusConverter implements AttributeConverter<InstanceCredentialStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(InstanceCredentialStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public InstanceCredentialStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : InstanceCredentialStatus.fromValue(dbData);
    }
}
