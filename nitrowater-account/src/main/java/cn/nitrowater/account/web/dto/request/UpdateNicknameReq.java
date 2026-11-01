package cn.nitrowater.account.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Nickname update for the authenticated account. */
@Data
public class UpdateNicknameReq {

    @NotBlank
    @Size(max = 12)
    private String nickname;
}
