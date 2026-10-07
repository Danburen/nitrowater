package cn.nitrowater.account.web.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import cn.nitrowater.core.lib.common.validation.StrongPassword;
import cn.nitrowater.core.lib.api.req.auth.DeviceInfo;

@Data
public class ForgotPasswordResetReq {
    @NotBlank
    private String reAuthToken;
    @NotBlank
    @StrongPassword
    private String newPwd;
    @NotBlank
    private String confirmPwd;
    @NotNull
    private DeviceInfo deviceInfo;
}
