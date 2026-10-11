package cn.nitrowater.account.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReAuthRequest {
    @NotBlank
    private String reAuthToken;
    @NotBlank
    private String value;
}
