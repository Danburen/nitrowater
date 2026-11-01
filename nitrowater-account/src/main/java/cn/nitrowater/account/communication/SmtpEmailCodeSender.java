package cn.nitrowater.account.communication;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.application.auth.code.AbstractRedisCodeSender;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP delivery for email verification codes.
 *
 * <p>Selected when {@code app.communication.mode=real}. The SMTP connection itself comes from
 * Spring Boot's standard {@code spring.mail.*} properties.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.communication", name = "mode", havingValue = "real")
public class SmtpEmailCodeSender extends AbstractRedisCodeSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailCodeSender(RedisHelperHolder redisHelper,
            JavaMailSender mailSender,
            @Value("${spring.mail.host:}") String host,
            @Value("${app.communication.email.from:}") String from,
            @Value("${expire.email.verify:5}") long expireMinutes) {
        super(redisHelper, VerifyChannel.EMAIL, expireMinutes);
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("spring.mail.host is required when app.communication.mode=real");
        }
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("app.communication.email.from is required when app.communication.mode=real");
        }
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    protected void deliver(String target, VerifyScene scene, String code, long expireMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(target);
        message.setSubject("NitroWater 验证码");
        message.setText("您的验证码是 " + code + "，有效期 " + expireMinutes + " 分钟。请勿转发给他人。");
        mailSender.send(message);
    }
}
