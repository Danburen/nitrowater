package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link ProblemSetVisibility} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class ProblemSetVisibilityConverter implements AttributeConverter<ProblemSetVisibility, Short> {

    @Override
    public Short convertToDatabaseColumn(ProblemSetVisibility attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ProblemSetVisibility convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ProblemSetVisibility.fromValue(dbData);
    }
}
