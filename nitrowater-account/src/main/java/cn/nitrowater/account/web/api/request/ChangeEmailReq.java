package cn.nitrowater.account.web.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import cn.nitrowater.core.api.req.auth.DeviceInfo;

@Data
public class ChangeEmailReq {
    @NotBlank
    private String reAuthToken;
    @NotBlank
    private String newEmail;
    @NotNull
    private DeviceInfo deviceInfo;
}
