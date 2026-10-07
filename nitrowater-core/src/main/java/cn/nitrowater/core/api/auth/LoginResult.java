package cn.nitrowater.core.api.auth;

import cn.nitrowater.core.entity.user.User;

public final record LoginResult(User user, boolean isNewUser) {
}
