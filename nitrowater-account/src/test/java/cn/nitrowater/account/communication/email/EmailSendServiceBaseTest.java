package cn.nitrowater.account.communication.email;

import cn.nitrowater.account.config.EmailTemplateConfig;
import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.lib.api.BaseResponseCode;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the email template contract.
 *
 * <p>Rendering is two-pass (fragment first, then layout with the fragment as {@code content}), so a
 * broken template path or a stray {@code th:insert} would only surface when a real code is being
 * sent. These tests exercise the same engine the service uses and assert both the injected values
 * and the escaping behaviour.</p>
 */
class EmailSendServiceBaseTest {

    private static final String FROM = "NitroWater <no-reply@nitrowater.cn>";
    private static final String LOGO_URL = "https://nitrowater.cn/logo.png";

    private final TemplateEngine engine = new EmailTemplateConfig().emailTemplateEngine();

    @Test
    void rendersFragmentInsideLayout() {
        Map<String, Object> data = new HashMap<>();
        data.put("code", "123456");
        data.put("expireMinutes", 5L);
        data.put("sceneLabel", "登录");

        String html = service(LOGO_URL).renderForTest(
                EmailTemplateLayout.BASE, EmailTemplateFragment.VERIFY_CODE, data);

        assertTrue(html.contains("123456"), "verification code must reach the body");
        assertTrue(html.contains("登录"), "scene label must reach the body");
        assertTrue(html.contains(">5<"), "expiry must reach the body");
        assertTrue(html.contains("此邮件由系统自动发送"), "layout footer must wrap the fragment");
        assertTrue(html.contains("src=\"" + LOGO_URL + "\""), "logo must be rendered");
    }

    @Test
    void fallsBackToWordmarkWhenLogoUrlBlank() {
        Map<String, Object> data = new HashMap<>();
        data.put("code", "000000");
        data.put("expireMinutes", 5L);
        data.put("sceneLabel", "注册");

        String html = service("").renderForTest(
                EmailTemplateLayout.BASE, EmailTemplateFragment.VERIFY_CODE, data);

        assertFalse(html.contains("<img"), "blank logo must not emit an image tag");
        assertTrue(html.contains("NitroWater"));
    }

    @Test
    void escapesInjectedValues() {
        Map<String, Object> data = new HashMap<>();
        data.put("code", "<script>alert(1)</script>");
        data.put("expireMinutes", 5L);
        data.put("sceneLabel", "登录");

        String html = service(LOGO_URL).renderForTest(
                EmailTemplateLayout.BASE, EmailTemplateFragment.VERIFY_CODE, data);

        assertFalse(html.contains("<script>alert(1)</script>"), "values must be HTML-escaped");
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void rejectsBlankSender() {
        TestEmailSendService service = new TestEmailSendService(engine, " ", LOGO_URL);
        BizException ex = assertThrows(BizException.class, service::senderForTest);

        assertEquals(BaseResponseCode.COMMUNICATION_SEND_FAILED.getCode(), ex.getErrorCode());
    }

    @Test
    void rejectsBlankRecipient() {
        BizException ex = assertThrows(BizException.class,
                () -> service(LOGO_URL).requireRecipientForTest("  "));

        assertEquals(BaseResponseCode.COMMUNICATION_SEND_FAILED.getCode(), ex.getErrorCode());
    }

    private TestEmailSendService service(String logoUrl) {
        return new TestEmailSendService(engine, FROM, logoUrl);
    }

    /**
     * Exposes the protected rendering/validation hooks; sending itself is provider-specific and is
     * not exercised here.
     */
    private static final class TestEmailSendService extends EmailSendServiceBase {

        TestEmailSendService(TemplateEngine templateEngine, String from, String logoUrl) {
            super(templateEngine, from, logoUrl);
        }

        String renderForTest(EmailTemplateLayout layout, EmailTemplateFragment fragment,
                Map<String, Object> data) {
            return render(layout, fragment, data);
        }

        String senderForTest() {
            return from();
        }

        void requireRecipientForTest(String to) {
            requireRecipient(to);
        }

        @Override
        public void sendSimpleEmail(String to, String subject, String text) {
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public void sendHtmlEmail(String to, String subject, String html) {
            throw new UnsupportedOperationException("not used by this test");
        }
    }
}
