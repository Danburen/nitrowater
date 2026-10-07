package cn.nitrowater.core.lib.api.resp.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import cn.nitrowater.core.lib.api.resp.CloudResPresignedUrlResp;
import cn.nitrowater.core.lib.entity.user.AccountStatus;
import cn.nitrowater.core.lib.entity.user.User;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;

/**
 * DTO for {@link User}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoResponse implements Serializable {
    private String uid;
    private String username;
    private String nickname;
    private CloudResPresignedUrlResp avatar;
    private AccountStatus accountStatus;
    private Instant createdAt;
    private Boolean passwordHash;
    private Set<String> roles;
    private Set<String> permissions;
}