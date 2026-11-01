package cn.nitrowater.account.communication;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.application.auth.code.AbstractRedisCodeSender;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.lib.utils.JsonUtil;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Aliyun SMS delivery for phone verification codes.
 *
 * <p>Selected when {@code app.communication.mode=real}. Credentials and the approved template are
 * configuration, never hard-coded.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.communication", name = "mode", havingValue = "real")
public class AliyunSmsCodeSender extends AbstractRedisCodeSender {

    private final Client client;
    private final String signName;
    private final String templateCode;

    public AliyunSmsCodeSender(RedisHelperHolder redisHelper,
            @Value("${app.communication.sms.access-key-id:}") String accessKeyId,
            @Value("${app.communication.sms.access-key-secret:}") String accessKeySecret,
            @Value("${app.communication.sms.sign-name:}") String signName,
            @Value("${app.communication.sms.template-code:}") String templateCode,
            @Value("${app.communication.sms.endpoint:dysmsapi.aliyuncs.com}") String endpoint,
            @Value("${expire.sms-code:2}") long expireMinutes) {
        super(redisHelper, VerifyChannel.SMS, expireMinutes);
        if (isBlank(accessKeyId) || isBlank(accessKeySecret) || isBlank(signName) || isBlank(templateCode)) {
            throw new IllegalStateException(
                    "Aliyun SMS credentials, sign-name and template-code are required when app.communication.mode=real");
        }
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint(endpoint);
            this.client = new Client(config);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create Aliyun SMS client", ex);
        }
        this.signName = signName;
        this.templateCode = templateCode;
    }

    @Override
    protected void deliver(String target, VerifyScene scene, String code, long expireMinutes) {
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
