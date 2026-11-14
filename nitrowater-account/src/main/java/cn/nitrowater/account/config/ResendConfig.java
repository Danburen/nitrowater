package cn.nitrowater.account.config;

import cn.nitrowater.lib.utils.StringUtil;
import com.resend.Resend;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resend client for the account service.
 *
 * <p>Only created in {@code real} mode. A missing key or sender is a startup failure rather than a
 * runtime surprise: with no SMTP fallback, empty values would otherwise turn every "send code"
 * request into a 500 and leave the operator hunting for them in production. Failing here means the
 * container never reaches a healthy state, which is what surfaces the misconfiguration.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.communication", name = "mode", havingValue = "real")
public class ResendConfig {

    /**
     * @param apiKey Resend API key ({@code re_...})
     * @param from   sender address on a domain verified in Resend
     * @return client bound to the account's API key
     */
    @Bean
    public Resend resend(@Value("${app.communication.email.resend.api-key:}") String apiKey,
            @Value("${app.communication.email.from:}") String from) {
        if (StringUtil.isBlank(apiKey)) {
            throw new IllegalStateException(
                    "RESEND_API_KEY is required when app.communication.mode=real");
        }
        if (StringUtil.isBlank(from)) {
            throw new IllegalStateException(
                    "MAIL_FROM is required when app.communication.mode=real");
        }
        return new Resend(apiKey);
    }
}
