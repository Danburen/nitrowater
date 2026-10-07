package cn.nitrowater.account.web.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReAuthInfoResp {
    private String maskedPhone;
}
