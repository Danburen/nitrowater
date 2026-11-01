package cn.nitrowater.account.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import cn.nitrowater.core.api.req.auth.DeviceInfo;

@Data
public class PhoneReAuthReq {
    @NotBlank
    private String reAuthToken;
    /** Optional for activation of an already-bound phone; required for initial binding. */
    private String phone;
    @NotNull
    private DeviceInfo deviceInfo;
}
