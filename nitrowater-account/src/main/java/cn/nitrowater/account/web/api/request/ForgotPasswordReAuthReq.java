package cn.nitrowater.account.web.api.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordReAuthReq {
    @NotBlank
    private String identifier;
    @NotBlank
    private String captcha;
}
