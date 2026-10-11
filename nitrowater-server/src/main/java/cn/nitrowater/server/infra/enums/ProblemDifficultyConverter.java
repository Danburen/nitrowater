package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link ProblemDifficulty} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class ProblemDifficultyConverter implements AttributeConverter<ProblemDifficulty, Short> {

    @Override
    public Short convertToDatabaseColumn(ProblemDifficulty attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ProblemDifficulty convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ProblemDifficulty.fromValue(dbData);
    }
}
