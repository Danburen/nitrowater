package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link SubmissionStatus} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class SubmissionStatusConverter implements AttributeConverter<SubmissionStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(SubmissionStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public SubmissionStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : SubmissionStatus.fromValue(dbData);
    }
}
