package cn.nitrowater.account.web.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import cn.nitrowater.lib.common.TokenResult;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReAuthTokenVo {
    private String reAuthTokenValue;

    public static ReAuthTokenVo of(TokenResult tokenResult) {
        if (tokenResult == null) {
            return new ReAuthTokenVo();
        }
        if (tokenResult.value() == null) {
            return new ReAuthTokenVo();
        }
        return new ReAuthTokenVo(tokenResult.value());
    }
}
