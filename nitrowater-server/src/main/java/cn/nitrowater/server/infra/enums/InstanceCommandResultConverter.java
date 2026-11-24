package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link InstanceCommandResult} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class InstanceCommandResultConverter implements AttributeConverter<InstanceCommandResult, Short> {

    @Override
    public Short convertToDatabaseColumn(InstanceCommandResult attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public InstanceCommandResult convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : InstanceCommandResult.fromValue(dbData);
    }
}
