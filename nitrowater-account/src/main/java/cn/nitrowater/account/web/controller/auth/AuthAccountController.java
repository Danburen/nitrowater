package cn.nitrowater.account.web.controller.auth;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import cn.nitrowater.account.web.dto.request.*;
import cn.nitrowater.account.web.dto.response.EmailChangeVo;
import cn.nitrowater.account.web.dto.response.PhoneChangeVo;
import cn.nitrowater.account.web.dto.response.ReAuthInfoResp;
import cn.nitrowater.account.web.dto.response.ReAuthTokenVo;
import cn.nitrowater.account.web.dto.response.UserCenterResp;
import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.lib.common.TokenResult;
import cn.nitrowater.lib.utils.MaskUtil;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;
import cn.nitrowater.core.api.resp.AccountResp;
import cn.nitrowater.core.api.resp.auth.CodeResult;
import cn.nitrowater.core.infrastructure.aspect.RateLimit;
import cn.nitrowater.core.infrastructure.utils.CookieUtil;
import cn.nitrowater.core.infrastructure.utils.ResponseUtil;
import cn.nitrowater.core.infrastructure.utils.context.UserCtxHolder;
import cn.nitrowater.core.application.account.AccountCoreService;
import cn.nitrowater.core.application.auth.SingleUseTokenService;
import cn.nitrowater.core.application.auth.code.VerificationService;
import cn.nitrowater.core.application.user.UserCoreService;
import cn.nitrowater.core.application.user.UserDatumCoreService;
import cn.nitrowater.core.exception.BizException;
import cn.nitrowater.lib.api.BaseResponseCode;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/auth/account")
@RequiredArgsConstructor
public class AuthAccountController {
    private final AccountCoreService accountCoreService;
    private final VerificationService verificationService;
    private final UserDatumCoreService userDatumCoreService;
    private final UserCoreService userCoreService;
    private final SingleUseTokenService singleUseTokenService;

    @RateLimit(key = "account.default", permits = 10, window = 300)
    @GetMapping
    public ApiResponse<AccountResp> get() {
        return ApiResponse.success(
                userDatumCoreService.getAccountInfo(UserCtxHolder.getUserUid())
        );
    }

    /** Login: full user-center snapshot (identity, nickname and masked contacts). */
    @RateLimit(key = "account.default", permits = 30, window = 300)
    @GetMapping("/me")
    public ApiResponse<UserCenterResp> me() {
        long uid = UserCtxHolder.getUserUid();
        return ApiResponse.success(UserCenterResp.from(
                userCoreService.getUser(uid),
                userDatumCoreService.getAccountInfo(uid)));
    }

    /** Login: update the display nickname. */
    @RateLimit(key = "user", permits = 5, window = 300)
    @PutMapping("/nickname")
    public ApiResponse<Void> updateNickname(@Valid @RequestBody UpdateNicknameReq body) {
        userCoreService.updateNickname(UserCtxHolder.getUserUid(), body.getNickname());
        return ApiResponse.success();
    }

    @Operation(summary = "获取 re-auth 信息（掩码手机号）")
    @GetMapping("/re-auth/info")
    public ApiResponse<ReAuthInfoResp> getReAuthInfo() {
        long uid = UserCtxHolder.getUserUid();
        String rawPhone = userDatumCoreService.getRawPhone(uid);
        String rawEmail = userDatumCoreService.getRawEmail(uid);
        List<String> channels = new ArrayList<>(2);
        if (rawPhone != null) {
            channels.add(VerifyChannel.SMS.name());
        }
        if (rawEmail != null) {
            channels.add(VerifyChannel.EMAIL.name());
        }
        return ApiResponse.success(new ReAuthInfoResp(
                rawPhone == null ? null : MaskUtil.maskPhone(rawPhone),
                rawEmail == null ? null : MaskUtil.maskEmail(rawEmail),
                channels));
    }

    @Operation(summary = "发起 re-auth（直接发验证码到绑定手机）")
    @PostMapping("/re-auth")
    @RateLimit(key = "ip", permits = 3, window = 60)
    public ApiResponse<Void> reAuth(@Valid @RequestBody ReAuthReq body,
                                    HttpServletResponse response) {
        long uid = UserCtxHolder.getUserUid();
        VerifyChannel channel = resolveChannel(uid, body.getChannel());
        String target = targetFor(uid, channel);
        CodeResult result = verificationService.sendCodeForAuthenticated(target, channel, body.getScene());
        ResponseUtil.setCookieAndNoCache(response, reAuthCookieName(body.getScene(), channel),
                result.getKey(), 300);
        return ApiResponse.success();
    }

    @Operation(summary = "验证 re-auth 验证码，获取 reAuthToken")
    @PostMapping("/re-auth/verify")
    @RateLimit(key = "ip", permits = 5, window = 300)
    public ApiResponse<ReAuthTokenVo> verifyReAuth(@Valid @RequestBody ReAuthVerifyReq body,
                                                   HttpServletRequest request) {
        long uid = UserCtxHolder.getUserUid();
        VerifyChannel channel = resolveChannel(uid, body.getChannel());
        String target = targetFor(uid, channel);
        String verifyKey = CookieUtil.getCookieValue(request.getCookies(),
                reAuthCookieName(body.getScene(), channel));
        if (verifyKey == null) {
            verifyKey = CookieUtil.getCookieValue(request.getCookies(),
                    body.getScene().name() + "_REAUTH_KEY");
        }
        if (verifyKey == null) {
            return ApiResponse.success(new ReAuthTokenVo(null));
        }
        verificationService.verifyCode(target, body.getScene(), channel, verifyKey, body.getCode());
        TokenResult token = singleUseTokenService.generateVerifyToken(body.getScene().name(), uid);
        return ApiResponse.success(new ReAuthTokenVo(token.value()));
    }

    private VerifyChannel resolveChannel(long uid, VerifyChannel requested) {
        if (requested != null) {
            return requested;
        }
        if (userDatumCoreService.getRawPhone(uid) != null) {
            return VerifyChannel.SMS;
        }
        if (userDatumCoreService.getRawEmail(uid) != null) {
            return VerifyChannel.EMAIL;
        }
        throw new BizException(BaseResponseCode.NOT_FOUND);
    }

    private String targetFor(long uid, VerifyChannel channel) {
        String target = channel == VerifyChannel.EMAIL
                ? userDatumCoreService.getRawEmail(uid)
                : userDatumCoreService.getRawPhone(uid);
        if (target == null) {
            throw new BizException(BaseResponseCode.NOT_FOUND);
        }
        return target;
    }

    private static String reAuthCookieName(VerifyScene scene, VerifyChannel channel) {
        return scene.name() + "_" + channel.name() + "_REAUTH_KEY";
    }

    @Operation(summary = "重置密码")
    @PostMapping("/password/reset")
    @RateLimit(key = "account.default", permits = 5, window = 300)
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordChangeReq body) {
        Long uid = singleUseTokenService.consumeVerifyToken(body.getReAuthToken(), VerifyScene.RESET_PASSWORD.name());
        accountCoreService.changePwd(uid, body.getNewPwd(), body.getConfirmPwd(), body.getDeviceInfo());
        return ApiResponse.success();
    }

    @Operation(summary = "设置密码")
    @PostMapping("/password/set")
    @RateLimit(key = "account.default", permits = 5, window = 300)
    public ApiResponse<Void> setPassword(@Valid @RequestBody PasswordChangeReq body) {
        Long uid = singleUseTokenService.consumeVerifyToken(body.getReAuthToken(), VerifyScene.SET_PASSWORD.name());
        accountCoreService.setPassword(uid, body.getNewPwd(), body.getConfirmPwd(), body.getDeviceInfo());
        return ApiResponse.success();
    }

    @Operation(summary = "激活邮箱")
    @PostMapping("/email/activate")
    @RateLimit(key = "account.default", permits = 5, window = 300)
    public ApiResponse<EmailChangeVo> activateEmail(@Valid @RequestBody EmailReAuthReq body) {
        Long uid = singleUseTokenService.consumeVerifyToken(body.getReAuthToken(), VerifyScene.ACTIVATE.name());
        CodeResult result = accountCoreService.activateEmail(uid, body.getEmail(), body.getDeviceInfo());
        return ApiResponse.success(new EmailChangeVo(result.getKey()));
    }

    @Operation(summary = "修改邮箱 — 保存为新邮箱（未激活）并发送激活码")
    @PostMapping("/email/change")
    @RateLimit(key = "account.default", permits = 5, window = 300)
    public ApiResponse<EmailChangeVo> changeEmail(@Valid @RequestBody ChangeEmailReq body) {
        Long uid = singleUseTokenService.consumeVerifyToken(body.getReAuthToken(), VerifyScene.CHANGE_EMAIL.name());
        CodeResult result = accountCoreService.changeEmail(uid, body.getNewEmail(), body.getDeviceInfo());
        return ApiResponse.success(new EmailChangeVo(result.getKey()));
    }

    @Operation(summary = "验证新邮箱激活码，完成换邮箱")
    @PostMapping("/email/change/verify")
    @RateLimit(key = "account.default", permits = 5, window = 300)
    public ApiResponse<Void> verifyChangeEmail(@Valid @RequestBody EmailChangeVerifyReq body) {
        long uid = UserCtxHolder.getUserUid();
        accountCoreService.verifyChangeEmail(body.getVerifyKey(), body.getCode(), uid, body.getDeviceInfo());
        return ApiResponse.success();
    }

    @Operation(summary = "发起修改手机号 — 保存为新手机号（未激活）并发送激活码")
    @PostMapping("/phone/change")
    @RateLimit(key = "account.default", permits = 5, window = 300)
    public ApiResponse<PhoneChangeVo> changePhone(@Valid @RequestBody ChangePhoneReq body) {
        Long uid = singleUseTokenService.consumeVerifyToken(body.getReAuthToken(), VerifyScene.CHANGE_PHONE.name());
        CodeResult result = accountCoreService.changePhone(uid, body.getNewPhone(), body.getDeviceInfo());
        return ApiResponse.success(new PhoneChangeVo(result.getKey()));
    }

    @Operation(summary = "验证新手机号激活码，完成换手机号")
    @PostMapping("/phone/change/verify")
    @RateLimit(key = "account.default", permits = 5, window = 300)
    public ApiResponse<Void> verifyChangePhone(@Valid @RequestBody PhoneChangeVerifyReq body) {
        long uid = UserCtxHolder.getUserUid();
        accountCoreService.verifyChangePhone(body.getVerifyKey(), body.getCode(), uid, body.getDeviceInfo());
        return ApiResponse.success();
    }

    @Operation(summary = "激活手机号")
    @PostMapping("/phone/activate")
    @RateLimit(key = "account.default", permits = 5, window = 300)
    public ApiResponse<PhoneChangeVo> activatePhone(@Valid @RequestBody PhoneReAuthReq body) {
        Long uid = singleUseTokenService.consumeVerifyToken(body.getReAuthToken(), VerifyScene.ACTIVATE.name());
        CodeResult result = accountCoreService.activatePhone(uid, body.getPhone(), body.getDeviceInfo());
        return ApiResponse.success(new PhoneChangeVo(result.getKey()));
    }

    @Operation(summary = "解绑邮箱")
    @PostMapping("/email/unbind")
    @RateLimit(key = "account.default", permits = 5, window = 300)
    public ApiResponse<Void> unbindEmail(@Valid @RequestBody EmailReAuthReq body) {
        Long uid = singleUseTokenService.consumeVerifyToken(body.getReAuthToken(), VerifyScene.UNBIND.name());
        accountCoreService.unbindEmail(uid, body.getEmail(), body.getDeviceInfo());
        return ApiResponse.success();
    }

    @Operation(summary = "绑定头像资源（写入 avatar_resource_uuid；传空清除）")
    @PutMapping("/avatar")
    @RateLimit(key = "account.default", permits = 10, window = 300)
    public ApiResponse<Void> updateAvatar(@Valid @RequestBody UpdateAvatarReq body) {
        accountCoreService.updateAvatarResourceUuid(UserCtxHolder.getUserUid(), body.getResourceUuid());
        return ApiResponse.success();
    }
}
