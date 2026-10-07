package cn.nitrowater.account.web.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import cn.nitrowater.core.api.auth.VerifyScene;

@Data
public class ReAuthReq {
    @NotNull
    private VerifyScene scene;
}
