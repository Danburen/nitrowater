package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link SkillOrigin} to its persisted {@code tinyint UNSIGNED} code. */
@Converter(autoApply = true)
public class SkillOriginConverter implements AttributeConverter<SkillOrigin, Integer> {

    @Override
    public Integer convertToDatabaseColumn(SkillOrigin attribute) {
        return attribute == null ? null : (int) attribute.getValue();
    }

    @Override
    public SkillOrigin convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : SkillOrigin.fromValue(dbData);
    }
}
