package cn.nitrowater.core.lib.services.auth.code;

import cn.nitrowater.core.lib.api.auth.VerifyChannel;
import cn.nitrowater.core.lib.api.auth.VerifyScene;

public interface CodeVerifier {
    Object generateVerifyCode();
    boolean verifyCode(String target,VerifyScene scene, String key, String code);
    VerifyChannel  channel();
}
