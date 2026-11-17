package cn.nitrowater.core.application.auth;

import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.req.auth.RegisterRequest;
import cn.nitrowater.core.infrastructure.domain.user.User;

public interface RegisterService {
    /**
     * Registers a normal account from the public registration form.
     *
     * <p>Registration covers one contact channel only: a phone proven by an SMS code. An email is
     * optional account data, bound later from the user center (where ownership is proven against an
     * already-authenticated account); accepting it here would mean a second code, a second captcha
     * and a second cookie for something the account can live without.</p>
     *
     * @param body       phone + username (required), optional password
     * @param smsCodeKey cookie key of the SMS code sent to {@code body.phone}
     * @return the persisted {@link User}
     */
    @Transactional
    User register(RegisterRequest body, String smsCodeKey);

    @Transactional
    User autoRegister(String target, VerifyChannel channel, VerifyScene scene, String codeKey, String code, String deviceFp);
}
