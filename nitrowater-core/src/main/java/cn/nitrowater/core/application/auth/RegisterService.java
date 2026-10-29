package cn.nitrowater.core.application.auth;

import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.req.auth.RegisterRequest;
import cn.nitrowater.core.domain.user.User;

public interface RegisterService {
    /**
     * Registers a normal account from the public registration form.
     *
     * @param body         phone + username (required), optional password and email
     * @param smsCodeKey   cookie key of the SMS code sent to {@code body.phone}
     * @param emailCodeKey cookie key of the email code sent to {@code body.email}, when present
     * @return the persisted {@link User}
     */
    @Transactional
    User register(RegisterRequest body, String smsCodeKey, String emailCodeKey);

    @Transactional
    User autoRegister(String target, VerifyChannel channel, VerifyScene scene, String codeKey, String code, String deviceFp);
}
