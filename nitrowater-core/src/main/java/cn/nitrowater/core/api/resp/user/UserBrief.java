package cn.nitrowater.core.api.resp.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import cn.nitrowater.core.api.resp.CloudResPresignedUrlResp;
import cn.nitrowater.core.entity.user.UserType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserBrief {
    private Long uid;

    private String displayName;
    private CloudResPresignedUrlResp avatar;
    private Short level;
    private UserType userType;
}
