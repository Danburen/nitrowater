package cn.nitrowater.core.lib.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import cn.nitrowater.core.lib.common.validation.validator.StrongPasswordValidator;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = {StrongPasswordValidator.class})
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
    String message() default "{user.password.pattern}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
