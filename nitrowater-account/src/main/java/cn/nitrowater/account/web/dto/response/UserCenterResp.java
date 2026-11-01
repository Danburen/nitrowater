package cn.nitrowater.account.web.dto.response;

import cn.nitrowater.core.api.resp.AccountResp;
import cn.nitrowater.core.domain.user.User;
import lombok.AllArgsConstructor;
import lombok.Data;

/** User-center snapshot: identity, profile and masked contact bindings. */
@Data
@AllArgsConstructor
public class UserCenterResp {

    private long uid;
    private String username;
    private String nickname;
    private String phoneMasked;
    private String emailMasked;
    private Boolean phoneVerified;
    private Boolean emailVerified;
    private boolean passwordSet;

    public static UserCenterResp from(User user, AccountResp account) {
        return new UserCenterResp(
                user.getUid(),
                user.getUsername(),
                user.getNickname(),
                account.getPhoneMasked(),
                account.getEmailMasked(),
                account.getPhoneVerified(),
                account.getEmailVerified(),
                user.getPasswordHash() != null && !user.getPasswordHash().isBlank());
    }
}
