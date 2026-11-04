package cn.nitrowater.account.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import cn.nitrowater.core.api.auth.VerifyChannel;
import cn.nitrowater.core.api.auth.VerifyScene;

@Data
public class ReAuthVerifyReq {
    @NotNull
    private VerifyScene scene;

    /** Optional channel; must match the channel used to request the code. */
    private VerifyChannel channel;

    /**
     * Key returned by {@code POST /api/auth/account/re-auth}. Sent explicitly because the BFF drops
     * downstream {@code Set-Cookie}, so a cookie-carried key never reaches the browser.
     */
    @NotBlank
    private String verifyKey;

    @NotBlank
    private String code;
}
