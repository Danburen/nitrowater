package cn.nitrowater.account.communication;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.application.auth.code.AbstractRedisCodeSender;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.lib.utils.MaskUtil;
import cn.nitrowater.lib.utils.StringUtil;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aliyun SMS delivery for phone verification codes.
 *
 * <p>Selected when {@code app.communication.mode=real}. The sign name, the approved template and the
 * endpoint are configuration; the <em>credentials</em> are read from Alibaba Cloud's standard
 * environment variables ({@value #ACCESS_KEY_ID_ENV} / {@value #ACCESS_KEY_SECRET_ENV}), the same
 * names the Aliyun SDK itself uses. Credentials are never part of the application configuration
 * files.</p>
 *
 * <p>When the provider is not configured the sender <em>degrades to logging</em> (like mock mode)
 * so a local {@code mode=real} run still starts without credentials; a WARN names the missing keys.
 * Production deployments must supply the credentials - the degraded code only lands in the log, so
 * no user would ever receive it.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.communication", name = "mode", havingValue = "real")
public class AliyunSmsCodeSender extends AbstractRedisCodeSender {

    /** Alibaba Cloud's standard credential variables; the SDK resolves the same pair. */
    static final String ACCESS_KEY_ID_ENV = "ALIBABA_CLOUD_ACCESS_KEY_ID";
    static final String ACCESS_KEY_SECRET_ENV = "ALIBABA_CLOUD_ACCESS_KEY_SECRET";

    /** {@code null} means "not configured": codes are logged instead of sent. */
    private final Client client;
    private final String signName;
    private final String templateCode;

    public AliyunSmsCodeSender(RedisHelperHolder redisHelper,
            @Value("${app.communication.sms.sign-name:}") String signName,
            @Value("${app.communication.sms.template-code:}") String templateCode,
            @Value("${app.communication.sms.endpoint:dysmsapi.aliyuncs.com}") String endpoint,
            @Value("${expire.sms-code:2}") long expireMinutes) {
        super(redisHelper, VerifyChannel.SMS, expireMinutes);

        String accessKeyId = System.getenv(ACCESS_KEY_ID_ENV);
        String accessKeySecret = System.getenv(ACCESS_KEY_SECRET_ENV);

        List<String> missing = new ArrayList<>();
        if (StringUtil.isBlank(accessKeyId)) {
            missing.add(ACCESS_KEY_ID_ENV);
        }
        if (StringUtil.isBlank(accessKeySecret)) {
            missing.add(ACCESS_KEY_SECRET_ENV);
        }
        if (StringUtil.isBlank(signName)) {
            missing.add("ALIYUN_SMS_SIGN_NAME");
        }
        if (StringUtil.isBlank(templateCode)) {
            missing.add("ALIYUN_SMS_TEMPLATE_CODE");
        }
        if (!missing.isEmpty()) {
            // Environment variable names, not Spring keys: they are what an operator has to fill.
            log.warn("Aliyun SMS is not configured (missing {}); verification codes will be LOGGED, "
                    + "not sent. These are process environment variables, read once at startup - set "
                    + "them and restart this service to enable real delivery. Never keep this mode in "
                    + "production: the log then holds the full code.", String.join(", ", missing));
            this.client = null;
            this.signName = null;
            this.templateCode = null;
            return;
        }

        try {
            this.client = new Client(new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint(endpoint));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create Aliyun SMS client", ex);
        }
        this.signName = signName;
        this.templateCode = templateCode;
    }

    @Override
    protected void deliver(String target, VerifyScene scene, String code, long expireMinutes) {
        if (client == null) {
            log.info("[DEGRADED][SMS] target={}, scene={}, code={}, ttl={}min",
                    MaskUtil.maskPhone(target), scene, code, expireMinutes);
            return;
        }
        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(target)
                .setSignName(signName)
                .setTemplateCode(templateCode)
                .setTemplateParam(JsonUtil.toJson(Map.of("code", code, "time", expireMinutes)));
        try {
            SendSmsResponse response = client.sendSms(request);
            String providerCode = response.getBody() == null ? null : response.getBody().getCode();
            if (!"OK".equalsIgnoreCase(providerCode)) {
                String message = response.getBody() == null ? "empty response" : response.getBody().getMessage();
                throw new IllegalStateException("Aliyun SMS rejected: " + providerCode + " " + message);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Aliyun SMS delivery failed", ex);
        }
    }
}
