package cn.nitrowater.lib.api;

import lombok.Getter;

@Getter
public enum AuthCode implements ResponseCode {
    AUTHORIZE_ERROR("auth.error"),
    TOKEN_EXPIRED("auth.token_expired"),
    TOKEN_INVALID("auth.token_invalid"),
    TOKEN_MISSING("auth.token_missing"),
    USER_NOT_FOUND("user.not_found"),
    REAUTHORIZATION_REQUIRED("auth.reauthorization.required"),
    CAPTCHA_INVALID("auth.captcha.incorrect"),
    USERNAME_OR_PASSWORD_INCORRECT("auth.credentials_incorrect"),
    VERIFY_TARGET_UNSUPPORTED("auth.target.unsupported"),
    INVALID_VERIFY_SCENE("auth.invalid.scene"),
    /** A mailbox was supplied without its proof, so the account must not be created yet. */
    EMAIL_VERIFY_REQUIRED("auth.email_verify_required"),
    /** The account exists but is suspended or deactivated; credentials alone are not enough. */
    ACCOUNT_DISABLED("auth.account_disabled"),;


    private final String code;

    AuthCode(String code) {
        this.code = code;
    }

    @Override
    public ResponseCode toNoArgsResponse() {
        return null;
    }

}
