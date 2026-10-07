package cn.nitrowater.core.api.req.user;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateUserInfoReq {
    @Size(max = 12)
    private String nickname;
}
