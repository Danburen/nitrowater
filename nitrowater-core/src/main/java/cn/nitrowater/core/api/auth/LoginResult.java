package cn.nitrowater.core.api.auth;

import cn.nitrowater.core.domain.user.User;

public final record LoginResult(User user, boolean isNewUser) {
}
