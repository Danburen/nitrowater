package cn.nitrowater.account.config;

import cn.nitrowater.account.security.SsoUserPrincipal;
import cn.nitrowater.account.security.UserRoleService;
import cn.nitrowater.core.api.auth.LoginResult;
import cn.nitrowater.core.api.req.auth.DeviceInfo;
import cn.nitrowater.core.api.req.auth.PwdLoginReq;
import cn.nitrowater.core.entity.user.User;
import cn.nitrowater.core.infrastructure.utils.CookieUtil;
import cn.nitrowater.core.services.auth.DeviceService;
import cn.nitrowater.core.services.auth.impl.LoginServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
        String captchaKey = request != null
                ? CookieUtil.getCookieValue(request.getCookies(), "CAPTCHA_KEY") : null;

        PwdLoginReq req = new PwdLoginReq();
        req.setIdentifier(identifier);
        req.setPassword(password);
        req.setCaptcha(captcha);
        DeviceInfo di = new DeviceInfo();
        di.setDeviceFp(deviceFp != null && !deviceFp.isBlank() ? deviceFp : "sso-unknown-device");
        req.setDeviceInfo(di);

        try {
            LoginResult result = loginService.login(req, captchaKey);
            User user = result.user();
            String did = (deviceFp != null && !deviceFp.isBlank())
                    ? deviceService.calculaateDid(user.getUid(), deviceFp)
                    : null;
            SsoUserPrincipal principal = SsoUserPrincipal.of(
                    user, deviceFp, did, userRoleService.findRoleCodes(user.getUid()));
            return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        } catch (RuntimeException e) {
            throw new BadCredentialsException(e.getMessage() == null ? "login failed" : e.getMessage(), e);
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
