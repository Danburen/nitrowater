package cn.nitrowater.account.communication.email;

import java.util.Map;

/**
 * Outbound email delivery.
 *
 * <p>The sender address is configuration, not a parameter: every message leaves through the same
 * verified domain, which is what keeps SPF/DKIM/DMARC aligned. Callers only choose the recipient,
 * the subject and the body.</p>
 *
 * <p>Implementations must treat a provider failure as a real failure: they throw
 * {@link cn.nitrowater.core.exception.BizException} with
 * {@link cn.nitrowater.lib.api.BaseResponseCode#COMMUNICATION_SEND_FAILED}. Nothing here degrades
 * to logging, because a code that only exists in the log is a code the user never received.</p>
 */
public interface EmailSendService {

    /**
     * Sends a plain-text message.
     *
     * @param to      recipient address
     * @param subject subject line
     * @param text    body
     */
    void sendSimpleEmail(String to, String subject, String text);

    /**
     * Sends an already-rendered HTML message.
     *
     * @param to      recipient address
     * @param subject subject line
     * @param html    body, already escaped as far as the caller requires
     */
    void sendHtmlEmail(String to, String subject, String html);

    /**
     * Sends a templated HTML message: renders {@code fragment} inside {@code layout}.
     *
     * @param to       recipient address
     * @param subject  subject line
     * @param layout   wrapper template
     * @param fragment content template
     * @param data     variables exposed to both templates
     */
    void sendHtmlEmail(String to, String subject, EmailTemplateLayout layout,
            EmailTemplateFragment fragment, Map<String, Object> data);
}
