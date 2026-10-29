package cn.nitrowater.core.api.req.auth;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bean Validation contract tests for {@link RegisterRequest}.
 *
 * <p>Password is optional by design (a user may register with a phone code and add a password
 * later), so {@code null} / empty / blank values must pass the DTO layer. When a non-blank
 * password is supplied it must still satisfy {@link cn.nitrowater.lib.common.validation.StrongPassword}.
 * The DTO also rejects a phone-shaped username so username resolution can never collide with
 * phone-number resolution.</p>
 */
class RegisterRequestValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    private static Validator validator() {
        return FACTORY.getValidator();
    }

    /** Builds an otherwise fully valid request so password violations are isolated. */
    private static RegisterRequest validRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setPhone("13800138000");
        req.setUsername("tester01");
        req.setPassword("Passw0rd123");
        req.setEmail("tester01@example.com");

        VerifyCodeDto verify = new VerifyCodeDto();
        verify.setChannel(VerifyChannel.SMS);
        verify.setTarget("13800138000");
        verify.setCode("123456");
        verify.setScene(VerifyScene.REGISTER);
        req.setVerify(verify);
        return req;
    }

    private static Set<String> passwordMessages(RegisterRequest req) {
        return validator().validate(req).stream()
                .filter(v -> "password".equals(v.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @ParameterizedTest(name = "password={0}")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void blankPasswordIsAcceptedByDto(String password) {
        RegisterRequest req = validRequest();
        req.setPassword(password);

        Set<ConstraintViolation<RegisterRequest>> violations = validator().validate(req);
        Set<String> passwordTemplates = violations.stream()
                .filter(v -> "password".equals(v.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessageTemplate)
                .collect(Collectors.toSet());

        assertTrue(passwordTemplates.isEmpty(),
                "optional password '" + password + "' must not be rejected at the DTO layer, got: "
                        + passwordTemplates);
    }

    @Test
    void blankPasswordProducesNoViolations() {
        RegisterRequest req = validRequest();
        req.setPassword("");

        Set<String> properties = validator().validate(req).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(properties.isEmpty(),
                "valid request with an empty optional password must pass, got: " + properties);
    }

    @Test
    void phoneShapedUsernameIsRejected() {
        RegisterRequest req = validRequest();
        req.setUsername("13800138000");

        Set<String> usernameTemplates = validator().validate(req).stream()
                .filter(v -> "username".equals(v.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessageTemplate)
                .collect(Collectors.toSet());

        assertFalse(usernameTemplates.isEmpty(),
                "a phone-shaped username must be rejected to keep identifier resolution unambiguous");
    }

    @Test
    void validRequestHasNoViolations() {
        Set<ConstraintViolation<RegisterRequest>> violations = validator().validate(validRequest());
        assertTrue(violations.isEmpty(),
                "fully valid request must pass validation, got: " + violations);
    }

    @Test
    void strongPasswordRuleStillRejectsWeakButNonBlankPassword() {
        RegisterRequest req = validRequest();
        req.setPassword("abcdefgh"); // 8 chars, no uppercase, no digit

        Set<String> messages = passwordMessages(req);
        assertFalse(messages.isEmpty(), "weak (non-blank) password must still violate @StrongPassword");
    }
}
