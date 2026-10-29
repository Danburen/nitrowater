package cn.nitrowater.account.config;

import cn.nitrowater.lib.api.AuthCode;
import cn.nitrowater.lib.api.BaseResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Maps form-login failures to a small, stable {@code error} code on the hosted login page.
 *
 * <p>{@link SsoAuthenticationProvider} keeps the original auth code as the exception message, so
 * the page can tell a bad captcha from bad credentials instead of showing one vague sentence for
 * everything.</p>
 */
@Component
public class SsoLoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(SsoLoginFailureHandler.class);

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String error = errorCode(exception);
        if ("server".equals(error)) {
            log.warn("SSO form login failed unexpectedly", exception);
        } else {
            log.debug("SSO form login failed: error={}, type={}", error, exception.getClass().getSimpleName());
        }

        StringBuilder target = new StringBuilder("/login?error=").append(error);
        String redirect = LoginRedirects.safeLocalPath(request.getParameter(LoginRedirects.PARAM));
        if (redirect != null) {
            target.append('&').append(LoginRedirects.PARAM).append('=')
                    .append(URLEncoder.encode(redirect, StandardCharsets.UTF_8));
        }
        getRedirectStrategy().sendRedirect(request, response, target.toString());
    }

    /**
     * Classifies the authentication failure for the login page.
     *
     * @param exception failure raised by Spring Security
     * @return {@code captcha}, {@code credentials}, {@code disabled}, {@code locked},
     *         {@code daily} or {@code server}
     */
    private static String errorCode(AuthenticationException exception) {
        if (exception instanceof LockedException locked) {
            return BaseResponseCode.DAILY_LIMIT_EXCEEDED.getCode().equals(locked.getMessage())
                    ? "daily" : "locked";
        }
        if (exception instanceof BadCredentialsException bad) {
            if (AuthCode.CAPTCHA_INVALID.getCode().equals(bad.getMessage())) {
                return "captcha";
            }
            // Only reached after the password matched, so this reveals nothing a credential
            // guesser did not already prove. It gives a disabled user an actionable message.
            if (AuthCode.ACCOUNT_DISABLED.getCode().equals(bad.getMessage())) {
                return "disabled";
            }
            return "credentials";
        }
        return "server";
    }
}
