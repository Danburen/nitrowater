package cn.nitrowater.core.services.auth.code;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.req.auth.SecurityVerifyCodeDto;
import cn.nitrowater.core.api.req.auth.SendCodeReq;
import cn.nitrowater.core.api.req.auth.VerifyCodeDto;
import cn.nitrowater.core.api.resp.auth.CodeResult;
import cn.nitrowater.core.exception.BizException;

/**
 * A service for sending verification codes
 * usually use in Strong Authentication
 * .e.g SMS, Email
 */
public interface VerificationService {

    /**
     * Send verification code to authenticate user.
     * will check the db and find channel sending target linked to target
     *
     * @param channel channel
     * @param scene   scene
     * @return code result
     */
    CodeResult sendAutoTargetAuthenticationCode(VerifyChannel channel, VerifyScene scene);

    /**
     * Send verification code to authenticate user segment given target
     * @param channel channel
     * @param scene  scene
     * @param target target
     * @return code result
     */
    CodeResult sendCodeForAuthenticated(String target, VerifyChannel channel, VerifyScene scene);

    /**
     * Send code to anonymous, usually used for register or login
     * @param dto {@link SendCodeReq}
     * @return {@link CodeResult}
     */
    CodeResult sendCodeForAnonymous(SendCodeReq dto);

    /**
     * Verify code
     * @param target target
     * @param scene  scene
     * @param channel channel
     * @param key key ofPending the code
     * @param code  code
     * @throws BizException if code is invalid
     */
    void verifyCode(String target, VerifyScene scene, VerifyChannel channel, String key, String code);

    void verifyCode(String verifyCodeKey, VerifyCodeDto verifyBody);

    /**
     * Verify code segment point target, will check the scene whether is the same as the scene
     * <b>Usually used for checking the user whether is the account owner</b> segment <b>whatever</b> channel.
     * @param verifyCodeKey key ofPending the code
     * @param verifyBody verify body
     * @param scene target scene
     */
    void verifyAuthorizedCode(String verifyCodeKey, SecurityVerifyCodeDto verifyBody, String target, VerifyScene scene);

    /**
     * Verify code segment point target, will check the scene whether is the same as the scene
     * <b>Usually used for verify the new binding target</b> segment <b>target</b>channel.
     * @param verifyCodeKey key ofPending the code
     * @param verifyBody verify body
     * @param scene target scene
     * @param allowChannels target channel
     */
    void verifyAuthorizedCodeWithChannel(String verifyCodeKey, SecurityVerifyCodeDto verifyBody, String target, VerifyScene scene, VerifyChannel... allowChannels);
}
