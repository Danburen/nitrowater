package cn.nitrowater.core.lib.api.auth;

import cn.nitrowater.core.lib.entity.user.User;

public final record LoginResult(User user, boolean isNewUser) {
}
