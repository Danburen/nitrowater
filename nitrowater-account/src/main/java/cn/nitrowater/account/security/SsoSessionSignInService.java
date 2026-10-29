package cn.nitrowater.account.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Establishes an authenticated SSO web session after a non-form login (currently code login).
 *
 * <p>The form-login filter normally performs these three steps for us. A JSON endpoint runs after
 * that filter, so it must do them explicitly or the next request looks anonymous:</p>
 * <ol>
 *   <li>create/rotate the HTTP session (session-fixation defence);</li>
 *   <li>persist the security context where the servlet chain reads it;</li>
 *   <li>register the session with the {@link SessionRegistry} so the expiry filter and
 *       credential-change invalidation can find it.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class SsoSessionSignInService {

    private final SessionRegistry sessionRegistry;

    /** Uses the same session attribute key as the servlet security chain. */
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    /**
     * @param request        current servlet request
     * @param response       current servlet response
     * @param authentication fully built authentication (principal + factor authorities)
     */
    public void signIn(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        // Create first: changeSessionId() throws when no session exists yet.
        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextRepository.saveContext(context, request, response);
        SecurityContextHolder.setContext(context);

        sessionRegistry.registerNewSession(request.getSession().getId(), authentication.getPrincipal());
    }
}
