package cn.nitrowater.core.application.auth;

import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.req.auth.RegisterRequest;
import cn.nitrowater.core.domain.user.User;

public interface RegisterService {
    @Transactional
    User register(RegisterRequest body, String smsCodeKey);

    @Transactional
    User autoRegister(String target, VerifyChannel channel, VerifyScene scene, String codeKey, String code, String deviceFp);
}
