package cn.nitrowater.account.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import cn.nitrowater.core.lib.api.auth.LoginResult;
import cn.nitrowater.core.lib.api.req.auth.DeviceInfo;
import cn.nitrowater.core.lib.api.req.auth.PwdLoginReq;
import cn.nitrowater.core.lib.infrastructure.utils.CookieUtil;
import cn.nitrowater.core.lib.services.auth.impl.LoginServiceImpl;

/**
 * 把现有 {@link LoginServiceImpl}（密码/验证码/锁定/DEK）包成 Spring Security 的 AuthenticationProvider。
 * <p>表单登录时额外读取 captcha / deviceFp（由 SSO 登录页提交）。
 * 成功后 principal.name = <b>uid</b>，使 OIDC 的 {@code sub} = uid。</p>
 */
@Component
@RequiredArgsConstructor
public class SsoAuthenticationProvider implements AuthenticationProvider {

    private final LoginServiceImpl loginService;

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
            long uid = result.user().getUid();
            UserDetails principal = User.withUsername(String.valueOf(uid))
                    .password("")
                    .authorities(AuthorityUtils.createAuthorityList("ROLE_USER"))
                    .build();
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
