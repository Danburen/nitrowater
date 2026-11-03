package cn.nitrowater.account.communication;

import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.application.auth.code.AbstractRedisCodeSender;
import cn.nitrowater.core.infrastructure.RedisHelperHolder;
import cn.nitrowater.lib.utils.MaskUtil;
import cn.nitrowater.lib.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SMTP delivery for email verification codes.
 *
 * <p>Selected when {@code app.communication.mode=real}. The SMTP connection itself comes from
 * Spring Boot's standard {@code spring.mail.*} properties.</p>
 *
 * <p>When the mail sender is not configured the sender <em>degrades to logging</em> (like mock
 * mode) so a local {@code mode=real} run still starts without credentials; a WARN names the
 * missing keys. Production deployments must supply the credentials - the degraded code only lands
 * in the log, so no user would ever receive it.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.communication", name = "mode", havingValue = "real")
public class SmtpEmailCodeSender extends AbstractRedisCodeSender {

    /** {@code null} means "not configured": codes are logged instead of sent. */
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailCodeSender(RedisHelperHolder redisHelper,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String host,
            @Value("${app.communication.email.from:}") String from,
            @Value("${expire.email.verify:5}") long expireMinutes) {
        super(redisHelper, VerifyChannel.EMAIL, expireMinutes);

        // Spring Boot only auto-configures JavaMailSender when spring.mail.host has text, so a
        // blank host also means "no bean" - both cases are the same missing configuration here.
        JavaMailSender candidate = mailSenderProvider.getIfAvailable();
        List<String> missing = new ArrayList<>();
        if (StringUtil.isBlank(host)) {
            missing.add("MAIL_HOST");
        }
        if (StringUtil.isBlank(from)) {
            missing.add("MAIL_FROM");
        }
        if (StringUtil.isNotBlank(host) && candidate == null) {
            // Host is set but Boot still did not create the bean: a classpath/autoconfig problem,
            // not a missing variable, so name it separately.
            missing.add("JavaMailSender bean");
        }
        if (!missing.isEmpty()) {
            // Environment variable names, not Spring keys: they are what an operator has to fill.
            log.warn("SMTP is not configured (missing {}); verification codes will be LOGGED, not "
                    + "sent. MAIL_HOST also gates Spring Boot's JavaMailSender auto-configuration. "
                    + "These are process environment variables, read once at startup - set them and "
                    + "restart this service to enable real delivery. Never keep this mode in "
                    + "production: the log then holds the full code.", String.join(", ", missing));
            this.mailSender = null;
            this.from = null;
            return;
        }
        this.mailSender = candidate;
        this.from = from;
    }

    @Override
    protected void deliver(String target, VerifyScene scene, String code, long expireMinutes) {
        if (mailSender == null) {
            log.info("[DEGRADED][EMAIL] target={}, scene={}, code={}, ttl={}min",
                    MaskUtil.maskEmail(target), scene, code, expireMinutes);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(target);
        message.setSubject("NitroWater 验证码");
        message.setText("您的验证码是 " + code + "，有效期 " + expireMinutes + " 分钟。请勿转发给他人。");
        mailSender.send(message);
    }
}
