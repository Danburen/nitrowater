package cn.nitrowater.core.lib.services.auth.code;

import cn.nitrowater.core.lib.api.auth.VerifyChannel;
import cn.nitrowater.core.lib.api.auth.VerifyScene;
import cn.nitrowater.core.lib.api.resp.auth.CodeResult;

/**
 * A class to manager verification code sending
 */
public interface CodeSender {
    /**
     * Direct Send code to target and save to cache segment key.
     * @param target  target usually is phone or email
     * @param scene verify scene
     * @return  result
     */
    CodeResult sendCode(String target, VerifyScene scene);

    /**
     * The channel ofPending the sender
     * @return channel
     */
    VerifyChannel channel();
}
