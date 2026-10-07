package cn.nitrowater.core.configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import cn.nitrowater.core.infrastructure.utils.CookieUtil;

/**
 * 从 {@code app.cookie.*} 注入 Cookie 的 SameSite / Secure（默认 Strict/false）。
 * <p>跨主域部署（如 waterfun.top ↔ nitrowater.cn）需设：
 * {@code app.cookie.same-site=None} + {@code app.cookie.secure=true}（HTTPS）。</p>
 */
@Slf4j
@Configuration
public class CookieConfig {

    @Value("${app.cookie.same-site:Strict}")
    private String sameSite;

    @Value("${app.cookie.secure:false}")
    private boolean secure;

    @PostConstruct
    public void init() {
        CookieUtil.configure(sameSite, secure);
        log.info("Cookie configured: SameSite={}, Secure={}", sameSite, secure);
    }
}
