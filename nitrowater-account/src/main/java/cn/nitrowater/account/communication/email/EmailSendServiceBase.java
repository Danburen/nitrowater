package cn.nitrowater.account.communication.email;

import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.lib.api.BaseResponseCode;
import cn.nitrowater.lib.utils.StringUtil;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

/**
 * Shared template rendering for {@link EmailSendService} implementations.
 *
 * <p>Rendering is two-pass on purpose: the fragment is rendered standalone, then handed to the
 * layout as {@code content}. That keeps templates free of {@code th:insert} indirection (no dynamic
 * template names resolved while rendering) and makes a broken fragment fail loudly in a unit test
 * rather than inside a provider call.</p>
 */
public abstract class EmailSendServiceBase implements EmailSendService {

    /** Layout variable holding the already-rendered fragment body. */
    private static final String CONTENT_VARIABLE = "content";

    private final TemplateEngine templateEngine;
    private final String from;
    private final String logoUrl;

    protected EmailSendServiceBase(TemplateEngine templateEngine, String from, String logoUrl) {
        this.templateEngine = templateEngine;
        this.from = from;
        this.logoUrl = logoUrl;
    }

    @Override
    public void sendHtmlEmail(String to, String subject, EmailTemplateLayout layout,
            EmailTemplateFragment fragment, Map<String, Object> data) {
        sendHtmlEmail(to, subject, render(layout, fragment, data));
    }

    /**
     * Renders {@code fragment} inside {@code layout}.
     *
     * @param layout   wrapper template
     * @param fragment content template
     * @param data     variables exposed to both templates
     * @return complete HTML document
     */
    protected String render(EmailTemplateLayout layout, EmailTemplateFragment fragment,
            Map<String, Object> data) {
        Context context = new Context(Locale.SIMPLIFIED_CHINESE);
        if (data != null) {
            context.setVariables(data);
        }
        context.setVariable("logoUrl", logoUrl);
        String content = templateEngine.process("fragment/" + fragment.templateKey(), context);
        context.setVariable(CONTENT_VARIABLE, content);
        return templateEngine.process("layout/" + layout.templateKey(), context);
    }

    /**
     * @return the configured sender address
     * @throws BizException when the sender address is not configured
     */
    protected String from() {
        if (StringUtil.isBlank(from)) {
            throw new BizException(BaseResponseCode.COMMUNICATION_SEND_FAILED);
        }
        return from;
    }

    /**
     * @param to recipient address
     * @throws BizException when the recipient is blank
     */
    protected void requireRecipient(String to) {
        if (StringUtil.isBlank(to)) {
            throw new BizException(BaseResponseCode.COMMUNICATION_SEND_FAILED);
        }
    }
}
