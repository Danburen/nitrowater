package cn.nitrowater.core.api.req.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.lib.common.validation.PhoneNumber;
import cn.nitrowater.lib.common.validation.StrongPassword;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Public registration payload. Exactly one contact target is required: a phone proven by an SMS
 * code, or an email proven by an email code. Both routes create the same kind of account; the
 * chosen target is the one stored as verified, and the other can be bound later from the user
 * center.
 */
@Data
public class RegisterRequest {
    /**
     * Registration identifier option A. Kept optional at the field level so the class-level
     * {@link #isSingleIdentifier()} can report one precise "phone or email" error.
     */
    @PhoneNumber
    private String phone;

    /** Registration identifier option B. */
    @Email(message = "{valid.email_address.invalid}")
    @Size(max = 254, message = "{valid.email_address.invalid}")
    private String email;

    /**
     * Display name. It is also accepted for password login, so a value that looks like a phone
     * number is rejected: otherwise username resolution would be ambiguous with phone login.
     */
    @NotBlank(message = "{user.valid.username_invalid}")
    @Size(min = 4, max = 20, message = "{user.valid.username_invalid}")
    @Pattern(regexp = "^(?!1[3-9]\\d{9}$)[0-9a-zA-Z_]+$",
            message = "{user.valid.username_phone_reserved}")
    private String username;

    /**
     * Optional. A user may register with a code and set a password later; when present it must
     * satisfy {@link StrongPassword}. Blank input is normalized to {@code null} so the validator
     * and the service agree that no password was supplied.
     */
    @StrongPassword
    private String password;

    /**
     * Treats a blank password as absent while leaving a real password byte-for-byte intact.
     * Lombok skips generating a setter because this method already exists.
     *
     * @param password raw password from the request
     */
    public void setPassword(String password) {
        this.password = StringUtil.isBlank(password) ? null : password;
    }

    /**
     * Proof for the chosen identifier. A phone target must use {@link VerifyChannel#SMS}; an email
     * target must use {@link VerifyChannel#EMAIL}, with the same value in {@code target}.
     */
    @NotNull
    private VerifyCodeDto verify;

    /**
     * Whether the user accepted the Terms of Service and Privacy Policy.
     *
     * <p>The checkbox is rendered on the registration form, but the rule is enforced here: an API
     * client that skips the UI must still be refused, and consent has to be something the server
     * observed rather than a line the page merely displayed.</p>
     */
    @AssertTrue(message = "{valid.terms.required}")
    private boolean agreed;

    /** @return whether exactly one registration identifier was supplied */
    @JsonIgnore
    @AssertTrue(message = "{validation.register.identifier}")
    public boolean isSingleIdentifier() {
        return hasPhone() ^ hasEmail();
    }

    /** @return whether the verification channel proves the chosen identifier */
    @JsonIgnore
    @AssertTrue(message = "{validation.register.verify_channel}")
    public boolean isVerifyChannelMatching() {
        if (verify == null || verify.getChannel() == null) {
            return true; // @NotNull reports the missing value; avoid a duplicate error.
        }
        if (hasPhone() && hasEmail()) {
            return false;
        }
        if (hasPhone()) {
            return verify.getChannel() == VerifyChannel.SMS;
        }
        if (hasEmail()) {
            return verify.getChannel() == VerifyChannel.EMAIL;
        }
        return true;
    }

    /** @return whether the verification target is the identifier being registered */
    @JsonIgnore
    @AssertTrue(message = "{validation.register.verify_target}")
    public boolean isVerifyTargetMatching() {
        if (verify == null || StringUtil.isBlank(verify.getTarget())) {
            return true; // @NotBlank on VerifyCodeDto reports the missing value.
        }
        String proof = verify.getTarget().trim();
        if (hasPhone() && !hasEmail()) {
            return phone.trim().equals(proof);
        }
        if (hasEmail() && !hasPhone()) {
            return email.trim().equalsIgnoreCase(proof);
        }
        return true;
    }

    private boolean hasPhone() {
        return !StringUtil.isBlank(phone);
    }

    private boolean hasEmail() {
        return !StringUtil.isBlank(email);
    }
}
