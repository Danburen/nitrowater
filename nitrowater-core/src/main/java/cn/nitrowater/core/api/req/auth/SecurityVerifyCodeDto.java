package cn.nitrowater.core.api.req.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;

@Data
public class SecurityVerifyCodeDto {
    @NotNull
    private VerifyChannel channel;
    @NotBlank
    private String code;
    @NotNull
    private VerifyScene scene;
    private String deviceFp;
    private DeviceInfo deviceInfo;
}
