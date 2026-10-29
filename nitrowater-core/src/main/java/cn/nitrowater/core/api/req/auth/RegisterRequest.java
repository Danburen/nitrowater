package cn.nitrowater.core.api.req.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import cn.nitrowater.lib.common.validation.PhoneNumber;
import cn.nitrowater.lib.common.validation.StrongPassword;

@Data
public class RegisterRequest {
    @PhoneNumber
    @NotBlank(message = "{valid.phone.invalid}")
    private String phone;

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
     * Optional. A user may register with a phone code and set a password later; when present it
     * must satisfy {@link StrongPassword}. Blank input is normalized to {@code null} so the
     * validator and the service agree that no password was supplied.
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
        this.password = password == null || password.isBlank() ? null : password;
    }

    @Email(message = "{valid.email_address.invalid}")
    private String email;

    /**
     * Optional email proof. Required whenever {@link #email} is present, so an unverified mailbox
     * can never be claimed by someone else (account pre-hijacking).
     */
    private VerifyCodeDto emailVerify;

    @NotNull
    private VerifyCodeDto verify;
}
