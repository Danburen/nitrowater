package cn.nitrowater.account.web.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import cn.nitrowater.core.lib.api.auth.VerifyScene;

@Data
public class ReAuthVerifyReq {
    @NotNull
    private VerifyScene scene;

    @NotBlank
    private String code;
}
