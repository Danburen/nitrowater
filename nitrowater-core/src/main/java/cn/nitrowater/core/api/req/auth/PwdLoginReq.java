package cn.nitrowater.core.api.req.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import cn.nitrowater.core.common.validation.StrongPassword;

@Data
public class PwdLoginReq {
    @NotEmpty(message = "{validation.required}")
    private String identifier;
    @NotBlank(message = "{validation.required}")
    @Size(min = 8, max = 20, message = "{validation.size}")
    @StrongPassword
    private String password;
    @NotEmpty(message = "{validation.required}")
    private String captcha;
    @NotNull
    private DeviceInfo deviceInfo;
}

