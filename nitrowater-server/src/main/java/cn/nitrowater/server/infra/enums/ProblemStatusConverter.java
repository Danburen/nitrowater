package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link ProblemStatus} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class ProblemStatusConverter implements AttributeConverter<ProblemStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(ProblemStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ProblemStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ProblemStatus.fromValue(dbData);
    }
}
