package cn.nitrowater.core.lib.api.req.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import cn.nitrowater.core.lib.common.validation.StrongPassword;

@Data
public class ForgetPasswordDto {
    @NotBlank
    private String target;
    @NotBlank
    private String code;
    @NotBlank
    @StrongPassword
    private String newPwd;
    @NotBlank
    private String confirmPwd;
    private DeviceInfo deviceInfo;
}
