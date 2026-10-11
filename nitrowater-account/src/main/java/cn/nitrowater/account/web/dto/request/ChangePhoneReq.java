package cn.nitrowater.account.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import cn.nitrowater.core.api.req.auth.DeviceInfo;

@Data
public class ChangePhoneReq {
    @NotBlank
    private String reAuthToken;
    @NotBlank
    private String newPhone;
    @NotNull
    private DeviceInfo deviceInfo;
}
