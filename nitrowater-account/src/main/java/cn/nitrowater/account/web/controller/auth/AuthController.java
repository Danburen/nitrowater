package cn.nitrowater.account.web.controller.auth;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import cn.nitrowater.account.web.dto.request.ForgotPasswordReAuthReq;
import cn.nitrowater.account.web.dto.request.ForgotPasswordResetReq;
import cn.nitrowater.account.web.dto.request.ForgotPasswordVerifyReq;
import cn.nitrowater.account.web.dto.response.ReAuthKeyVo;
import cn.nitrowater.account.web.dto.response.ReAuthTokenVo;
import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.lib.api.AuthCode;
import cn.nitrowater.lib.common.TokenResult;
import cn.nitrowater.lib.common.exceptions.AuthException;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.req.auth.RegisterRequest;
import cn.nitrowater.core.api.req.auth.SendCodeReq;
import cn.nitrowater.core.api.resp.auth.CodeResult;
import cn.nitrowater.core.infrastructure.aspect.RateLimit;
import cn.nitrowater.core.infrastructure.utils.CookieUtil;
import cn.nitrowater.core.infrastructure.utils.ResponseUtil;
import cn.nitrowater.core.application.account.AccountCoreService;
import cn.nitrowater.core.application.auth.LineCaptchaResult;
import cn.nitrowater.core.application.auth.SingleUseTokenService;
import cn.nitrowater.core.application.auth.code.VerificationService;
import cn.nitrowater.core.application.auth.impl.CaptchaServiceImpl;
import cn.nitrowater.core.application.auth.impl.RegisterServiceImpl;

import java.io.IOException;

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


    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    @RateLimit(key = "auth.captcha", permits = 30, window = 60)
    public void getCaptcha(HttpServletResponse response) throws IOException {
        LineCaptchaResult result = captchaService.generateCaptcha();
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
    public ApiResponse<Void> sendCode(@Valid @RequestBody SendCodeReq dto,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        String captchaKey = CookieUtil.getCookieValue(request.getCookies(), "CAPTCHA_KEY");
        if (!captchaService.verifyCode(captchaKey, dto.getCaptcha())) {
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
        String captchaKey = CookieUtil.getCookieValue(request.getCookies(), "CAPTCHA_KEY");
        if (!captchaService.verifyCode(captchaKey, body.getCaptcha())) {
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
        registerService.register(dto, CookieUtil.getCookieValue(request.getCookies(), "SMS_CODE_KEY"));
        // No token is issued here: the client signs in through the SSO form login / OIDC afterwards.
        return ApiResponse.success();
    }

}
