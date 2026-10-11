package cn.nitrowater.core.application.auth.code;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;

public interface CodeVerifier {
    Object generateVerifyCode();
    boolean verifyCode(String target,VerifyScene scene, String key, String code);
    VerifyChannel  channel();
}
