package cn.nitrowater.account.infra.communication.email;

import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.lib.utils.MaskUtil;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;

/**
 * {@link EmailSendService} backed by Resend's HTTP API.
 *
 * <p>Only registered when {@code app.communication.mode=real}; mock mode keeps the console sender
 * and never constructs a Resend client. There is deliberately no SMTP fallback: a launch either has
 * a working Resend key or it fails at startup (see {@code ResendConfig}), instead of silently
 * degrading to "the code is in the log".</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.communication", name = "mode", havingValue = "real")
public class ResendEmailSendService extends EmailSendServiceBase {

    private final Resend resend;

    public ResendEmailSendService(TemplateEngine templateEngine,
            Resend resend,
            @Value("${app.communication.email.from:}") String from,
            @Value("${app.communication.email.logo-url:}") String logoUrl) {
        super(templateEngine, from, logoUrl);
        this.resend = resend;
    }

    @Override
    public void sendSimpleEmail(String to, String subject, String text) {
        requireRecipient(to);
        deliver(CreateEmailOptions.builder()
                .from(from())
                .to(to)
                .subject(subject)
                .text(text)
                .build(), to);
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String html) {
        requireRecipient(to);
        deliver(CreateEmailOptions.builder()
                .from(from())
                .to(to)
                .subject(subject)
                .html(html)
                .build(), to);
    }

    /**
     * Sends one prepared message and translates a provider failure into a domain error.
     *
     * @param options complete Resend request
     * @param to      recipient, used for the masked log line only
     * @throws BizException when Resend rejects the request or the call fails
     */
    private void deliver(CreateEmailOptions options, String to) {
        try {
            resend.emails().send(options);
        } catch (ResendException ex) {
            // Masked recipient + provider message only; the payload may carry the code, so the
            // rendered body never reaches the log.
            log.error("Resend rejected email to {}: status={}, error={}",
                    MaskUtil.maskEmail(to), ex.getStatusCode(), ex.getErrorName(), ex);
            throw new BizException(BaseResponseCode.COMMUNICATION_SEND_FAILED,
                    HttpStatus.BAD_GATEWAY);
        }
    }
}
