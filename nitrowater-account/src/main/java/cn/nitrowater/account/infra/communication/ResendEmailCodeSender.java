package cn.nitrowater.account.infra.communication;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.application.auth.code.AbstractRedisCodeSender;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.account.infra.communication.email.EmailSendService;
import cn.nitrowater.account.infra.communication.email.EmailTemplateFragment;
import cn.nitrowater.account.infra.communication.email.EmailTemplateLayout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Delivers email verification codes through {@link EmailSendService} (Resend, HTML template).
 *
 * <p>Selected when {@code app.communication.mode=real}; {@link
 * cn.nitrowater.core.application.auth.code.impl.ConsoleEmailCodeSender} covers mock mode.</p>
 *
 * <p>The code is delivered <em>before</em> it is stored in Redis (see
 * {@link AbstractRedisCodeSender#sendCode}), so a provider failure surfaces as an error and never
 * leaves a verifiable code the user did not receive.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.communication", name = "mode", havingValue = "real")
public class ResendEmailCodeSender extends AbstractRedisCodeSender {

    /** Subject line shared by every code-bearing email. */
    private static final String SUBJECT = "NitroWater 验证码";

    private final EmailSendService emailSendService;

    public ResendEmailCodeSender(RedisHelperHolder redisHelper,
            EmailSendService emailSendService,
            @Value("${expire.email.verify:5}") long expireMinutes) {
        super(redisHelper, VerifyChannel.EMAIL, expireMinutes);
        this.emailSendService = emailSendService;
    }

    @Override
    protected void deliver(String target, VerifyScene scene, String code, long expireMinutes) {
        Map<String, Object> data = new HashMap<>();
        data.put("code", code);
        data.put("expireMinutes", expireMinutes);
        data.put("sceneLabel", sceneLabel(scene));
        emailSendService.sendHtmlEmail(target, SUBJECT,
                EmailTemplateLayout.BASE, EmailTemplateFragment.VERIFY_CODE, data);
    }

    /**
     * Maps a verification scene to the wording shown in the email, so a user can tell an expected
     * code from a phishing attempt ("you did not ask to reset your password").
     *
     * @param scene verification scene
     * @return human readable reason
     */
    private static String sceneLabel(VerifyScene scene) {
        return switch (scene) {
            case LOGIN -> "登录";
            case REGISTER -> "注册";
            case FORGOT_PASSWORD, RESET_PASSWORD -> "重置密码";
            case SET_PASSWORD -> "设置密码";
            case CHANGE_EMAIL -> "更换邮箱";
            case BIND_EMAIL -> "绑定邮箱";
            case UNBIND -> "解绑邮箱";
            case CHANGE_PHONE -> "更换手机号";
            case ACTIVATE -> "激活账号";
        };
    }
}
