package cn.nitrowater.bff.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Records every successful OIDC login in the session's account pool, then redirects to {@code /}.
 *
 * <p>The redirect policy is unchanged from the previous {@code defaultSuccessUrl("/", true)}: the
 * SPA resumes its own in-app destination from {@code sessionStorage}, so the BFF must not try to
 * replay a saved request.</p>
 *
 * <p>The session is stamped with the current release epoch so a later deployment can invalidate it
 * (see {@link SessionEpochFilter}).</p>
 */
@Component
public class AccountLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final AccountSessionService accounts;
    private final String sessionEpoch;

    public AccountLoginSuccessHandler(AccountSessionService accounts,
            @Value("${app.session.epoch:dev}") String sessionEpoch) {
        this.accounts = accounts;
        this.sessionEpoch = sessionEpoch;
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        accounts.registerLogin(request, authentication);
        // Session fixation protection has already rotated the session, so this is the final one.
        request.getSession().setAttribute(SessionEpochFilter.EPOCH_ATTRIBUTE, sessionEpoch);
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
