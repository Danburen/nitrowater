package cn.nitrowater.account.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import cn.nitrowater.core.api.req.auth.DeviceInfo;
import cn.nitrowater.lib.common.validation.StrongPassword;

@Data
public class PasswordChangeReq {
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
