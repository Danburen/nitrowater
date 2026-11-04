package cn.nitrowater.account.web.controller.auth;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import cn.nitrowater.account.config.LoginRedirectResolver;
import cn.nitrowater.account.security.PromptLoginFilter;
import cn.nitrowater.account.security.SsoSessionSignInService;
import cn.nitrowater.account.security.SsoUserPrincipal;
import cn.nitrowater.account.security.UserRoleService;
import cn.nitrowater.account.web.dto.request.LoginCodeReq;
import cn.nitrowater.account.web.dto.response.LoginRedirectVo;
import cn.nitrowater.account.web.dto.request.ForgotPasswordReAuthReq;
import cn.nitrowater.account.web.dto.request.ForgotPasswordResetReq;
import cn.nitrowater.account.web.dto.request.ForgotPasswordVerifyReq;
import cn.nitrowater.account.web.dto.response.ReAuthKeyVo;
import cn.nitrowater.account.web.dto.response.ReAuthTokenVo;
import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.lib.api.AuthCode;
import cn.nitrowater.lib.common.TokenResult;
import cn.nitrowater.lib.common.exceptions.AuthException;
import cn.nitrowater.core.api.auth.LoginResult;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.req.auth.RegisterRequest;
import cn.nitrowater.core.api.req.auth.SendCodeReq;
import cn.nitrowater.core.api.req.auth.VerifyCodeDto;
import cn.nitrowater.core.api.resp.auth.CodeResult;
import cn.nitrowater.core.infrastructure.aspect.RateLimit;
import cn.nitrowater.core.infrastructure.utils.CookieUtil;
import cn.nitrowater.core.infrastructure.utils.ResponseUtil;
import cn.nitrowater.core.application.account.AccountCoreService;
import cn.nitrowater.core.application.auth.AuthIdentifierUtil;
import cn.nitrowater.core.application.auth.DeviceService;
import cn.nitrowater.core.application.auth.LineCaptchaResult;
import cn.nitrowater.core.application.auth.LoginService;
import cn.nitrowater.core.application.auth.SingleUseTokenService;
import cn.nitrowater.core.application.auth.code.VerificationService;
import cn.nitrowater.core.application.auth.impl.CaptchaServiceImpl;
import cn.nitrowater.core.application.auth.impl.RegisterServiceImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import cn.nitrowater.lib.utils.StringUtil;

@Slf4j
@RestController
@Validated
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final CaptchaServiceImpl captchaService;
    private final RegisterServiceImpl registerService;
    private final VerificationService verificationService;
    private final SingleUseTokenService singleUseTokenService;
    private final AccountCoreService accountCoreService;
    private final LoginService loginService;
    private final DeviceService deviceService;
    private final UserRoleService userRoleService;
    private final SsoSessionSignInService sessionSignInService;
    private final LoginRedirectResolver loginRedirectResolver;


    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    // Per-IP, not a literal key: a shared bucket means one busy client locks every user out.
    @RateLimit(key = "ip", permits = 20, window = 60)
    public void getCaptcha(HttpSession session, HttpServletResponse response) throws IOException {
        LineCaptchaResult result = captchaService.generateCaptcha(session);
        // Diagnostic mirror only: the challenge is bound to the HTTP session (CaptchaServiceImpl),
        // and verification no longer reads this cookie. Kept so dev tooling can map a rendered image
        // back to its Redis entry (bin/dev/seed-test-users.ps1 reads the code by this uuid).
        Cookie cookie = new Cookie("CAPTCHA_KEY", result.uuid());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(120);
        response.addCookie(cookie);
        // set the header ofPending response
        response.setContentType("image/jpeg");
        response.setHeader("Pragma", "No-cache");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setDateHeader("Expires", 0);
        // write img stream to response stream
        result.captcha().write(response.getOutputStream());
    }

    /***
     * Send sms/email code api for scene only allow login or register
     * @param dto send code request
     * @param response http response
     * @return send code result
     */
    @Operation(summary = "发送验证码（需图形验证码）")
    @PostMapping("/send-code")
    @RateLimit(key = "ip", permits = 10, window = 60)
    public ApiResponse<Void> sendCode(@Valid @RequestBody SendCodeReq dto,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        // Check the resend window first: on 429 the captcha must remain usable for a retry.
        verificationService.assertSendable(dto.getTarget(), dto.getChannel());
        if (!captchaService.verifyCode(request.getSession(false), dto.getCaptcha())) {
            throw new AuthException(AuthCode.CAPTCHA_INVALID);
        }
        CodeResult result = verificationService.sendCodeForAnonymous(dto);
        String cookieKey = dto.getChannel().name() + "_CODE_KEY";
        ResponseUtil.setCookieAndNoCache(response, cookieKey, result.getKey(), 120);
        return ApiResponse.success();
    }

    @Operation(summary = "忘记密码 - 发送验证码到绑定手机（通过任意标识符）")
    @PostMapping("/forgot-password/re-auth")
    @RateLimit(key = "ip", permits = 3, window = 60)
    public ApiResponse<ReAuthKeyVo> forgotPasswordReAuth(
            @Valid @RequestBody ForgotPasswordReAuthReq body,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!captchaService.verifyCode(request.getSession(false), body.getCaptcha())) {
            throw new AuthException(AuthCode.CAPTCHA_INVALID);
        }
        String reAuthKey = accountCoreService.initiateForgotPasswordReAuth(body.getIdentifier());
        if (reAuthKey != null) {
            ResponseUtil.setCookieAndNoCache(response, "SMS_CODE_KEY", reAuthKey, 300);
            return ApiResponse.success(new ReAuthKeyVo(reAuthKey));
        }
        return ApiResponse.success(new ReAuthKeyVo(null));
    }

    @Operation(summary = "忘记密码 - 验证码确认，获取 reAuthToken")
    @PostMapping("/forgot-password/re-auth/verify")
    @RateLimit(key = "ip", permits = 5, window = 300)
    public ApiResponse<ReAuthTokenVo> forgotPasswordVerifyReAuth(@Valid @RequestBody ForgotPasswordVerifyReq body) {
        TokenResult token = accountCoreService.verifyForgotPasswordReAuth(
                body.getReAuthKey(), body.getCode()
        );
        return ApiResponse.success(ReAuthTokenVo.of(token));
    }

    @Operation(summary = "忘记密码 - 使用 reAuthToken 重置密码")
    @PostMapping("/forgot-password/reset")
    @RateLimit(key = "ip", permits = 3, window = 300)
    public ApiResponse<Void> forgotPasswordReset(@Valid @RequestBody ForgotPasswordResetReq body) {
        Long uid = singleUseTokenService.consumeVerifyToken(body.getReAuthToken(), VerifyScene.FORGOT_PASSWORD.name());
        accountCoreService.resetPasswordByToken(uid, body.getNewPwd(), body.getConfirmPwd(), body.getDeviceInfo());
        return ApiResponse.success();
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    @RateLimit(key = "ip", permits = 3, window = 300)
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest dto, HttpServletRequest request) {
        registerService.register(
                dto,
                CookieUtil.getCookieValue(request.getCookies(), "SMS_CODE_KEY"),
                CookieUtil.getCookieValue(request.getCookies(), "EMAIL_CODE_KEY")
        );
        // No token is issued here: the client signs in through the SSO form login / OIDC afterwards.
        return ApiResponse.success();
    }

    @Operation(summary = "验证码快捷登录（自动识别手机号/邮箱）")
    @PostMapping("/login-by-code")
    @RateLimit(key = "ip", permits = 10, window = 60)
    public ApiResponse<LoginRedirectVo> loginByCode(@Valid @RequestBody LoginCodeReq body,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        VerifyChannel channel;
        try {
            channel = AuthIdentifierUtil.detectChannel(body.getIdentifier());
        } catch (IllegalArgumentException e) {
            throw new AuthException(AuthCode.VERIFY_TARGET_UNSUPPORTED);
        }

        VerifyCodeDto verify = new VerifyCodeDto();
        verify.setChannel(channel);
        verify.setTarget(body.getIdentifier());
        verify.setScene(VerifyScene.LOGIN);
        verify.setCode(body.getCode());
        verify.setDeviceInfo(body.getDeviceInfo());

        String codeKey = CookieUtil.getCookieValue(request.getCookies(), channel.name() + "_CODE_KEY");
        LoginResult result = loginService.loginByCode(verify, codeKey);

        String deviceFp = body.getDeviceFp();
        if ((StringUtil.isBlank(deviceFp)) && body.getDeviceInfo() != null) {
            deviceFp = body.getDeviceInfo().getDeviceFp();
        }
        String did = (StringUtil.isNotBlank(deviceFp))
                ? deviceService.computeDid(result.user().getUid(), deviceFp)
                : null;
        SsoUserPrincipal principal = SsoUserPrincipal.of(
                result.user(), deviceFp, did, userRoleService.findRoleCodes(result.user().getUid()));

        Set<GrantedAuthority> authorities = new LinkedHashSet<>(principal.getAuthorities());
        authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.OTT_AUTHORITY));
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);

        sessionSignInService.signIn(request, response, authentication);
        String target = loginRedirectResolver.resolve(request, response);
        PromptLoginFilter.markIfNeeded(request.getSession(false), target);
        return ApiResponse.success(new LoginRedirectVo(target));
    }

}
