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
     * <p>Registration proves exactly one contact channel: a phone by SMS code or an email by email
     * code. The proven target is stored as verified; the other one can be bound later from the user
     * center against the authenticated account.</p>
     *
     * @param body    phone or email + username (required), optional password
     * @param codeKey cookie key of the code sent to the chosen target
     * @return the persisted {@link User}
     */
    @Transactional
    User register(RegisterRequest body, String codeKey);

    @Transactional
    User autoRegister(String target, VerifyChannel channel, VerifyScene scene, String codeKey, String code, String deviceFp);
}
