package cn.nitrowater.account.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;

@Data
public class ReAuthReq {
    @NotNull
    private VerifyScene scene;
    /** Optional channel; defaults to SMS when a phone is bound, otherwise EMAIL. */
    private VerifyChannel channel;
}
