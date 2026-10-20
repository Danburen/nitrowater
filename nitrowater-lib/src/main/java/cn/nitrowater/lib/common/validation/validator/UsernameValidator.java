package cn.nitrowater.lib.common.validation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import cn.nitrowater.lib.common.validation.Username;

import java.util.regex.Pattern;

public class UsernameValidator implements ConstraintValidator<Username,String> {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    @Override
    public void initialize(Username constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        // Null defers to @NotBlank (same lenient contract as PhoneNumberValidator);
        // the regex matcher would otherwise throw NPE on a missing field.
        if (s == null) {
            return true;
        }
        return USERNAME_PATTERN.matcher(s).matches();
    }
}
