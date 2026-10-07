package cn.nitrowater.account.web.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EmailChangeVo {
    private String verifyKey;
}
