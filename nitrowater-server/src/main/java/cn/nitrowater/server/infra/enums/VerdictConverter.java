package cn.nitrowater.server.infra.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link Verdict} as its {@code tinyint UNSIGNED} value. */
@Converter(autoApply = true)
public class VerdictConverter implements AttributeConverter<Verdict, Short> {

    @Override
    public Short convertToDatabaseColumn(Verdict attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public Verdict convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : Verdict.fromValue(dbData);
    }
}
