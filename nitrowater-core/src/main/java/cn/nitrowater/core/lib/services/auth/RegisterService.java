package cn.nitrowater.core.lib.services.auth;

import org.springframework.transaction.annotation.Transactional;
import cn.nitrowater.core.lib.api.auth.VerifyChannel;
import cn.nitrowater.core.lib.api.auth.VerifyScene;
import cn.nitrowater.core.lib.api.req.auth.RegisterRequest;
import cn.nitrowater.core.lib.entity.user.User;

public interface RegisterService {
    @Transactional
    User register(RegisterRequest body, String smsCodeKey);

    @Transactional
    User autoRegister(String target, VerifyChannel channel, VerifyScene scene, String codeKey, String code, String deviceFp);
}
