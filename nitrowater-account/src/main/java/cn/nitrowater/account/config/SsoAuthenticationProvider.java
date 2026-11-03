package cn.nitrowater.account.config;

import cn.nitrowater.account.security.SsoUserPrincipal;
import cn.nitrowater.account.security.UserRoleService;
import cn.nitrowater.core.api.auth.LoginResult;
import cn.nitrowater.core.api.req.auth.DeviceInfo;
import cn.nitrowater.core.api.req.auth.PwdLoginReq;
import cn.nitrowater.core.domain.user.User;
import cn.nitrowater.core.exception.threshold.AttemptLimitExceededException;
import cn.nitrowater.core.exception.threshold.DailyLimitExceededException;
import cn.nitrowater.core.application.auth.DeviceService;
import cn.nitrowater.core.application.auth.impl.LoginServiceImpl;
import cn.nitrowater.lib.common.exceptions.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashSet;
import java.util.Set;
import cn.nitrowater.lib.utils.StringUtil;

/**
 * Phase 2: delegates authentication to the existing {@link LoginServiceImpl}
 * (captcha / lockout / DEK reused) and returns an {@link SsoUserPrincipal}.
 *
 * <p>Captcha and deviceFp are read from the form login request; roles are loaded
 * from user_role for the token's roles claim.</p>
 */
@Component
@RequiredArgsConstructor
public class SsoAuthenticationProvider implements AuthenticationProvider {

    private final LoginServiceImpl loginService;
    private final DeviceService deviceService;
    private final UserRoleService userRoleService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String identifier = String.valueOf(authentication.getPrincipal());
        String password = String.valueOf(authentication.getCredentials());

        HttpServletRequest request = currentRequest();
        String captcha = request != null ? request.getParameter("captcha") : null;
        String deviceFp = request != null ? request.getParameter("deviceFp") : null;
        // The challenge lives in the servlet session, never in a cookie: the captcha image request
        // created it a moment ago, and a stale/aborted CAPTCHA_KEY must not decide what we verify.
        HttpSession session = request != null ? request.getSession(false) : null;

        PwdLoginReq req = new PwdLoginReq();
        req.setIdentifier(identifier);
        req.setPassword(password);
        req.setCaptcha(captcha);
        DeviceInfo di = new DeviceInfo();
        di.setDeviceFp(StringUtil.isNotBlank(deviceFp) ? deviceFp : "sso-unknown-device");
        req.setDeviceInfo(di);

        try {
            LoginResult result = loginService.login(req, session);
            User user = result.user();
            String did = (StringUtil.isNotBlank(deviceFp))
                    ? deviceService.computeDid(user.getUid(), deviceFp)
                    : null;
            SsoUserPrincipal principal = SsoUserPrincipal.of(
                    user,
                    deviceFp,
                    did,
                    userRoleService.findRoleCodes(user.getUid())
            );
            // Mark the authentication factor (PASSWORD). Spring Security's
            // AbstractUserDetailsAuthenticationProvider does this for the standard form login;
            // since this provider is custom it must add it explicitly, otherwise the AS cannot
            // derive the id_token `auth_time` claim once an OIDC session is tracked.
            Set<GrantedAuthority> authorities = new LinkedHashSet<>(principal.getAuthorities());
            authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY));
            return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        } catch (AuthenticationException e) {
            throw e;
        } catch (AttemptLimitExceededException | DailyLimitExceededException e) {
            // A lockout is not a wrong password: keep it distinguishable for logs and for anyone
            // wiring a failure handler (the default handler still redirects to /login?error).
            throw new LockedException(e.getMessage(), e);
        } catch (AuthException e) {
            throw new BadCredentialsException(e.getMessage() == null ? "login failed" : e.getMessage(), e);
        } catch (RuntimeException e) {
            // Anything else is our bug or an outage — never reported as bad credentials, or a broken
            // login looks like a user typo forever.
            throw new AuthenticationServiceException("login failed unexpectedly", e);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }
}
